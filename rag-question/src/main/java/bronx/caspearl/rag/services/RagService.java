package bronx.caspearl.rag.services;

import bronx.caspearl.rag.dto.AskResponse;
import bronx.caspearl.rag.dto.LegalAnalysis;
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

    @Value("classpath:/prompts/query-rewrite.st")
    private Resource queryRewritePromptResource;

    @Value("classpath:/prompts/compare-concepts.st")
    private Resource compareConceptsPromptResource;

    @Value("classpath:/prompts/summarize-topic.st")
    private Resource summarizeTopicPromptResource;

    @Value("classpath:/prompts/article-lookup.st")
    private Resource articleLookupPromptResource;

    public RagService(
            ChatClient chatClient,
            @Qualifier("rewriteChatClient") ChatClient rewriteChatClient,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            JpaChatMemory jpaChatMemory) {
        this.chatClient = chatClient;
        this.rewriteChatClient = rewriteChatClient;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.jpaChatMemory = jpaChatMemory;
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

        // Detect if the question is a follow-up that depends on conversation history.
        // If so, skip the rewrite and pass the original question directly to the chatClient
        // which has MessageChatMemoryAdvisor and will inject the full history automatically.
        boolean isFollowUp = isFollowUpQuestion(question);
        String effectiveQuestion;
        if (isFollowUp) {
            log.info("Detected follow-up question, skipping rewrite: '{}'", question);
            effectiveQuestion = question;
        } else {
            effectiveQuestion = rewriteQuery(question, conversationId);
        }
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
                // On completion: persist full answer
                .doOnComplete(() -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    savedMessage.setAnswer(fullAnswer.toString());
                    savedMessage.setProcessingTimeMs(processingTime);
                    savedMessage.setStatus(ChatMessage.MessageStatus.COMPLETED);
                    savedMessage.setTokenCount((int) tokenCount.get());
                    messageRepository.save(savedMessage);
                    log.info("Stream completed in {}ms, {} tokens, question: {}",
                            processingTime, tokenCount.get(), question);
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
            String rewrittenQuestion = rewriteQuery(question);
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

            return AskResponse.builder()
                    .answer(answer)
                    .originalQuestion(question)
                    .rewrittenQuestion(rewrittenQuestion)
                    .sessionId(conversationId)
                    .sources(List.of("cambodia_labour_law.pdf"))
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

    // ==================== Compare / Summarize / Article (add streaming variants as needed) ====================

    @CircuitBreaker(name = "llmService", fallbackMethod = "compareFallback")
    public AskResponse compareConcepts(String concept1, String concept2) {
        log.info("Comparing: {} vs {}", concept1, concept2);
        String searchQuery = rewriteQuery(concept1 + " versus " + concept2 + " under Cambodian Labour Law");
        PromptTemplate compareTemplate = new PromptTemplate(compareConceptsPromptResource);
        String instructions = compareTemplate.render(Map.of("concept1", concept1, "concept2", concept2));
        String answer = chatClient.prompt()
                .user(searchQuery + "\n\n" + instructions)
                .call().content();
        return AskResponse.builder()
                .answer(answer)
                .originalQuestion(concept1 + " vs " + concept2)
                .rewrittenQuestion(searchQuery)
                .sources(List.of("cambodia_labour_law.pdf"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "summaryFallback")
    public AskResponse summarizeTopic(String topic) {
        log.info("Summarizing: {}", topic);
        String searchQuery = rewriteQuery(topic + " under Cambodian Labour Law");
        PromptTemplate summaryTemplate = new PromptTemplate(summarizeTopicPromptResource);
        String instructions = summaryTemplate.render(Map.of("topic", topic));
        String answer = chatClient.prompt()
                .user(searchQuery + "\n\n" + instructions)
                .call().content();
        return AskResponse.builder()
                .answer(answer)
                .originalQuestion("Summary: " + topic)
                .rewrittenQuestion(searchQuery)
                .sources(List.of("cambodia_labour_law.pdf"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "articleFallback")
    public AskResponse lookupArticle(String articleNumber) {
        log.info("Article lookup: {}", articleNumber);
        String searchQuery = "Article " + articleNumber + " of the Cambodia Labour Law full text and provisions";
        PromptTemplate articleTemplate = new PromptTemplate(articleLookupPromptResource);
        String instructions = articleTemplate.render(Map.of("articleNumber", articleNumber));
        String answer = chatClient.prompt()
                .user(searchQuery + "\n\n" + instructions)
                .call().content();
        return AskResponse.builder()
                .answer(answer)
                .originalQuestion("Article " + articleNumber)
                .rewrittenQuestion(searchQuery)
                .sources(List.of("cambodia_labour_law.pdf"))
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
     * Detects if the user's question is a follow-up that references previous conversation.
     * Follow-ups should NOT be rewritten because the rewriter strips context.
     * Instead, they are passed directly to the chatClient which has full chat memory.
     */
    private boolean isFollowUpQuestion(String question) {
        if (question == null) return false;
        String q = question.toLowerCase().trim();

        // Keywords that indicate the user is referencing previous conversation
        List<String> followUpIndicators = List.of(
                "summarize", "summary", "recap",
                "what you said", "what you told", "what did you say",
                "you just", "you mentioned", "you explained",
                "explain more", "tell me more", "elaborate",
                "can you repeat", "say that again",
                "the above", "previous answer", "your answer",
                "about that", "regarding that", "on that",
                "in short", "in brief", "briefly",
                "what about it", "why is that", "how so",
                "and what about", "what else"
        );

        for (String indicator : followUpIndicators) {
            if (q.contains(indicator)) {
                return true;
            }
        }

        // Very short questions with pronouns likely reference previous context
        // e.g., "What about it?", "Why?", "How?", "And overtime?"
        if (q.length() < 30 && (q.startsWith("why") || q.startsWith("how") || q.startsWith("and "))) {
            return true;
        }

        return false;
    }

    /**
     * Overload for callers without conversation context (e.g. compare, summarize endpoints).
     */
    private String rewriteQuery(String question) {
        return rewriteQuery(question, null);
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
    private AskResponse compareFallback(String c1, String c2, Throwable t) {
        return AskResponse.builder().answer("Unable to compare concepts right now.")
                .originalQuestion(c1 + " vs " + c2).timestamp(LocalDateTime.now()).build();
    }

    @SuppressWarnings("unused")
    private AskResponse summaryFallback(String topic, Throwable t) {
        return AskResponse.builder().answer("Unable to generate summary right now.")
                .originalQuestion("Summary: " + topic).timestamp(LocalDateTime.now()).build();
    }

    @SuppressWarnings("unused")
    private AskResponse articleFallback(String articleNumber, Throwable t) {
        return AskResponse.builder().answer("Unable to look up article right now.")
                .originalQuestion("Article " + articleNumber).timestamp(LocalDateTime.now()).build();
    }

    @SuppressWarnings("unused")
    private LegalAnalysis analysisFallback(String topic, Throwable t) {
        return LegalAnalysis.builder().summary("Analysis unavailable.").build();
    }
}