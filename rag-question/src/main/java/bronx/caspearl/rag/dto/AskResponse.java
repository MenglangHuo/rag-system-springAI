package bronx.caspearl.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for RAG question-answering responses.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskResponse(
        String answer,
        String originalQuestion,
        String rewrittenQuestion,
        String sessionId,
        List<String> sources,
        LocalDateTime timestamp
) {
}
