# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Start PostgreSQL with pgvector (required before running the app)
docker-compose up -d

# Set required environment variable
export OPENAI_API_KEY=sk-...

# Run the application
mvn spring-boot:run

# Build JAR
mvn clean package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=DocumentIngestionServiceTest

# Run a single test method
mvn test -Dtest=DocumentIngestionServiceTest#shouldReturnFailedResponseForEmptyPdf
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the app is running.

## Architecture

FinRAG is a Spring Boot RAG (Retrieval-Augmented Generation) backend for financial document Q&A. It uses Spring AI 1.0.0-M3 (milestone), OpenAI APIs, and PostgreSQL with pgvector for vector storage.

### Two Core Pipelines

**Ingest:** PDF upload → PDFBox text extraction → overlapping 500-word chunks → OpenAI `text-embedding-3-small` (1536-dim) → pgvector (HNSW index, cosine distance)

**Query:** User question → embed → pgvector HNSW similarity search (top-5) → inject retrieved chunks into system prompt → GPT-4o (temp=0.2) → response with source citations

### Layer Overview

- **`controller/FinRagController`** — REST endpoints: `POST /api/v1/documents/ingest`, `POST /api/v1/query`, `GET /api/v1/documents`, `GET /api/v1/conversations/{id}`
- **`service/DocumentIngestionService`** — PDF parsing, sliding-window chunking, embedding, vector store writes
- **`service/RagQueryService`** — vector search, prompt construction with context injection, conversation history, LLM call via Spring AI `ChatClient`
- **`config/RagProperties`** — `@ConfigurationProperties`-bound RAG tuning params (chunk-size, chunk-overlap, top-k, similarity-threshold) — tune via `application.yml`
- **`config/ChatClientConfig`** — Spring AI `ChatClient` bean (fluent LLM API)
- **`model/FinRagModels`** — all request/response DTOs

## Code Style

Use comments sparingly. Only comment complex or non-obvious code.

### Key Infrastructure

- **Database:** PostgreSQL 16 + pgvector, managed via `docker-compose.yml`. Spring AI auto-manages the `vector_store` table; `init.sql` only enables the extension.
- **Spring AI version:** 1.0.0-M3 (milestone) — uses Spring Milestones Maven repository. API surface may differ from GA release.
- **OpenAI key** must be set as `OPENAI_API_KEY` env var; not hardcoded anywhere.
