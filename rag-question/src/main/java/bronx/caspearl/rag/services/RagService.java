package bronx.caspearl.rag.services;

import bronx.caspearl.rag.advisor.SourceCitationAdvisor;
import bronx.caspearl.rag.dto.AskResponse;
import bronx.caspearl.rag.dto.LegalAnalysis;
import bronx.caspearl.rag.dto.UserIntent;
import bronx.caspearl.rag.entity.ChatConversation;
import bronx.caspearl.rag.entity.ChatMessage;
import bronx.caspearl.rag.repository.ChatConversationRepository;
import bronx.caspearl.rag.repository.ChatMessageRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class RagService {

    private final ChatClient chatClient;
    private final ChatClient rewriteChatClient;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final JpaChatMemory jpaChatMemory;
    private final SourceCitationAdvisor sourceCitationAdvisor;

    @Value("classpath:/prompts/query-rewrite.st")
    private Resource queryRewritePromptResource;

    @Value("classpath:/prompts/compare-concepts.st")
    private Resource compareConceptsPromptResource;

    @Value("classpath:/prompts/summarize-topic.st")
    private Resource summarizeTopicPromptResource;

    @Value("classpath:/prompts/article-lookup.st")
    private Resource articleLookupPromptResource;

    @Value("classpath:/prompts/intent-classify.st")
    private Resource intentClassifyPromptResource;

    /**
     * When true, uses the LLM to classify user intent instead of keyword matching.
     * Disable for lower latency at the cost of accuracy.
     */
    @Value("${rag.follow-up.use-llm-classification:true}")
    private boolean useLlmIntentClassification;

    public RagService(
            ChatClient chatClient,
            @Qualifier("rewriteChatClient") ChatClient rewriteChatClient,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            JpaChatMemory jpaChatMemory,
            SourceCitationAdvisor sourceCitationAdvisor) {
        this.chatClient = chatClient;
        this.rewriteChatClient = rewriteChatClient;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.jpaChatMemory = jpaChatMemory;
        this.sourceCitationAdvisor = sourceCitationAdvisor;
    }

    // ==================== Streaming RAG Q&A ====================

    /**
     * Returns a token-by-token Flux for SSE streaming to the frontend.
     * DB persistence happens on stream completion — non-blocking.
     */
    @CircuitBreaker(name = "llmService", fallbackMethod = "streamFallback")
    public Flux<String> askStream(String question, String sessionId) {
        log.info("Streaming question: {}", question);
        long startTime = System.currentTimeMillis();

        ChatConversation conversation = resolveConversation(sessionId);
        String conversationId = conversation.getSessionId();

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .originalQuestion(question)
                .status(ChatMessage.MessageStatus.PROCESSING)
                .build();
        ChatMessage savedMessage = messageRepository.save(message);

        // Intent classification: NEW_QUESTION, FOLLOW_UP, SUMMARIZE_CONVERSATION,
        // TRANSFORM_PREVIOUS_ANSWER, or CONVERSATIONAL
        UserIntent intent = classifyUserIntent(question, conversationId);
        log.info("Intent classified as {} for question: '{}'", intent, question);

        // CONVERSATIONAL messages bypass RAG entirely
        if (intent == UserIntent.CONVERSATIONAL) {
            savedMessage.setRewrittenQuestion("[CONVERSATIONAL]");
            messageRepository.save(savedMessage);
            return handleConversationalStream(question, conversationId, savedMessage, startTime)
                    .startWith("[SESSION:" + conversationId + "]");
        }

        // Whole-chat recap requests ("summarize all 10 questions", "what did we discuss so far")
        // use chat memory directly and skip vector search.
        if (intent == UserIntent.SUMMARIZE_CONVERSATION) {
            if (!hasConversationHistory(conversationId)) {
                savedMessage.setRewrittenQuestion("[SUMMARY_WITHOUT_HISTORY]");
                messageRepository.save(savedMessage);
                return handleUnresolvedConversationSummaryStream(question, conversationId, savedMessage, startTime)
                        .startWith("[SESSION:" + conversationId + "]");
            }
            savedMessage.setRewrittenQuestion("[SUMMARIZE_CONVERSATION]");
            messageRepository.save(savedMessage);
            return handleConversationSummaryStream(question, conversationId, savedMessage, startTime)
                    .startWith("[SESSION:" + conversationId + "]");
        }

        // Transform requests ("summary pls", "make it short", "translate to Khmer")
        // should operate on the last assistant answer directly, without vector search.
        if (intent == UserIntent.TRANSFORM_PREVIOUS_ANSWER) {
            if (!hasConversationHistory(conversationId)) {
                savedMessage.setRewrittenQuestion("[TRANSFORM_WITHOUT_HISTORY]");
                messageRepository.save(savedMessage);
                return handleUnresolvedFollowUpStream(question, conversationId, savedMessage, startTime)
                        .startWith("[SESSION:" + conversationId + "]");
            }
            savedMessage.setRewrittenQuestion("[TRANSFORM_PREVIOUS_ANSWER]");
            messageRepository.save(savedMessage);
            return handleTransformPreviousAnswerStream(question, conversationId, savedMessage, startTime)
                    .startWith("[SESSION:" + conversationId + "]");
        }

        // A dangling follow-up like "summary it" has nothing to resolve on a new/empty session.
        // Answer directly instead of sending a vague query into vector search.
        if (intent == UserIntent.FOLLOW_UP && !hasConversationHistory(conversationId)) {
            savedMessage.setRewrittenQuestion("[FOLLOW_UP_WITHOUT_HISTORY]");
            messageRepository.save(savedMessage);
            return handleUnresolvedFollowUpStream(question, conversationId, savedMessage, startTime)
                    .startWith("[SESSION:" + conversationId + "]");
        }

        // Both FOLLOW_UP and NEW_QUESTION go through query rewrite + RAG
        // (follow-ups NEED rewrite to resolve "it", "that", "summary pls" → standalone query)
        String effectiveQuestion = rewriteQuery(question, conversationId);
        savedMessage.setRewrittenQuestion(effectiveQuestion);
        messageRepository.save(savedMessage);

        // Accumulate full answer for persistence after stream ends
        StringBuilder fullAnswer = new StringBuilder();
        AtomicLong tokenCount = new AtomicLong(0);

        return chatClient.prompt()
                .user(effectiveQuestion)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                // Accumulate tokens and forward them
                .doOnNext(token -> {
                    fullAnswer.append(token);
                    tokenCount.incrementAndGet();
                })
                // On completion: persist full answer + capture sources
                .doOnComplete(() -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    savedMessage.setAnswer(fullAnswer.toString());
                    savedMessage.setProcessingTimeMs(processingTime);
                    savedMessage.setStatus(ChatMessage.MessageStatus.COMPLETED);
                    savedMessage.setTokenCount((int) tokenCount.get());
                    messageRepository.save(savedMessage);
                    log.info("Stream completed in {}ms, {} tokens, intent: {}, question: {}",
                            processingTime, tokenCount.get(), intent, question);
                })
                // On error: mark failed
                .doOnError(e -> {
                    savedMessage.setStatus(ChatMessage.MessageStatus.FAILED);
                    savedMessage.setErrorMessage(e.getMessage());
                    savedMessage.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    messageRepository.save(savedMessage);
                    log.error("Stream error for question '{}': {}", question, e.getMessage());
                })
                // Prepend sessionId as first SSE event so Vue knows which session to bind
                .startWith("[SESSION:" + conversationId + "]");
    }

    // ==================== Blocking ask (keep for internal/non-streaming use) ====================

    @Transactional
    @CircuitBreaker(name = "llmService", fallbackMethod = "askFallback")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public AskResponse ask(String question, String sessionId) {
        log.info("Blocking ask: {}", question);
        long startTime = System.currentTimeMillis();

        ChatConversation conversation = resolveConversation(sessionId);
        String conversationId = conversation.getSessionId();

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .originalQuestion(question)
                .status(ChatMessage.MessageStatus.PROCESSING)
                .build();
        message = messageRepository.save(message);

        try {
            UserIntent intent = classifyUserIntent(question, conversationId);
            log.info("Intent classified as {} for blocking question: '{}'", intent, question);

            if (intent == UserIntent.CONVERSATIONAL) {
                message.setRewrittenQuestion("[CONVERSATIONAL]");
                String answer = handleConversational(question, conversationId);
                completeMessage(message, answer, startTime, null);

                return AskResponse.builder()
                        .answer(answer)
                        .originalQuestion(question)
                        .rewrittenQuestion("[CONVERSATIONAL]")
                        .sessionId(conversationId)
                        .sources(List.of())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            if (intent == UserIntent.SUMMARIZE_CONVERSATION) {
                if (!hasConversationHistory(conversationId)) {
                    message.setRewrittenQuestion("[SUMMARY_WITHOUT_HISTORY]");
                    String answer = unresolvedConversationSummaryAnswer();
                    completeMessage(message, answer, startTime, null);

                    return AskResponse.builder()
                            .answer(answer)
                            .originalQuestion(question)
                            .rewrittenQuestion("[SUMMARY_WITHOUT_HISTORY]")
                            .sessionId(conversationId)
                            .sources(List.of())
                            .timestamp(LocalDateTime.now())
                            .build();
                }

                message.setRewrittenQuestion("[SUMMARIZE_CONVERSATION]");
                String answer = handleConversationSummary(question, conversationId);
                completeMessage(message, answer, startTime, null);

                return AskResponse.builder()
                        .answer(answer)
                        .originalQuestion(question)
                        .rewrittenQuestion("[SUMMARIZE_CONVERSATION]")
                        .sessionId(conversationId)
                        .sources(List.of())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            if (intent == UserIntent.TRANSFORM_PREVIOUS_ANSWER) {
                if (!hasConversationHistory(conversationId)) {
                    message.setRewrittenQuestion("[TRANSFORM_WITHOUT_HISTORY]");
                    String answer = unresolvedFollowUpAnswer();
                    completeMessage(message, answer, startTime, null);

                    return AskResponse.builder()
                            .answer(answer)
                            .originalQuestion(question)
                            .rewrittenQuestion("[TRANSFORM_WITHOUT_HISTORY]")
                            .sessionId(conversationId)
                            .sources(List.of())
                            .timestamp(LocalDateTime.now())
                            .build();
                }

                message.setRewrittenQuestion("[TRANSFORM_PREVIOUS_ANSWER]");
                String answer = handleTransformPreviousAnswer(question, conversationId);
                completeMessage(message, answer, startTime, null);

                return AskResponse.builder()
                        .answer(answer)
                        .originalQuestion(question)
                        .rewrittenQuestion("[TRANSFORM_PREVIOUS_ANSWER]")
                        .sessionId(conversationId)
                        .sources(List.of())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            if (intent == UserIntent.FOLLOW_UP && !hasConversationHistory(conversationId)) {
                message.setRewrittenQuestion("[FOLLOW_UP_WITHOUT_HISTORY]");
                String answer = unresolvedFollowUpAnswer();
                completeMessage(message, answer, startTime, null);

                return AskResponse.builder()
                        .answer(answer)
                        .originalQuestion(question)
                        .rewrittenQuestion("[FOLLOW_UP_WITHOUT_HISTORY]")
                        .sessionId(conversationId)
                        .sources(List.of())
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            String rewrittenQuestion = rewriteQuery(question, conversationId);
            message.setRewrittenQuestion(rewrittenQuestion);

            String answer = chatClient.prompt()
                    .user(rewrittenQuestion)
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            long processingTime = System.currentTimeMillis() - startTime;
            message.setAnswer(answer);
            message.setProcessingTimeMs(processingTime);
            message.setStatus(ChatMessage.MessageStatus.COMPLETED);
            messageRepository.save(message);

            // Get real source citations captured by SourceCitationAdvisor
            List<String> sources = sourceCitationAdvisor.getAndClearSources(conversationId);
            if (sources.isEmpty()) {
                sources = List.of("cambodia_labour_law.pdf");
            }

            return AskResponse.builder()
                    .answer(answer)
                    .originalQuestion(question)
                    .rewrittenQuestion(rewrittenQuestion)
                    .sessionId(conversationId)
                    .sources(sources)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            message.setStatus(ChatMessage.MessageStatus.FAILED);
            message.setErrorMessage(e.getMessage());
            message.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            messageRepository.save(message);
            throw e;
        }
    }

    public AskResponse ask(String question) {
        return ask(question, null);
    }

    // ==================== Compare / Summarize / Article — now with memory ====================

    @CircuitBreaker(name = "llmService", fallbackMethod = "compareFallback")
    public AskResponse compareConcepts(String concept1, String concept2, String sessionId) {
        log.info("Comparing: {} vs {}, session: {}", concept1, concept2, sessionId);

        ChatConversation conversation = resolveConversation(sessionId);
        String conversationId = conversation.getSessionId();

        String originalQuestion = concept1 + " vs " + concept2;
        String searchQuery = rewriteQuery(concept1 + " versus " + concept2 + " under Cambodian Labour Law", conversationId);
        PromptTemplate compareTemplate = new PromptTemplate(compareConceptsPromptResource);
        String instructions = compareTemplate.render(Map.of("concept1", concept1, "concept2", concept2));

        String answer = chatClient.prompt()
                .user(searchQuery + "\n\n" + instructions)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();

        // Persist to DB so this interaction is visible in chat history
        persistMessage(conversation, originalQuestion, searchQuery, answer);

        List<String> sources = sourceCitationAdvisor.getAndClearSources(conversationId);
        if (sources.isEmpty()) sources = List.of("cambodia_labour_law.pdf");

        return AskResponse.builder()
                .answer(answer)
                .originalQuestion(originalQuestion)
                .rewrittenQuestion(searchQuery)
                .sessionId(conversationId)
                .sources(sources)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "summaryFallback")
    public AskResponse summarizeTopic(String topic, String sessionId) {
        log.info("Summarizing: {}, session: {}", topic, sessionId);

        ChatConversation conversation = resolveConversation(sessionId);
        String conversationId = conversation.getSessionId();

        String originalQuestion = "Summary: " + topic;
        String searchQuery = rewriteQuery(topic + " under Cambodian Labour Law", conversationId);
        PromptTemplate summaryTemplate = new PromptTemplate(summarizeTopicPromptResource);
        String instructions = summaryTemplate.render(Map.of("topic", topic));

        String answer = chatClient.prompt()
                .user(searchQuery + "\n\n" + instructions)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();

        persistMessage(conversation, originalQuestion, searchQuery, answer);

        List<String> sources = sourceCitationAdvisor.getAndClearSources(conversationId);
        if (sources.isEmpty()) sources = List.of("cambodia_labour_law.pdf");

        return AskResponse.builder()
                .answer(answer)
                .originalQuestion(originalQuestion)
                .rewrittenQuestion(searchQuery)
                .sessionId(conversationId)
                .sources(sources)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "articleFallback")
    public AskResponse lookupArticle(String articleNumber, String sessionId) {
        log.info("Article lookup: {}, session: {}", articleNumber, sessionId);

        ChatConversation conversation = resolveConversation(sessionId);
        String conversationId = conversation.getSessionId();

        String originalQuestion = "Article " + articleNumber;
        String searchQuery = "Article " + articleNumber + " of the Cambodia Labour Law full text and provisions";
        PromptTemplate articleTemplate = new PromptTemplate(articleLookupPromptResource);
        String instructions = articleTemplate.render(Map.of("articleNumber", articleNumber));

        String answer = chatClient.prompt()
                .user(searchQuery + "\n\n" + instructions)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();

        persistMessage(conversation, originalQuestion, searchQuery, answer);

        List<String> sources = sourceCitationAdvisor.getAndClearSources(conversationId);
        if (sources.isEmpty()) sources = List.of("cambodia_labour_law.pdf");

        return AskResponse.builder()
                .answer(answer)
                .originalQuestion(originalQuestion)
                .rewrittenQuestion(searchQuery)
                .sessionId(conversationId)
                .sources(sources)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "analysisFallback")
    public LegalAnalysis analyzeTopic(String topic) {
        log.info("Structured analysis: {}", topic);
        BeanOutputConverter<LegalAnalysis> outputConverter = new BeanOutputConverter<>(LegalAnalysis.class);
        String prompt = """
                Analyze the following topic under Cambodian Labour Law and provide a structured analysis.
                Topic: %s
                %s
                """.formatted(topic, outputConverter.getFormat());
        String response = chatClient.prompt().user(prompt).call().content();
        return outputConverter.convert(response);
    }

    // ==================== Private Helpers ====================

    /**
     * Intent classifier: determines whether a user message is a new question,
     * a retrieval follow-up, a whole-conversation summary request,
     * a transform of the previous answer, or casual conversation/greeting.
     *
     * Uses LLM-based classification (accurate, ~500ms) or keyword heuristic (fast, less accurate)
     * based on the rag.follow-up.use-llm-classification config property.
     */
    private UserIntent classifyUserIntent(String question, String conversationId) {
        if (question == null || question.isBlank()) return UserIntent.NEW_QUESTION;

        // Broad conversation summaries are cheap and high-confidence to detect.
        // Force this route so "summarize all 10 questions" is not treated as "summarize it".
        if (isConversationSummaryByKeywords(question)) {
            return UserIntent.SUMMARIZE_CONVERSATION;
        }

        // Fast path: no conversation history means follow-ups cannot be resolved,
        // but still classify them so callers can avoid a useless vector search.
        if (conversationId == null) {
            return classifyIntentWithKeywords(question, null);
        }
        List<Message> history = jpaChatMemory.get(conversationId);
        if (history == null || history.isEmpty()) {
            return classifyIntentWithKeywords(question, null);
        }

        if (useLlmIntentClassification) {
            return classifyIntentWithLlm(question, conversationId);
        } else {
            return classifyIntentWithKeywords(question, conversationId);
        }
    }

    /**
     * LLM-based intent classification using a dedicated prompt template.
     * Works for any language (including Khmer), handles complex references,
     * and requires no keyword maintenance.
     */
    private UserIntent classifyIntentWithLlm(String question, String conversationId) {
        try {
            String historyText = buildHistoryText(conversationId);
            PromptTemplate classifyTemplate = new PromptTemplate(intentClassifyPromptResource);
            String prompt = classifyTemplate.render(Map.of(
                    "question", question,
                    "history", historyText
            ));

            String classification = rewriteChatClient.prompt()
                    .user(prompt)
                    .call().content();

            UserIntent intent = UserIntent.fromLlmResponse(classification);
            log.debug("LLM intent classification for '{}': {} → {}", question, classification, intent);
            return intent;
        } catch (Exception e) {
            log.warn("LLM intent classification failed, falling back to keywords: {}", e.getMessage());
            return classifyIntentWithKeywords(question, conversationId);
        }
    }

    /**
     * Keyword-based intent classification (fast fallback).
     * Checks whole-chat summary, transform, and follow-up indicators before conversational
     * patterns so inputs like "summary pls" are not swallowed by the generic short-message rule.
     */
    private UserIntent classifyIntentWithKeywords(String question, String conversationId) {
        if (isConversationSummaryByKeywords(question)) {
            return UserIntent.SUMMARIZE_CONVERSATION;
        }
        if (isTransformPreviousAnswerByKeywords(question, conversationId)) {
            return UserIntent.TRANSFORM_PREVIOUS_ANSWER;
        }
        if (isFollowUpByKeywords(question)) {
            return UserIntent.FOLLOW_UP;
        }
        if (isConversationalByKeywords(question)) {
            return UserIntent.CONVERSATIONAL;
        }
        return UserIntent.NEW_QUESTION;
    }

    /**
     * Detects requests to recap the whole chat, all user questions, or topics so far.
     */
    private boolean isConversationSummaryByKeywords(String question) {
        String q = question.toLowerCase().trim();

        boolean asksForSummary = q.contains("summarize")
                || q.contains("summarise")
                || q.contains("summary")
                || q.contains("recap")
                || q.contains("review")
                || q.contains("what did we discuss")
                || q.contains("what have we discussed")
                || q.contains("what we discussed")
                || q.contains("topics we discussed")
                || q.contains("topics we covered");

        boolean broadConversationScope = q.contains("conversation")
                || q.contains("chat")
                || q.contains("all question")
                || q.contains("all previous question")
                || q.contains("previous question")
                || q.contains("questions i asked")
                || q.contains("question i asked")
                || q.contains("questions so far")
                || q.contains("everything so far")
                || q.contains("so far")
                || q.contains("what did we discuss")
                || q.contains("what have we discussed")
                || q.contains("what we discussed")
                || q.contains("topics we discussed")
                || q.contains("topics we covered")
                || q.contains("whole")
                || q.contains("entire")
                || q.matches(".*\\b\\d+\\s+questions?\\b.*")
                || q.matches(".*\\bten\\s+questions?\\b.*");

        return asksForSummary && broadConversationScope;
    }

    /**
     * Detects greetings, thanks, acknowledgments, and casual chitchat.
     */
    private boolean isConversationalByKeywords(String question) {
        String q = question.toLowerCase().trim();
        // Strip punctuation for matching ("hello!" → "hello")
        String stripped = q.replaceAll("[^a-zA-Z\\s]", "").trim();
        List<String> conversationalPatterns = List.of(
                "hello", "hi", "hey", "yo", "sup",
                "good morning", "good afternoon", "good evening", "good day",
                "hi there", "hello there", "hey there",
                "hello guy", "hello guys", "hi guy", "hi guys",
                "thanks", "thank you", "thx", "ty", "thank",
                "ok", "okay", "ok got it", "got it", "understood", "i see",
                "bye", "goodbye", "see you", "see ya", "later",
                "nice", "great", "cool", "awesome", "perfect", "wonderful",
                "no problem", "no worries", "alright",
                "how are you", "whats up", "hows it going"
        );
        for (String pattern : conversationalPatterns) {
            if (stripped.equals(pattern) || stripped.startsWith(pattern + " ")) return true;
        }
        // Very short messages (1-2 words) with no legal terms are likely conversational
        String[] words = stripped.split("\\s+");
        if (words.length <= 2 && !containsLegalTerms(stripped)) {
            return true;
        }
        return false;
    }

    /**
     * Detects requests that should transform the previous assistant answer directly
     * instead of performing another vector search.
     *
     * EXCLUDES: "summarize/summary/recap" without clear "answer" context — those belong to
     * SUMMARIZE_CONVERSATION if they include broad scope keywords.
     * INCLUDES: Format/style/language transforms, explicit answer references.
     */
    private boolean isTransformPreviousAnswerByKeywords(String question, String conversationId) {
        String q = question.toLowerCase().trim();

        // Only match transform if there's a previous message to transform
        if (conversationId != null) {
            List<Message> history = jpaChatMemory.get(conversationId);
            if (history == null || history.isEmpty()) {
                return false;
            }
        }

        // Format/style transforms (these clearly indicate "transform THIS answer")
        List<String> formatTransforms = List.of(
                "make it short", "make it shorter", "make it brief", "short answer",
                "shorten it", "shorten that", "condense", "in short", "in brief", "briefly",
                "simplify", "simple words", "explain simply", "break it down",
                "rephrase", "rewrite", "say that differently",
                "bullet point", "bullet points", "list key", "key points", "table",
                "translate", "in khmer", "to khmer", "in english", "to english",
                "make it clearer", "clarify that"
        );

        // Transforms with explicit answer reference (safe to match alone)
        List<String> answerRefTransforms = List.of(
                "sum it", "tl;dr", "tldr",
                "tell me again", "repeat that", "can you repeat", "say that again",
                "what do you mean", "explain more", "explain that", "elaborate"
        );

        for (String indicator : formatTransforms) {
            if (q.contains(indicator)) return true;
        }
        for (String indicator : answerRefTransforms) {
            if (q.contains(indicator)) return true;
        }

        // "summarize/recap" the ANSWER (not the conversation) — requires "answer" or "it" context
        if ((q.contains("summarize") || q.contains("summary") || q.contains("recap"))
                && (q.contains("answer") || q.contains("it") || q.contains("that"))) {
            // But NOT if it also has broad conversation scope (that's SUMMARIZE_CONVERSATION instead)
            if (!isConversationSummaryByKeywords(question)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detects follow-up references to previous conversation.
     */
    private boolean isFollowUpByKeywords(String question) {
        String q = question.toLowerCase().trim();
        List<String> followUpIndicators = List.of(
                "what you said", "what you told", "what did you say",
                "you just", "you mentioned", "you explained",
                "explain more", "tell me more", "elaborate", "go deeper",
                "the above", "previous answer", "your answer", "last answer",
                "about that", "regarding that", "on that", "about it",
                "what about it", "why is that", "how so",
                "and what about", "what else", "anything else",
                "more detail", "more details", "give me more",
                "can you clarify"
        );
        for (String indicator : followUpIndicators) {
            if (q.contains(indicator)) return true;
        }
        return false;
    }

    /**
     * Checks if text contains legal terminology — used to distinguish
     * between conversational messages and very short legal questions.
     */
    private boolean containsLegalTerms(String text) {
        List<String> legalTerms = List.of(
                "article", "law", "labour", "labor", "contract", "employee", "employer",
                "termination", "dismissal", "severance", "overtime", "salary", "wage",
                "probation", "notice", "leave", "maternity", "paternity", "holiday",
                "fdc", "udc", "nssf", "molvt", "indemnity", "compensation",
                "working hours", "minimum wage", "strike", "union", "pension"
        );
        for (String term : legalTerms) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    /**
     * Handles conversational messages (greetings, thanks, chitchat) by responding
     * with the LLM directly — NO vector search, NO RAG retrieval.
     * Uses conversation history so the bot can reference prior discussion naturally.
     */
    private Flux<String> handleConversationalStream(String question, String conversationId,
                                                     ChatMessage savedMessage, long startTime) {
        String historyText = buildHistoryText(conversationId);

        StringBuilder fullAnswer = new StringBuilder();
        AtomicLong tokenCount = new AtomicLong(0);

        return rewriteChatClient.prompt()
                .system("""
                    You are a friendly, professional Cambodian Labour Law assistant.
                    Respond naturally and warmly to the user's message.
                    If the user greets you, greet them back warmly.
                    If the user thanks you, acknowledge it graciously.
                    If the user seems to want help, gently offer to answer questions
                    about Cambodian Labour Law.
                    Keep your response brief (1-3 sentences).
                    If there is prior conversation context, you may reference it naturally.
                    """)
                .user(question + (historyText.contains("(No previous conversation)")
                        ? "" : "\n\nPrior conversation context:\n" + historyText))
                .stream()
                .content()
                .doOnNext(token -> {
                    fullAnswer.append(token);
                    tokenCount.incrementAndGet();
                })
                .doOnComplete(() -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    savedMessage.setAnswer(fullAnswer.toString());
                    savedMessage.setProcessingTimeMs(processingTime);
                    savedMessage.setStatus(ChatMessage.MessageStatus.COMPLETED);
                    savedMessage.setTokenCount((int) tokenCount.get());
                    messageRepository.save(savedMessage);
                    log.info("Conversational stream completed in {}ms for: '{}'", processingTime, question);
                })
                .doOnError(e -> {
                    savedMessage.setStatus(ChatMessage.MessageStatus.FAILED);
                    savedMessage.setErrorMessage(e.getMessage());
                    savedMessage.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    messageRepository.save(savedMessage);
                    log.error("Conversational stream error for '{}': {}", question, e.getMessage());
                });
    }

    private String handleConversational(String question, String conversationId) {
        String historyText = buildHistoryText(conversationId);
        return rewriteChatClient.prompt()
                .system("""
                    You are a friendly, professional Cambodian Labour Law assistant.
                    Respond naturally and warmly to the user's message.
                    If the user greets you, greet them back warmly.
                    If the user thanks you, acknowledge it graciously.
                    If the user seems to want help, gently offer to answer questions
                    about Cambodian Labour Law.
                    Keep your response brief (1-3 sentences).
                    If there is prior conversation context, you may reference it naturally.
                    """)
                .user(question + (historyText.contains("(No previous conversation)")
                        ? "" : "\n\nPrior conversation context:\n" + historyText))
                .call()
                .content();
    }

    /**
     * Summarizes the whole conversation/history directly. This is for requests like
     * "summarize all 10 questions", "recap our chat", or "what did we discuss so far".
     */
    private Flux<String> handleConversationSummaryStream(String question, String conversationId,
                                                         ChatMessage savedMessage, long startTime) {
        String conversationContext = buildConversationSummaryContext(conversationId);
        if (conversationContext.contains("(No previous conversation)")) {
            String answer = unresolvedConversationSummaryAnswer();
            return Flux.just(answer)
                    .doOnComplete(() -> completeMessage(savedMessage, answer, startTime, 1));
        }

        StringBuilder fullAnswer = new StringBuilder();
        AtomicLong tokenCount = new AtomicLong(0);

        return rewriteChatClient.prompt()
                .system(conversationSummarySystemPrompt())
                .user(buildConversationSummaryPrompt(question, conversationContext))
                .stream()
                .content()
                .doOnNext(token -> {
                    fullAnswer.append(token);
                    tokenCount.incrementAndGet();
                })
                .doOnComplete(() -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    savedMessage.setAnswer(fullAnswer.toString());
                    savedMessage.setProcessingTimeMs(processingTime);
                    savedMessage.setStatus(ChatMessage.MessageStatus.COMPLETED);
                    savedMessage.setTokenCount((int) tokenCount.get());
                    messageRepository.save(savedMessage);
                    log.info("Conversation summary completed in {}ms for: '{}'", processingTime, question);
                })
                .doOnError(e -> {
                    savedMessage.setStatus(ChatMessage.MessageStatus.FAILED);
                    savedMessage.setErrorMessage(e.getMessage());
                    savedMessage.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    messageRepository.save(savedMessage);
                    log.error("Conversation summary stream error for '{}': {}", question, e.getMessage());
                });
    }

    private String handleConversationSummary(String question, String conversationId) {
        String conversationContext = buildConversationSummaryContext(conversationId);
        if (conversationContext.contains("(No previous conversation)")) {
            return unresolvedConversationSummaryAnswer();
        }
        return rewriteChatClient.prompt()
                .system(conversationSummarySystemPrompt())
                .user(buildConversationSummaryPrompt(question, conversationContext))
                .call()
                .content();
    }

    private String conversationSummarySystemPrompt() {
        return """
                You are a Cambodian Labour Law assistant.
                Summarize only the provided conversation transcript. Do not perform new legal retrieval.
                Do not add new legal facts, article numbers, or conclusions that were not already discussed.
                If the user asks to summarize questions, focus on the user's question topics and the key answer takeaways.
                If the transcript has fewer questions than the user mentions, say what is available without guessing.
                Keep the answer practical, clear, and concise unless the user asks for more detail.
                """;
    }

    private String buildConversationSummaryPrompt(String question, String conversationContext) {
        return """
                User request:
                %s

                Conversation transcript to summarize:
                %s

                Return a useful recap of the conversation. Group related questions where helpful.
                """.formatted(question, conversationContext);
    }

    private String buildConversationSummaryContext(String conversationId) {
        try {
            ChatConversation conversation = conversationRepository.findBySessionId(conversationId).orElse(null);
            if (conversation == null) {
                return "(No previous conversation)";
            }

            List<ChatMessage> messages = messageRepository.findByConversationIdAndStatusOrderByCreatedAtAsc(
                    conversation.getId(), ChatMessage.MessageStatus.COMPLETED);
            if (messages == null || messages.isEmpty()) {
                return "(No previous conversation)";
            }

            StringBuilder sb = new StringBuilder();
            int maxPromptChars = 20000;
            int exchangeNumber = 1;
            for (ChatMessage msg : messages) {
                String question = msg.getOriginalQuestion();
                String answer = msg.getAnswer();
                if ((question == null || question.isBlank()) && (answer == null || answer.isBlank())) {
                    continue;
                }

                String compactQuestion = compactForSummary(question, 800);
                String compactAnswer = compactForSummary(answer, 1500);
                String entry = "Exchange " + exchangeNumber + "\n"
                        + "USER: " + compactQuestion + "\n"
                        + "ASSISTANT: " + compactAnswer + "\n\n";

                if (sb.length() + entry.length() > maxPromptChars) {
                    sb.append("...(conversation truncated to fit summary prompt)\n");
                    break;
                }
                sb.append(entry);
                exchangeNumber++;
            }

            return sb.isEmpty() ? "(No previous conversation)" : sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build conversation summary context: {}", e.getMessage());
            return "(No previous conversation)";
        }
    }

    private String compactForSummary(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        return text.length() > maxChars ? text.substring(0, maxChars) + "..." : text;
    }

    /**
     * Transforms the last assistant answer directly. This is for user requests like
     * "summary pls", "make it short", "rephrase", or "translate to Khmer".
     */
    private Flux<String> handleTransformPreviousAnswerStream(String question, String conversationId,
                                                             ChatMessage savedMessage, long startTime) {
        String previousAnswer = getLastAssistantAnswer(conversationId);
        if (previousAnswer == null || previousAnswer.isBlank()) {
            String answer = unresolvedFollowUpAnswer();
            return Flux.just(answer)
                    .doOnComplete(() -> completeMessage(savedMessage, answer, startTime, 1));
        }

        StringBuilder fullAnswer = new StringBuilder();
        AtomicLong tokenCount = new AtomicLong(0);

        return rewriteChatClient.prompt()
                .system(previousAnswerTransformSystemPrompt())
                .user(buildPreviousAnswerTransformPrompt(question, conversationId, previousAnswer))
                .stream()
                .content()
                .doOnNext(token -> {
                    fullAnswer.append(token);
                    tokenCount.incrementAndGet();
                })
                .doOnComplete(() -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    savedMessage.setAnswer(fullAnswer.toString());
                    savedMessage.setProcessingTimeMs(processingTime);
                    savedMessage.setStatus(ChatMessage.MessageStatus.COMPLETED);
                    savedMessage.setTokenCount((int) tokenCount.get());
                    messageRepository.save(savedMessage);
                    log.info("Previous-answer transform completed in {}ms for: '{}'", processingTime, question);
                })
                .doOnError(e -> {
                    savedMessage.setStatus(ChatMessage.MessageStatus.FAILED);
                    savedMessage.setErrorMessage(e.getMessage());
                    savedMessage.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    messageRepository.save(savedMessage);
                    log.error("Previous-answer transform stream error for '{}': {}", question, e.getMessage());
                });
    }

    private String handleTransformPreviousAnswer(String question, String conversationId) {
        String previousAnswer = getLastAssistantAnswer(conversationId);
        if (previousAnswer == null || previousAnswer.isBlank()) {
            return unresolvedFollowUpAnswer();
        }
        return rewriteChatClient.prompt()
                .system(previousAnswerTransformSystemPrompt())
                .user(buildPreviousAnswerTransformPrompt(question, conversationId, previousAnswer))
                .call()
                .content();
    }

    private String previousAnswerTransformSystemPrompt() {
        return """
                You are a Cambodian Labour Law assistant.
                Transform the previous assistant answer according to the user's latest request.
                Do not perform new legal research, do not add new facts, and do not invent missing article numbers.
                Preserve the legal meaning, uncertainty, caveats, and any "not enough information" limits from the previous answer.
                If the user asks for a short answer or summary, keep it concise and practical.
                If the user asks for a translation, translate faithfully and keep legal terms clear.
                Return only the transformed answer.
                """;
    }

    private String buildPreviousAnswerTransformPrompt(String question, String conversationId, String previousAnswer) {
        String compactPreviousAnswer = previousAnswer;
        if (compactPreviousAnswer.length() > 5000) {
            compactPreviousAnswer = compactPreviousAnswer.substring(0, 5000) + "...";
        }

        return """
                User request:
                %s

                Previous assistant answer to transform:
                %s

                Recent conversation context:
                %s
                """.formatted(question, compactPreviousAnswer, buildHistoryText(conversationId));
    }

    private Flux<String> handleUnresolvedConversationSummaryStream(String question, String conversationId,
                                                                   ChatMessage savedMessage, long startTime) {
        String answer = unresolvedConversationSummaryAnswer();
        return Flux.just(answer)
                .doOnComplete(() -> completeMessage(savedMessage, answer, startTime, 1));
    }

    private String unresolvedConversationSummaryAnswer() {
        return "I can summarize the conversation, but there is no previous completed exchange in this chat yet. Ask a few questions first, then I can summarize them.";
    }

    private Flux<String> handleUnresolvedFollowUpStream(String question, String conversationId,
                                                        ChatMessage savedMessage, long startTime) {
        String answer = unresolvedFollowUpAnswer();
        return Flux.just(answer)
                .doOnComplete(() -> completeMessage(savedMessage, answer, startTime, 1));
    }

    private String unresolvedFollowUpAnswer() {
        return "I can help with that, but I do not have a previous answer in this chat to refer to yet. Please send the topic or question you want me to summarize, shorten, or explain.";
    }

    private boolean hasConversationHistory(String conversationId) {
        List<Message> history = jpaChatMemory.get(conversationId);
        return history != null && !history.isEmpty();
    }

    private String getLastAssistantAnswer(String conversationId) {
        try {
            List<Message> messages = jpaChatMemory.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return null;
            }
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if (message.getMessageType() != null
                        && "ASSISTANT".equals(message.getMessageType().name())
                        && message.getText() != null
                        && !message.getText().isBlank()) {
                    return message.getText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve last assistant answer for transform: {}", e.getMessage());
        }
        return null;
    }

    private void completeMessage(ChatMessage message, String answer, long startTime, Integer tokenCount) {
        long processingTime = System.currentTimeMillis() - startTime;
        message.setAnswer(answer);
        message.setProcessingTimeMs(processingTime);
        message.setStatus(ChatMessage.MessageStatus.COMPLETED);
        if (tokenCount != null) {
            message.setTokenCount(tokenCount);
        }
        messageRepository.save(message);
    }

    private String rewriteQuery(String question, String conversationId) {
        try {
            // Build a compact history string from recent exchanges
            String historyText = buildHistoryText(conversationId);

            PromptTemplate rewriteTemplate = new PromptTemplate(queryRewritePromptResource);
            String prompt = rewriteTemplate.render(Map.of(
                    "question", question,
                    "history", historyText
            ));
            String rewritten = rewriteChatClient.prompt().user(prompt).call().content();
            if (rewritten != null && !rewritten.isBlank()) {
                log.info("Query rewritten: '{}' → '{}'", question, rewritten.trim());
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original: {}", e.getMessage());
        }
        return question;
    }

    /**
     * Builds a compact text summary of recent conversation history
     * so the query rewriter can resolve follow-up references.
     */
    private String buildHistoryText(String conversationId) {
        try {
            List<Message> messages = jpaChatMemory.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return "(No previous conversation)";
            }
            StringBuilder sb = new StringBuilder();
            for (Message msg : messages) {
                String role = msg.getMessageType().name();
                // Truncate long answers to keep the rewrite prompt small
                String text = msg.getText();
                if (text != null && text.length() > 300) {
                    text = text.substring(0, 300) + "...";
                }
                sb.append(role).append(": ").append(text).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build history for rewrite: {}", e.getMessage());
            return "(No previous conversation)";
        }
    }

    /**
     * Overload for callers without conversation context (e.g. compare, summarize endpoints).
     */
    private String rewriteQuery(String question) {
        return rewriteQuery(question, null);
    }

    /**
     * Persists a Q&A exchange to the database so it appears in conversation history.
     * Used by compare/summarize/article endpoints that previously didn't save.
     */
    private void persistMessage(ChatConversation conversation, String originalQuestion,
                                String rewrittenQuestion, String answer) {
        try {
            ChatMessage message = ChatMessage.builder()
                    .conversation(conversation)
                    .originalQuestion(originalQuestion)
                    .rewrittenQuestion(rewrittenQuestion)
                    .answer(answer)
                    .status(ChatMessage.MessageStatus.COMPLETED)
                    .build();
            messageRepository.save(message);
        } catch (Exception e) {
            log.warn("Failed to persist message for compare/summarize/article: {}", e.getMessage());
        }
    }

    private ChatConversation resolveConversation(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return conversationRepository.findBySessionId(sessionId)
                    .orElseGet(() -> conversationRepository.save(
                            ChatConversation.builder()
                                    .sessionId(sessionId)
                                    .title("Cambodia Labour Law Q&A")
                                    .build()));
        }
        return conversationRepository.save(
                ChatConversation.builder()
                        .sessionId(UUID.randomUUID().toString())
                        .title("Cambodia Labour Law Q&A")
                        .build());
    }

    // ==================== Fallbacks ====================

    @SuppressWarnings("unused")
    private Flux<String> streamFallback(String question, String sessionId, Throwable t) {
        log.error("Stream circuit breaker: {}", t.getMessage());
        return Flux.just("The AI service is temporarily unavailable. Please try again shortly.");
    }

    @SuppressWarnings("unused")
    private AskResponse askFallback(String question, String sessionId, Throwable t) {
        log.error("Ask circuit breaker: {}", t.getMessage());
        return AskResponse.builder()
                .answer("The AI service is temporarily unavailable.")
                .originalQuestion(question)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("unused")
    private AskResponse compareFallback(String c1, String c2, String sessionId, Throwable t) {
        return AskResponse.builder().answer("Unable to compare concepts right now.")
                .originalQuestion(c1 + " vs " + c2).timestamp(LocalDateTime.now()).build();
    }

    @SuppressWarnings("unused")
    private AskResponse summaryFallback(String topic, String sessionId, Throwable t) {
        return AskResponse.builder().answer("Unable to generate summary right now.")
                .originalQuestion("Summary: " + topic).timestamp(LocalDateTime.now()).build();
    }

    @SuppressWarnings("unused")
    private AskResponse articleFallback(String articleNumber, String sessionId, Throwable t) {
        return AskResponse.builder().answer("Unable to look up article right now.")
                .originalQuestion("Article " + articleNumber).timestamp(LocalDateTime.now()).build();
    }

    @SuppressWarnings("unused")
    private LegalAnalysis analysisFallback(String topic, Throwable t) {
        return LegalAnalysis.builder().summary("Analysis unavailable.").build();
    }
}
