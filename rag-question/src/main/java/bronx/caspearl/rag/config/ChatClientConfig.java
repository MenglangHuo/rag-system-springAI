package bronx.caspearl.rag.config;

import bronx.caspearl.rag.advisor.SourceCitationAdvisor;
import bronx.caspearl.rag.services.ArticleLookupTool;
import bronx.caspearl.rag.services.LegalCalculatorTools;
import bronx.caspearl.rag.services.ToolConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Configures ChatClient beans with Spring AI 1.0 GA best practices:
 * - Externalized prompt templates (.st files)
 * - QuestionAnswerAdvisor for RAG vector search
 * - MessageChatMemoryAdvisor for multi-turn conversations
 * - SimpleLoggerAdvisor for observability/debugging
 * - SafeGuardAdvisor for prompt injection defense + hallucination validation
 * - SourceCitationAdvisor for real document source citations
 * - Tool calling: checkFormStatus, severance/notice/probation calculators, article lookup
 */
@Configuration
public class ChatClientConfig {

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    /**
     * Primary ChatClient with full RAG pipeline + tool calling + safety advisors:
     * 1. SafeGuardAdvisor — blocks prompt injection, validates article references
     * 2. SimpleLoggerAdvisor — logs prompts/responses for debugging
     * 3. MessageChatMemoryAdvisor — injects conversation history for multi-turn chat
     * 4. QuestionAnswerAdvisor — retrieves relevant docs from PgVector
     * 5. SourceCitationAdvisor — captures retrieved docs for source citations
     *
     * Tools (LLM can call autonomously):
     * - checkFormStatus — look up form processing status
     * - calculateSeverance — compute dismissal indemnity
     * - calculateNoticePeriod — compute required notice period
     * - calculateProbationEndDate — compute probation end date
     * - lookupArticleByNumber — targeted vector search for specific articles
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 VectorStore vectorStore,
                                 ChatMemory chatMemory,
                                 ToolConfiguration toolConfig,
                                 LegalCalculatorTools legalCalculatorTools,
                                 ArticleLookupTool articleLookupTool,
                                 @Qualifier("customSafeGuardAdvisor") bronx.caspearl.rag.advisor.SafeGuardAdvisor safeGuardAdvisor,
                                 SourceCitationAdvisor sourceCitationAdvisor) {

        PromptTemplate systemPrompt = new PromptTemplate(systemPromptResource);
        String systemPromptText = systemPrompt.render();

        return builder
                .defaultSystem(systemPromptText)
                .defaultTools(toolConfig, legalCalculatorTools, articleLookupTool)
                .defaultAdvisors(
                        safeGuardAdvisor,
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(5)
                                        .similarityThreshold(0.65)
                                        .build())
                                .build(),
                        sourceCitationAdvisor
                )
                .build();
    }

    /**
     * A separate ChatClient without RAG advisors — used for query rewriting,
     * follow-up classification, and structured analysis where we don't want
     * vector store context or tool calling.
     */
    @Bean("rewriteChatClient")
    public ChatClient rewriteChatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * JpaChatMemory — Our custom persistent implementation.
     * Reads directly from PostgreSQL so conversations survive server restarts.
     */
    @Bean
    public ChatMemory chatMemory(bronx.caspearl.rag.services.JpaChatMemory jpaChatMemory) {
        return jpaChatMemory;
    }
}
