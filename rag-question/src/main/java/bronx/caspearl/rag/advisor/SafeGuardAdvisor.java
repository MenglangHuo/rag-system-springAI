package bronx.caspearl.rag.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safety advisor that protects the RAG system from:
 * 1. Prompt injection attacks (pre-call validation)
 * 2. Hallucinated article numbers (post-call validation)
 *
 * Runs FIRST in the advisor chain (highest precedence).
 * Cambodia Labour Law has Articles 1–396, so any reference
 * outside this range is flagged as a hallucination.
 */
@Slf4j
@Component("customSafeGuardAdvisor")
public class SafeGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int MAX_VALID_ARTICLE = 396;

    private static final String BLOCKED_RESPONSE =
            "⚠️ Your message was blocked by our safety filter. " +
            "Please rephrase your question about Cambodian Labour Law.";

    /**
     * Patterns that indicate prompt injection attempts.
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+a", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(all\\s+)?(your|previous)\\s+(instructions|rules)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override\\s+(system|your)\\s+(prompt|instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+you\\s+are", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+restrictions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDAN\\b.*\\bdo\\s+anything\\s+now\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s*:\\s*you\\s+are", Pattern.CASE_INSENSITIVE)
    );

    /** Regex to find "Article NNN" references in LLM responses */
    private static final Pattern ARTICLE_REF_PATTERN =
            Pattern.compile("Article\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "CustomSafeGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // ==================== Blocking Path ====================

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // --- PRE-CALL: Check for prompt injection ---
        String userText = request.prompt().getContents();
        if (containsInjection(userText)) {
            log.warn("BLOCKED prompt injection attempt: '{}'",
                    userText.substring(0, Math.min(100, userText.length())));
            return buildBlockedResponse(request);
        }

        // --- Proceed with the chain ---
        ChatClientResponse response = chain.nextCall(request);

        // --- POST-CALL: Validate article references ---
        String answer = extractContent(response);
        if (answer != null) {
            List<Integer> invalidArticles = findInvalidArticles(answer);
            if (!invalidArticles.isEmpty()) {
                log.warn("Hallucination detected — invalid article references: {}", invalidArticles);
                String disclaimer = "\n\n⚠️ **Note:** Some article numbers referenced above " +
                        "(e.g., " + invalidArticles + ") may not exist in the Cambodia Labour Law. " +
                        "Please verify with the official text.";
                return appendToResponse(response, disclaimer);
            }
        }

        return response;
    }

    // ==================== Streaming Path ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String userText = request.prompt().getContents();
        if (containsInjection(userText)) {
            log.warn("BLOCKED prompt injection (stream): '{}'",
                    userText.substring(0, Math.min(100, userText.length())));
            return Flux.just(buildBlockedResponse(request));
        }
        // For streaming, article validation happens in RagService.doOnComplete
        return chain.nextStream(request);
    }

    // ==================== Helpers ====================

    private boolean containsInjection(String text) {
        if (text == null || text.isBlank()) return false;
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> findInvalidArticles(String text) {
        Matcher matcher = ARTICLE_REF_PATTERN.matcher(text);
        return matcher.results()
                .map(mr -> Integer.parseInt(mr.group(1)))
                .filter(num -> num < 1 || num > MAX_VALID_ARTICLE)
                .distinct()
                .toList();
    }

    private String extractContent(ChatClientResponse response) {
        try {
            return response.chatResponse().getResult().getOutput().getText();
        } catch (Exception e) {
            return null;
        }
    }

    private ChatClientResponse buildBlockedResponse(ChatClientRequest request) {
        AssistantMessage blockedMsg = new AssistantMessage(BLOCKED_RESPONSE);
        Generation generation = new Generation(blockedMsg);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(request.context())
                .build();
    }

    private ChatClientResponse appendToResponse(ChatClientResponse original, String suffix) {
        try {
            String originalText = original.chatResponse().getResult().getOutput().getText();
            AssistantMessage newMsg = new AssistantMessage(originalText + suffix);
            Generation generation = new Generation(newMsg);
            ChatResponse chatResponse = new ChatResponse(List.of(generation));
            return ChatClientResponse.builder()
                    .chatResponse(chatResponse)
                    .context(original.context())
                    .build();
        } catch (Exception e) {
            return original;
        }
    }
}
