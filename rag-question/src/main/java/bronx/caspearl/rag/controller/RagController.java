package bronx.caspearl.rag.controller;

import bronx.caspearl.rag.dto.AskRequest;
import bronx.caspearl.rag.dto.AskResponse;
import bronx.caspearl.rag.dto.CompareRequest;
import bronx.caspearl.rag.dto.IngestionResponse;
import bronx.caspearl.rag.dto.LegalAnalysis;
import bronx.caspearl.rag.services.PdfIngestionService;
import bronx.caspearl.rag.services.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG - Cambodia Labour Law", description = "Question answering for Cambodia Labour Law")
public class RagController {

    private final RagService ragService;
    private final PdfIngestionService pdfIngestionService;

    // ==================== Streaming endpoint (SSE) ====================

    /**
     * Streams tokens as Server-Sent Events.
     * Vue 3 should use EventSource or fetch() with ReadableStream on this endpoint.
     * The first event will be "[SESSION:uuid]" — Vue should capture this as the sessionId.
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream an answer (SSE)",
            description = "Returns a token-by-token stream. First event contains the session ID.")
    public Flux<String> askStream(@Valid @RequestBody AskRequest request) {
        log.info("SSE /ask/stream - question: {}, session: {}", request.question(), request.sessionId());
        return ragService.askStream(request.question(), request.sessionId());
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question (POST, blocking)")
    public ResponseEntity<AskResponse> askPost(@Valid @RequestBody AskRequest request) {
        log.info("POST /ask - question: {}, session: {}", request.question(), request.sessionId());
        return ResponseEntity.ok(ragService.ask(request.question(), request.sessionId()));
    }

    // ==================== Template-Driven Features ====================

    @PostMapping("/compare")
    @Operation(summary = "Compare two legal concepts",
            description = "Compare two labour law concepts using a structured comparison template (e.g., FDC vs UDC).")
    public ResponseEntity<AskResponse> compareConcepts(@Valid @RequestBody CompareRequest request) {
        log.info("POST /compare - {} vs {}, session: {}", request.concept1(), request.concept2(), request.sessionId());
        return ResponseEntity.ok(ragService.compareConcepts(request.concept1(), request.concept2(), request.sessionId()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Summarize a legal topic",
            description = "Get a structured summary of a labour law topic (e.g., overtime pay, maternity leave).")
    public ResponseEntity<AskResponse> summarizeTopic(
            @Parameter(description = "Topic to summarize", example = "overtime pay")
            @RequestParam @NotBlank String topic,
            @Parameter(description = "Session ID for conversation continuity (optional)")
            @RequestParam(required = false) String sessionId) {
        log.info("GET /summary - topic: {}, session: {}", topic, sessionId);
        return ResponseEntity.ok(ragService.summarizeTopic(topic, sessionId));
    }

    @GetMapping("/article/{articleNumber}")
    @Operation(summary = "Look up a specific article",
            description = "Look up a specific article from the Cambodia Labour Law by number (e.g., 67, 73).")
    public ResponseEntity<AskResponse> lookupArticle(
            @Parameter(description = "Article number to look up", example = "67")
            @PathVariable @NotBlank String articleNumber,
            @Parameter(description = "Session ID for conversation continuity (optional)")
            @RequestParam(required = false) String sessionId) {
        log.info("GET /article/{}, session: {}", articleNumber, sessionId);
        return ResponseEntity.ok(ragService.lookupArticle(articleNumber, sessionId));
    }

    @GetMapping("/analyze")
    @Operation(summary = "Structured legal analysis",
            description = "Analyze a topic and return structured JSON with summary, relevant articles, key provisions, and implications.")
    public ResponseEntity<LegalAnalysis> analyzeTopic(
            @Parameter(description = "Topic to analyze", example = "termination of employment")
            @RequestParam @NotBlank String topic) {
        log.info("GET /analyze - topic: {}", topic);
        return ResponseEntity.ok(ragService.analyzeTopic(topic));
    }

    // ==================== Document Ingestion ====================

    @PostMapping("/ingest-law")
    @Operation(summary = "Ingest Cambodia Labour Law PDF",
            description = "Reads the Cambodia Labour Law PDF, splits it into chunks, embeds them, and stores in the vector database.")
    public ResponseEntity<IngestionResponse> ingestLaw() {
        log.info("POST /ingest-law — Starting ingestion...");
        return ResponseEntity.ok(pdfIngestionService.loadPdfIntoDatabase());
    }

    @PostMapping("/add-info")
    @Operation(summary = "Add manual knowledge",
            description = "Add a custom text snippet to the knowledge base.")
    public ResponseEntity<IngestionResponse> addInfo(
            @Parameter(description = "Text content to add to the knowledge base")
            @RequestParam @NotBlank String text) {
        log.info("POST /add-info — Adding manual info ({} chars)", text.length());
        return ResponseEntity.ok(pdfIngestionService.addManualInfo(text));
    }
}
