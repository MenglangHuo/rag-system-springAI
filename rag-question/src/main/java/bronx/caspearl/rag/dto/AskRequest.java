package bronx.caspearl.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for asking questions to the RAG system.
 */
public record AskRequest(
        @NotBlank(message = "Question must not be empty")
        @Size(min = 3, max = 2000, message = "Question must be between 3 and 2000 characters")
        String question,
        String sessionId
) {
}
