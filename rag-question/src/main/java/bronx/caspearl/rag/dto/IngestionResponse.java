package bronx.caspearl.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response DTO for document ingestion operations.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestionResponse(
        String status,
        int chunksProcessed,
        int totalPages,
        String message,
        LocalDateTime timestamp
) {
}
