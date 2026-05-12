package bronx.caspearl.rag.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Simple health/liveness check endpoint.
 */
@RestController
@Tag(name = "Health", description = "Application health check")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns application health status")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "rag-question-service",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
