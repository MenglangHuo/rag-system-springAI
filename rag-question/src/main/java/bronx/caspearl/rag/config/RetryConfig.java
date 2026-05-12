package bronx.caspearl.rag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry across the application.
 * Methods annotated with @Retryable will automatically retry on failure.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
