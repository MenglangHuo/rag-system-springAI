package bronx.caspearl.rag.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM-callable tool that performs a targeted vector search for a specific
 * article number from the Cambodia Labour Law. This is more precise than
 * the general QuestionAnswerAdvisor because it constructs an article-specific
 * search query.
 */
@Slf4j
@Service
public class ArticleLookupTool {

    private final VectorStore vectorStore;

    public ArticleLookupTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "Look up the exact text and content of a specific article number " +
                         "from the Cambodia Labour Law. Use this when the user asks about " +
                         "a specific article like 'Article 67' or 'What does Article 89 say?'")
    public String lookupArticleByNumber(
            @ToolParam(description = "The article number to look up, e.g., '67', '73', '89'")
            String articleNumber) {

        log.info(">>> Tool: lookupArticleByNumber({})", articleNumber);

        // Build a targeted search query for the specific article
        String searchQuery = "Article " + articleNumber +
                " of the Cambodia Labour Law full text provisions and requirements";

        SearchRequest request = SearchRequest.builder()
                .query(searchQuery)
                .topK(3)
                .similarityThreshold(0.5) // Lower threshold for precise article lookups
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);

        if (docs == null || docs.isEmpty()) {
            return "Article " + articleNumber + " was not found in the knowledge base. " +
                   "The article may not exist or may not have been ingested yet.";
        }

        StringBuilder result = new StringBuilder();
        result.append("Found ").append(docs.size())
              .append(" relevant chunk(s) for Article ").append(articleNumber).append(":\n\n");

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            result.append("--- Chunk ").append(i + 1).append(" ---\n");

            // Include metadata if available
            if (doc.getMetadata().containsKey("page_number")) {
                result.append("Page: ").append(doc.getMetadata().get("page_number")).append("\n");
            }

            result.append(doc.getText()).append("\n\n");
        }

        return result.toString();
    }
}
