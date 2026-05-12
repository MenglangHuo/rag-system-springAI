package bronx.caspearl.rag.services;

import bronx.caspearl.rag.entity.ChatConversation;
import bronx.caspearl.rag.entity.ChatMessage;
import bronx.caspearl.rag.repository.ChatConversationRepository;
import bronx.caspearl.rag.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
 * is our "sessionId" string (e.g. "x7k9p2m4"), NOT the UUID primary key.
 * We must resolve sessionId → ChatConversation.id (UUID) before querying messages.
 */
@Slf4j
@Service
public class JpaChatMemory implements ChatMemory {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository chatConversationRepository;

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

            // Each Q&A exchange in our DB is 1 row, but equals 2 Messages for the LLM (User + Assistant).
            // Fetching the last 10 rows equals 20 messages.
            int rowsToFetch = 10;

            Page<ChatMessage> page = chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversation.getId(), PageRequest.of(0, rowsToFetch));

            // DB returns newest first. We need oldest first for the LLM context window.
            List<ChatMessage> chatMessages = new ArrayList<>(page.getContent());
            Collections.reverse(chatMessages);

            List<Message> springAiMessages = new ArrayList<>();
            for (ChatMessage cm : chatMessages) {
                // Only include fully completed exchanges where we have both Q and A
                if (cm.getStatus() == ChatMessage.MessageStatus.COMPLETED && cm.getAnswer() != null) {
                    springAiMessages.add(new UserMessage(cm.getOriginalQuestion()));
                    springAiMessages.add(new AssistantMessage(cm.getAnswer()));
                }
            }

            log.info("JpaChatMemory restored {} messages for session '{}'",
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
}
