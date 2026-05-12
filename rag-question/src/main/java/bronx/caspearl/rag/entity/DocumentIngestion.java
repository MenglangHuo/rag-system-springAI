package bronx.caspearl.rag.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks document ingestion history.
 * Records which documents have been ingested, their status, and statistics.
 * Enables idempotency checks to prevent duplicate ingestion.
 */
@Entity
@Table(name = "document_ingestions", indexes = {
        @Index(name = "idx_doc_source", columnList = "source_name"),
        @Index(name = "idx_doc_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIngestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "successful_chunks")
    private Integer successfulChunks;

    @Column(name = "failed_chunks")
    private Integer failedChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private IngestionStatus status = IngestionStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum SourceType {
        PDF, MANUAL_ENTRY, URL
    }

    public enum IngestionStatus {
        PENDING, IN_PROGRESS, COMPLETED, PARTIAL, FAILED
    }
}
