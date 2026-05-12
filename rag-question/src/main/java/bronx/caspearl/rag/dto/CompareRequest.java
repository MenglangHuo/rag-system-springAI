package bronx.caspearl.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for comparing two legal concepts.
 */
public record CompareRequest(
        @NotBlank(message = "First concept must not be blank")
        String concept1,
        @NotBlank(message = "Second concept must not be blank")
        String concept2,
        String sessionId
) {}
