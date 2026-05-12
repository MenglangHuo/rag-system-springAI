package bronx.caspearl.rag.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advisor that captures documents retrieved by QuestionAnswerAdvisor
 * and stores them so RagService can include real source citations
 * (page numbers, article references) in AskResponse.
 *
 * Order is set to LOWEST so this advisor wraps everything —
 * its post-processing runs AFTER QuestionAnswerAdvisor has stored docs in context.
 */
@Slf4j
@Component
public class SourceCitationAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * Thread-safe map: conversationId → list of source reference strings.
     * RagService calls getAndClearSources() after each request.
     */
    private final ConcurrentHashMap<String, List<String>> sourcesMap = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "SourceCitationAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    // ==================== Blocking Path ====================

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        captureSourcesFromContext(request, response);
        return response;
    }

    // ==================== Streaming Path ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request)
                .doOnNext(response -> {
                    String convId = resolveConversationId(request);
                    if (!sourcesMap.containsKey(convId)) {
                        captureSourcesFromContext(request, response);
                    }
                });
    }

    // ==================== Public API for RagService ====================

    /**
     * Retrieves and clears the captured sources for a given conversation.
     * Called by RagService after a call/stream completes.
     */
    public List<String> getAndClearSources(String conversationId) {
        List<String> sources = sourcesMap.remove(conversationId);
        return sources != null ? sources : Collections.emptyList();
    }

    // ==================== Helpers ====================

    @SuppressWarnings("unchecked")
    private void captureSourcesFromContext(ChatClientRequest request, ChatClientResponse response) {
        try {
            Map<String, Object> context = response.context();
            Object docsObj = context.get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);

            if (docsObj instanceof List<?> docsList && !docsList.isEmpty()) {
                List<String> formattedSources = ((List<Document>) docsList).stream()
                        .map(this::formatDocumentSource)
                        .distinct()
                        .toList();

                String convId = resolveConversationId(request);
                sourcesMap.put(convId, formattedSources);
                log.debug("Captured {} source citations for session '{}'",
                        formattedSources.size(), convId);
            }
        } catch (Exception e) {
            log.debug("Could not capture source citations: {}", e.getMessage());
        }
    }

    private String formatDocumentSource(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        String sourceName = (String) metadata.getOrDefault("source_name", "cambodia_labour_law.pdf");
        Object pageNum = metadata.get("page_number");
        Object articleNum = metadata.get("article_number");

        StringBuilder sb = new StringBuilder(sourceName);
        if (pageNum != null) {
            sb.append(" — Page ").append(pageNum);
        }
        if (articleNum != null) {
            sb.append(" — Article ").append(articleNum);
        }

        String text = doc.getText();
        if (text != null && text.length() > 20) {
            String preview = text.substring(0, Math.min(80, text.length())).replaceAll("\\s+", " ").trim();
            sb.append(" (\"").append(preview).append("...\")");
        }

        return sb.toString();
    }

    private String resolveConversationId(ChatClientRequest request) {
        try {
            Object convId = request.context().get(ChatMemory.CONVERSATION_ID);
            return convId != null ? convId.toString() : "default";
        } catch (Exception e) {
            return "default";
        }
    }
}
