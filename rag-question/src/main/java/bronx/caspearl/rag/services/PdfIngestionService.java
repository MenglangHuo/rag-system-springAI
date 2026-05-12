package bronx.caspearl.rag.services;

import bronx.caspearl.rag.dto.IngestionResponse;
import bronx.caspearl.rag.entity.DocumentIngestion;
import bronx.caspearl.rag.exception.IngestionException;
import bronx.caspearl.rag.repository.DocumentIngestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for ingesting PDF documents into the PgVector store.
 * Reads the Cambodia Labour Law PDF, splits it into chunks, embeds them,
 * and stores the vectors in PostgreSQL. Tracks all ingestion history in the database.
 */
@Slf4j
@Service
public class PdfIngestionService {

    private final VectorStore vectorStore;
    private final DocumentIngestionRepository ingestionRepository;

    private static final int CHUNK_TOKEN_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;
    private static final int BATCH_SIZE = 10;
    private static final String LAW_SOURCE_NAME = "cambodia_labour_law.pdf";

    @Value("classpath:cambodia_labour_law.pdf")
    private Resource pdfResource;

    public PdfIngestionService(VectorStore vectorStore, DocumentIngestionRepository ingestionRepository) {
        this.vectorStore = vectorStore;
        this.ingestionRepository = ingestionRepository;
    }

    /**
     * Reads the Cambodia Labour Law PDF, splits into chunks, and stores embeddings.
     * Persists ingestion history to the document_ingestions table.
     *
     * @return IngestionResponse with status and statistics
     */
    @Transactional
    public IngestionResponse loadPdfIntoDatabase() {
        log.info("Starting PDF ingestion for Cambodia Labour Law...");
        long startTime = System.currentTimeMillis();

        // Create ingestion record
        DocumentIngestion ingestion = DocumentIngestion.builder()
                .sourceName(LAW_SOURCE_NAME)
                .sourceType(DocumentIngestion.SourceType.PDF)
                .status(DocumentIngestion.IngestionStatus.IN_PROGRESS)
                .build();
        ingestion = ingestionRepository.save(ingestion);

        try {
            // 1. Read the PDF
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
            List<Document> documents;
            try {
                documents = pdfReader.get();
            } catch (Exception e) {
                throw new IngestionException("Failed to read PDF document", e);
            }

            if (documents.isEmpty()) {
                throw new IngestionException("The PDF reader found 0 pages in the document");
            }

            int totalPages = documents.size();
            ingestion.setTotalPages(totalPages);
            log.info("Extracted {} pages from PDF", totalPages);

            // 2. Validate first page has content
            String sampleText = documents.getFirst().getText();
            if (sampleText == null || sampleText.trim().isEmpty()) {
                throw new IngestionException(
                        "The PDF text is empty. It might be a scanned image or use non-standard fonts.");
            }

            // 3. Split into smaller chunks with overlap
            TokenTextSplitter textSplitter = new TokenTextSplitter(
                    CHUNK_TOKEN_SIZE, CHUNK_OVERLAP, 5, 10000, true);
            List<Document> splitDocuments = textSplitter.apply(documents);

            // 4. Clean control characters
            List<Document> cleanedDocuments = splitDocuments.stream()
                    .filter(doc -> doc.getText() != null && !doc.getText().trim().isEmpty())
                    .map(doc -> new Document(
                            doc.getText().replaceAll("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F]", " "),
                            doc.getMetadata()))
                    .toList();

            ingestion.setTotalChunks(cleanedDocuments.size());
            log.info("Split into {} chunks, sending for embedding...", cleanedDocuments.size());

            // 5. Embed and store in batches
            int successfulBatches = 0;
            int failedBatches = 0;

            for (int i = 0; i < cleanedDocuments.size(); i += BATCH_SIZE) {
                int end = Math.min(cleanedDocuments.size(), i + BATCH_SIZE);
                List<Document> batch = cleanedDocuments.subList(i, end);
                int batchNumber = (i / BATCH_SIZE) + 1;

                try {
                    embedBatch(batch);
                    successfulBatches++;
                    log.debug("Successfully embedded batch {}", batchNumber);
                } catch (Exception e) {
                    failedBatches++;
                    log.error("Failed to embed batch {}: {}", batchNumber, e.getMessage());
                }
            }

            if (failedBatches > 0 && successfulBatches == 0) {
                throw new IngestionException("All embedding batches failed. Check LLM connectivity.");
            }

            // 6. Update ingestion record
            long processingTime = System.currentTimeMillis() - startTime;
            ingestion.setSuccessfulChunks(successfulBatches * BATCH_SIZE);
            ingestion.setFailedChunks(failedBatches * BATCH_SIZE);
            ingestion.setProcessingTimeMs(processingTime);
            ingestion.setCompletedAt(LocalDateTime.now());
            ingestion.setStatus(failedBatches == 0
                    ? DocumentIngestion.IngestionStatus.COMPLETED
                    : DocumentIngestion.IngestionStatus.PARTIAL);
            ingestionRepository.save(ingestion);

            String status = failedBatches == 0 ? "SUCCESS" : "PARTIAL";
            String message = String.format(
                    "Cambodia Labour Law loaded: %d/%d batches succeeded, %d total chunks in %dms",
                    successfulBatches, successfulBatches + failedBatches, cleanedDocuments.size(), processingTime);

            log.info(message);

            return IngestionResponse.builder()
                    .status(status)
                    .chunksProcessed(cleanedDocuments.size())
                    .totalPages(totalPages)
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            ingestion.setStatus(DocumentIngestion.IngestionStatus.FAILED);
            ingestion.setErrorMessage(e.getMessage());
            ingestion.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            ingestionRepository.save(ingestion);
            throw e;
        }
    }

    /**
     * Adds manual text to the vector store and tracks it.
     */
    @Transactional
    public IngestionResponse addManualInfo(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text must not be empty");
        }

        DocumentIngestion ingestion = DocumentIngestion.builder()
                .sourceName("manual_entry_" + System.currentTimeMillis())
                .sourceType(DocumentIngestion.SourceType.MANUAL_ENTRY)
                .status(DocumentIngestion.IngestionStatus.IN_PROGRESS)
                .totalChunks(1)
                .build();
        ingestion = ingestionRepository.save(ingestion);

        Document manualDoc = new Document(
                text,
                Map.of("source", "manual_entry", "category", "labour_law_snippet"));

        vectorStore.add(List.of(manualDoc));

        ingestion.setStatus(DocumentIngestion.IngestionStatus.COMPLETED);
        ingestion.setSuccessfulChunks(1);
        ingestion.setCompletedAt(LocalDateTime.now());
        ingestionRepository.save(ingestion);

        log.info("Manual info added to vector store ({} chars)", text.length());

        return IngestionResponse.builder()
                .status("SUCCESS")
                .chunksProcessed(1)
                .message("Successfully added manual info to Knowledge Base")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Returns ingestion history for a given source.
     */
    public List<DocumentIngestion> getIngestionHistory(String sourceName) {
        return ingestionRepository.findBySourceNameOrderByCreatedAtDesc(
                sourceName != null ? sourceName : LAW_SOURCE_NAME);
    }

    /**
     * Embeds a single batch with retry support.
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    protected void embedBatch(List<Document> batch) {
        vectorStore.add(batch);
    }
}
