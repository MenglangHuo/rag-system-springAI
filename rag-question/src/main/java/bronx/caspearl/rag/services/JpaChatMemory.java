package bronx.caspearl.rag.services;

import bronx.caspearl.rag.entity.ChatConversation;
import bronx.caspearl.rag.entity.ChatMessage;
import bronx.caspearl.rag.repository.ChatConversationRepository;
import bronx.caspearl.rag.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A persistent implementation of Spring AI's ChatMemory that reads from
 * our existing PostgreSQL ChatMessageRepository.
 * This ensures the LLM retains conversation context even if the server restarts.
 *
 * Important: The conversationId passed by Spring AI's MessageChatMemoryAdvisor
 * is our "sessionId" string (e.g., "x7k9p2m4"), NOT the UUID primary key.
 * We must resolve sessionId → ChatConversation.id (UUID) before querying messages.
 *
 * Supports configurable window size and conversation summary:
 * when total messages exceed the window, older messages are condensed into
 * a summary system message to prevent context loss.
 */
@Slf4j
@Service
public class JpaChatMemory implements ChatMemory {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository chatConversationRepository;

    /**
     * Number of Q&A rows to fetch from DB. Each row = 2 LLM messages (User + Assistant).
     * Configurable via application properties: rag.memory.window-size
     */
    @Value("${rag.memory.window-size:10}")
    private int windowSize;

    /**
     * When total messages exceed this multiple of windowSize, older messages
     * are summarized into a single system message. Default: 2x window.
     */
    @Value("${rag.memory.summary-threshold-multiplier:2}")
    private int summaryThresholdMultiplier;

    public JpaChatMemory(ChatMessageRepository chatMessageRepository,
                         ChatConversationRepository chatConversationRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatConversationRepository = chatConversationRepository;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // No-op!
        // Spring AI calls this to save messages, but we ALREADY save them manually
        // in RagService.java so we can track custom metadata like processingTime and Tokens.
        // If we implemented this, we would get duplicate database rows.
        log.debug("JpaChatMemory.add() ignored. Persistence is handled manually in RagService.");
    }

    @Override
    public List<Message> get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }

        try {
            // Resolve the sessionId string to the actual ChatConversation entity
            Optional<ChatConversation> conversationOpt =
                    chatConversationRepository.findBySessionId(conversationId);

            if (conversationOpt.isEmpty()) {
                log.debug("No conversation found for sessionId: {}", conversationId);
                return new ArrayList<>();
            }

            ChatConversation conversation = conversationOpt.get();

            // Fetch recent messages within the window
            Page<ChatMessage> recentPage = chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversation.getId(), PageRequest.of(0, windowSize));

            // DB returns newest first. We need oldest first for the LLM context window.
            List<ChatMessage> recentMessages = new ArrayList<>(recentPage.getContent());
            Collections.reverse(recentMessages);

            List<Message> springAiMessages = new ArrayList<>();

            // Check if there are older messages beyond the window that should be summarized
            long totalMessages = recentPage.getTotalElements();
            if (totalMessages > (long) windowSize * summaryThresholdMultiplier) {
                // Summarize older messages into a system message
                String summary = buildOlderMessagesSummary(conversation, recentMessages);
                if (summary != null && !summary.isBlank()) {
                    springAiMessages.add(new SystemMessage(
                            "Summary of earlier conversation: " + summary));
                }
            }

            // Add recent messages
            for (ChatMessage cm : recentMessages) {
                // Only include fully completed exchanges where we have both Q and A
                if (cm.getStatus() == ChatMessage.MessageStatus.COMPLETED && cm.getAnswer() != null) {
                    springAiMessages.add(new UserMessage(cm.getOriginalQuestion()));
                    springAiMessages.add(new AssistantMessage(cm.getAnswer()));
                }
            }

            log.info("JpaChatMemory restored {} messages (including summary) for session '{}'",
                    springAiMessages.size(), conversationId);
            return springAiMessages;

        } catch (Exception e) {
            log.warn("Failed to retrieve chat memory for session '{}': {}", conversationId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void clear(String conversationId) {
        log.debug("JpaChatMemory.clear() called for {}", conversationId);
        // We leave this as a no-op so we don't accidentally delete permanent chat history.
    }

    /**
     * Builds a condensed summary of older messages that are outside the recent window.
     * This prevents complete context loss for long conversations.
     */
    private String buildOlderMessagesSummary(ChatConversation conversation,
                                              List<ChatMessage> recentMessages) {
        try {
            // Fetch the older messages (page 1 = the ones before the recent window)
            Page<ChatMessage> olderPage = chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversation.getId(), PageRequest.of(1, windowSize));

            if (olderPage.isEmpty()) return null;

            List<ChatMessage> olderMessages = new ArrayList<>(olderPage.getContent());
            Collections.reverse(olderMessages);

            StringBuilder summary = new StringBuilder();
            summary.append("The user previously discussed these topics: ");

            for (ChatMessage cm : olderMessages) {
                if (cm.getStatus() == ChatMessage.MessageStatus.COMPLETED && cm.getOriginalQuestion() != null) {
                    // Include only the question topics (not full answers) to keep summary compact
                    String question = cm.getOriginalQuestion();
                    if (question.length() > 100) {
                        question = question.substring(0, 100) + "...";
                    }
                    summary.append("\"").append(question).append("\"; ");
                }
            }

            return summary.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to build older messages summary: {}", e.getMessage());
            return null;
        }
    }
}
