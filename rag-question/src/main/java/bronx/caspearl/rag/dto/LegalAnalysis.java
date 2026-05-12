package bronx.caspearl.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured output DTO for legal analysis responses.
 * Used with Spring AI's BeanOutputConverter to ensure
 * the LLM returns a well-structured JSON response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalAnalysis {

    private String summary;
    private List<String> relevantArticles;
    private List<KeyProvision> keyProvisions;
    private String practicalImplication;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyProvision {
        private String articleNumber;
        private String title;
        private String description;
    }
}
