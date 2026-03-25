# FinRAG — Financial Document Q&A Assistant

A **RAG (Retrieval-Augmented Generation)** backend built in Java with Spring AI.  
Upload financial PDFs (annual reports, payment policies, compliance docs) and ask natural language questions.

---

## Architecture

```
                        ┌─────────────────────────────────────────────────┐
  INGEST PIPELINE       │                                                 │
                        │   PDF Upload                                    │
                        │       │                                         │
                        │   PDFBox → Extract text per page               │
                        │       │                                         │
                        │   Chunker → Split into 500-word overlapping    │
                        │             segments (overlap=100)              │
                        │       │                                         │
                        │   OpenAI Embeddings API                        │
                        │   (text-embedding-3-small → 1536-dim vector)   │
                        │       │                                         │
                        │   pgvector (PostgreSQL) → Store chunks+vectors │
                        └─────────────────────────────────────────────────┘

                        ┌─────────────────────────────────────────────────┐
  QUERY PIPELINE        │                                                 │
                        │   User Question                                 │
                        │       │                                         │
                        │   OpenAI Embeddings → Embed the question       │
                        │       │                                         │
                        │   pgvector HNSW Index → Find top-5 similar    │
                        │             chunks by cosine distance          │
                        │       │                                         │
                        │   Prompt Builder → Inject chunks as context    │
                        │       │                                         │
                        │   GPT-4o → Generate grounded answer           │
                        │       │                                         │
                        │   Response with sources + citations            │
                        └─────────────────────────────────────────────────┘
```

---

## Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- OpenAI API key → [platform.openai.com](https://platform.openai.com)

---

## Quick Start

### 1. Start PostgreSQL with pgvector

```bash
docker-compose up -d
```

### 2. Set your OpenAI API key

```bash
export OPENAI_API_KEY=sk-...your-key-here...
```

### 3. Run the application

```bash
mvn spring-boot:run
```

### 4. Open Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## API Usage

### Ingest a PDF

```bash
curl -X POST http://localhost:8080/api/v1/documents/ingest \
  -F "file=@annual_report_2024.pdf"
```

Response:
```json
{
  "documentId": "a3f1c2d4-...",
  "filename": "annual_report_2024.pdf",
  "chunksCreated": 142,
  "status": "SUCCESS"
}
```

### Ask a Question

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What were the key risk factors mentioned in the report?",
    "documentId": "a3f1c2d4-..."
  }'
```

Response:
```json
{
  "conversationId": "b5e2...",
  "question": "What were the key risk factors?",
  "answer": "The report highlights three key risk factors...",
  "sources": [
    { "filename": "annual_report_2024.pdf", "pageNumber": 34, "excerpt": "..." }
  ]
}
```

### Multi-turn conversation

```bash
# Second question — pass conversationId from previous response
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Which of those risks is most likely to impact revenue?",
    "conversationId": "b5e2..."
  }'
```

---

## Project Structure

```
src/main/java/com/finrag/
├── FinRagApplication.java         ← Spring Boot entry point
├── config/
│   ├── AppConfig.java             ← Swagger config
│   ├── ChatClientConfig.java      ← Spring AI ChatClient bean
│   └── RagProperties.java         ← Tunable RAG parameters
├── controller/
│   └── FinRagController.java      ← REST endpoints (ingest + query)
├── service/
│   ├── DocumentIngestionService.java  ← PDF → chunks → pgvector
│   └── RagQueryService.java           ← vector search → LLM → answer
└── model/
    └── FinRagModels.java          ← Request/Response DTOs
```

---

## Key Concepts Learned

| Concept | Where in code |
|---------|--------------|
| **Chunking** | `DocumentIngestionService.chunkPages()` |
| **Overlapping chunks** | `start += (chunkSize - overlap)` sliding window |
| **Embeddings** | Auto-called by `vectorStore.add()` via Spring AI |
| **Vector similarity search** | `vectorStore.similaritySearch()` with COSINE distance |
| **Prompt stuffing** | `RagQueryService.buildSystemPrompt()` |
| **Grounding** | System prompt rules preventing hallucination |
| **Conversation history** | `buildConversationHistory()` + turn storage |
| **Source citations** | Metadata stored per chunk, returned with answer |

---

## Tuning Parameters

Edit `application.yml` → `finrag.rag`:

| Parameter | Default | Effect |
|-----------|---------|--------|
| `chunk-size` | 500 | Larger = more context per chunk, more tokens |
| `chunk-overlap` | 100 | Larger = better boundary handling, more storage |
| `top-k` | 5 | More chunks = richer context, higher LLM cost |
| `similarity-threshold` | 0.7 | Higher = stricter match, fewer results |

---

## Stretch Goals (After You Get It Running)

- [ ] **Testcontainers** — integration tests with real Postgres+pgvector
- [ ] **Multi-document comparison** — "How does policy A differ from policy B?"
- [ ] **Fraud pattern detection** — Upload transaction CSVs, ask anomaly questions
- [ ] **Ollama integration** — Swap OpenAI for a local model (air-gapped enterprise simulation)
- [ ] **Redis conversation store** — Replace in-memory Map with Redis for persistence
- [ ] **Role-based doc access** — Spring Security + doc-level ACLs
- [ ] **React frontend** — Simple chat UI over the existing REST API

---

## Cost Estimate (OpenAI API)

| Operation | Model | Cost |
|-----------|-------|------|
| Embedding 100-page PDF | text-embedding-3-small | ~$0.002 |
| Per Q&A query | GPT-4o | ~$0.01–0.03 |

Very cheap for experimentation. Set a spending limit on your OpenAI account.

---

## Good PDFs to Test With

- Company annual reports (available on investor relations pages)
- RBI payment system guidelines (public PDFs)
- PCI-DSS compliance documentation
- Mastercard/Visa public rule books
