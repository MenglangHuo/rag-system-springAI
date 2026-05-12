package bronx.caspearl.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
 */
@Configuration
public class ChatClientConfig {

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    /**
     * Primary ChatClient with full RAG pipeline:
     * 1. SimpleLoggerAdvisor — logs prompts/responses for debugging
     * 2. MessageChatMemoryAdvisor — injects conversation history for multi-turn chat
     * 3. QuestionAnswerAdvisor — retrieves relevant docs from PgVector and injects into context
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory) {
        PromptTemplate systemPrompt = new PromptTemplate(systemPromptResource);
        String systemPromptText = systemPrompt.render();

        return builder
                .defaultSystem(systemPromptText)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(5)
                                        .similarityThreshold(0.65)
                                        .build())
                                .build()
                )
                .build();
    }

    /**
     * A separate ChatClient without RAG advisors — used for query rewriting
     * and structured analysis where we don't want vector store context.
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
