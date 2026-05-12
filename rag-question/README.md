# 🇰🇭 Cambodia Labour Law — RAG Question Answering Service

A production-grade **Retrieval-Augmented Generation (RAG)** system built with **Spring AI 1.0 GA** that answers questions about Cambodia's Labour Law by semantically searching through the official law document and generating contextual answers using an LLM.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Client / Swagger UI                       │
└──────────────────────────┬───────────────────────────────────────┘
                           │ HTTP
┌──────────────────────────▼───────────────────────────────────────┐
│                       RagController                              │
│  GET  /api/rag/ask?question=...    → Ask (blocking)              │
│  POST /api/rag/ask/stream          → Ask (Streaming SSE)         │
│  POST /api/rag/ingest-law          → Ingest PDF into vector DB   │
│  POST /api/rag/add-info            → Add manual knowledge        │
│  GET  /health                      → Health check                │
└──────────────┬──────────────────────────┬────────────────────────┘
               │                          │
┌──────────────▼──────────┐  ┌────────────▼────────────────────────┐
│       RagService        │  │      PdfIngestionService            │
│                         │  │                                     │
│  1. Rewrite user query  │  │  1. Read PDF (PagePdfDocumentReader)│
│  2. Vector search (RAG) │  │  2. Split into chunks (500 tokens)  │
│  3. LLM generates answer│  │  3. Embed via LLM                   │
│  4. Persist to DB       │  │  4. Store in PgVector               │
└────┬──────────┬─────────┘  │  5. Track in document_ingestions    │
     │          │             └────────────┬───────────────────────┘
     │          │                          │
┌────▼────┐ ┌──▼──────────────┐  ┌────────▼────────────┐
│ Ollama  │ │  PostgreSQL     │  │  PgVector Store     │
│  LLM    │ │  (JPA Tables)   │  │  (vector_store)     │
│         │ │                 │  │                     │
│ gemma3  │ │ chat_conversations│ │ Embeddings +        │
│ nomic-  │ │ chat_messages   │  │ similarity search   │
│ embed   │ │ document_       │  │                     │
│         │ │   ingestions    │  │                     │
└─────────┘ └─────────────────┘  └─────────────────────┘
```

---

## 🔄 RAG Flow (How a Question is Answered)

```
User asks: "What is FDC?"
         │
         ▼
┌─ Step 1: Query Rewriting ──────────────────────────────────────┐
│  LLM rewrites → "What is a Fixed Duration Contract (FDC)      │
│  under Cambodian Labour Law, including its definition,        │
│  maximum duration, renewal conditions, and legal provisions?" │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─ Step 2: Vector Search ───────────────────────────────────────┐
│  QuestionAnswerAdvisor searches PgVector for top-5 most       │
│  similar document chunks using cosine similarity (≥0.65)      │
│  Result: Relevant articles from the Labour Law PDF            │
└────────────────────────────────┬──────────────────────────────┘
                                 │
                                 ▼
┌─ Step 3: LLM Answer Generation ──────────────────────────────┐
│  LLM receives: system prompt + retrieved law context + query  │
│  Generates a legal answer citing specific articles             │
└────────────────────────────────┬──────────────────────────────┘
                                 │
                                 ▼
┌─ Step 4: Persist & Respond ──────────────────────────────────┐
│  Save Q&A to chat_messages table (audit trail)                │
│  Return structured JSON response to the client                │
└───────────────────────────────────────────────────────────────┘
```

---

## 📦 Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.4.5 + Spring AI 1.0.0 GA |
| LLM | Ollama (gemma4:31b-cloud via OpenAI-compatible API |
| Embeddings | nomic-embed-text (768 dimensions) |
| Vector Store | PostgreSQL 16 + pgvector (HNSW index) |
| Database | PostgreSQL (JPA/Hibernate for entities) |
| Resilience | Resilience4j (circuit breaker) + Spring Retry |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven + JDK 21 |

---

## 🗄️ Database Tables

### JPA-Managed Tables (auto-created by Hibernate)

| Table | Purpose |
|-------|---------|
| `chat_conversations` | Groups Q&A messages into sessions |
| `chat_messages` | Stores each question, rewritten query, answer, processing time, status |
| `document_ingestions` | Tracks PDF ingestion history — source, chunks, status, timing |

### Spring AI-Managed Table

| Table | Purpose |
|-------|---------|
| `vector_store` | PgVector embeddings — automatically managed by Spring AI |

---

## 🚀 Getting Started

### Prerequisites

- **JDK 21+**
- **Docker** (for PostgreSQL)
- **Ollama** running locally with models pulled

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

This starts PostgreSQL 16 with the `pgvector` extension on port `5432`.

### 2. Pull Ollama Models

```bash
# Chat model
ollama pull gemma4:31b-cloud

# Embedding model
ollama pull nomic-embed-text
```

### 3. Start the Application

```bash
# Standard mode
./mvnw spring-boot:run

# Development mode (verbose logging)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The app starts on **http://localhost:8090**.

### 4. Ingest the Labour Law PDF (One-Time)

```bash
curl -X POST http://localhost:8090/api/rag/ingest-law
```

This reads `cambodia_labour_law.pdf`, splits it into ~500-token chunks, embeds them via Ollama, and stores vectors in PostgreSQL.

### 5. Ask Questions

```bash
# GET request
curl "http://localhost:8090/api/rag/ask?question=What%20is%20FDC"

# POST request
curl -X POST http://localhost:8090/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the rules for overtime pay?"}'
```

### 6. Swagger UI

Open **http://localhost:8090/swagger-ui.html** for interactive API documentation.

---

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/rag/ask/stream` | Stream tokens via SSE `{"question": "..."}` |
| `POST` | `/api/rag/ingest-law` | Ingest the Labour Law PDF into vector store |
| `POST` | `/api/rag/add-info` | Add manual text to the knowledge base |
| `GET` | `/health` | Application health check |
| `GET` | `/actuator/health` | Spring Actuator health |

### Sample Response

```json
{
  "answer": "A Fixed Duration Contract (FDC) under Cambodian Labour Law is...",
  "originalQuestion": "What is FDC",
  "rewrittenQuestion": "What is a Fixed Duration Contract (FDC) under Cambodian Labour Law...",
  "sources": ["cambodia_labour_law.pdf"],
  "timestamp": "2026-05-07T16:30:00"
}
```

---

## ⚙️ Configuration

All settings can be overridden via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8090` | Application port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rag_question` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `OPENAI_API_KEY` | `ollama` | API key (placeholder for Ollama) |
| `OPENAI_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `CHAT_MODEL` | `gemma3:27b` | LLM model for chat |
| `EMBEDDING_MODEL` | `nomic-embed-text` | Model for embeddings |

---

## 📁 Project Structure

```
src/main/java/bronx/caspearl/rag/
├── AIRAGApplication.java           # Spring Boot entry point
├── config/
│   ├── ChatClientConfig.java       # ChatClient + RAG advisor setup
│   └── RetryConfig.java            # @EnableRetry
├── controller/
│   ├── RagController.java          # REST API endpoints
│   └── HealthController.java       # Health check
├── dto/
│   ├── AskRequest.java             # Question request DTO
│   ├── AskResponse.java            # Answer response DTO
│   ├── IngestionResponse.java      # Ingestion status DTO
│   └── ApiError.java               # Error response DTO
├── entity/
│   ├── ChatConversation.java       # Conversation session entity
│   ├── ChatMessage.java            # Q&A message entity
│   └── DocumentIngestion.java      # Ingestion tracking entity
├── exception/
│   ├── GlobalExceptionHandler.java # Centralized error handling
│   └── IngestionException.java     # Custom exception
├── repository/
│   ├── ChatConversationRepository.java
│   ├── ChatMessageRepository.java
│   └── DocumentIngestionRepository.java
└── services/
    ├── RagService.java             # Core RAG pipeline
    ├── PdfIngestionService.java    # PDF ingestion + embedding
    ├── ToolConfiguration.java      # Spring AI tool calling demo
    ├── FormStatusRequest.java      # Tool calling DTO
    └── FormStatusResponse.java     # Tool calling DTO
```

---

## 🛡️ Resilience Features

- **Circuit Breaker** (Resilience4j): Opens after 50% failure rate, waits 30s before retry
- **Retry** (Spring Retry): 3 attempts with exponential backoff on LLM calls
- **Batch Embedding**: PDF chunks are embedded in batches of 10; individual batch failures don't abort the entire ingestion
- **Graceful Fallback**: Circuit breaker returns user-friendly error message when LLM is unavailable
# rag-system-spring-ai

## Rag_Question cannot reached Ollama localhost
```angular2html
sudo mkdir -p /etc/systemd/system/ollama.service.d
```

```angular2html
sudo tee /etc/systemd/system/ollama.service.d/override.conf << 'EOF'
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
EOF
```

- Then reload and restart:
```angular2html
sudo systemctl daemon-reload
sudo systemctl restart ollama
```
