package bronx.caspearl.rag.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single Q&A exchange within a conversation.
 * Stores the original question, rewritten question, and AI-generated answer.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_msg_conversation", columnList = "conversation_id"),
        @Index(name = "idx_chat_msg_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ChatConversation conversation;

    @Column(name = "original_question", nullable = false, columnDefinition = "TEXT")
    private String originalQuestion;

    @Column(name = "rewritten_question", columnDefinition = "TEXT")
    private String rewrittenQuestion;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum MessageStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
