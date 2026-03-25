package com.finrag.controller;

import com.finrag.model.FinRagModels.*;
import com.finrag.service.DocumentIngestionService;
import com.finrag.service.RagQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "FinRAG API", description = "Financial Document Q&A using RAG")
public class FinRagController {

    private final DocumentIngestionService ingestionService;
    private final RagQueryService queryService;

    // ── INGEST ────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/documents/ingest
     *
     * Upload a PDF → extract text → chunk → embed → store in pgvector.
     * Try it with: an annual report, a payment policy doc, or a compliance guide.
     */
    @PostMapping(value = "/documents/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest a PDF document", description = "Parses, chunks, embeds and stores a PDF in the vector store")
    public ResponseEntity<IngestResponse> ingestDocument(@RequestParam("file") MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        // Sanitize filename for logging to prevent log injection
        String safeFilename = originalFilename != null ? originalFilename.replaceAll("[\r\n]", "_") : "null";
        log.info("Received ingest request for file: {}", safeFilename);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate both MIME type and extension — extension-only checks can be bypassed
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest().build();
        }
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().build();
        }

        IngestResponse response = ingestionService.ingest(file);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/documents
     *
     * List all ingested documents.
     */
    @GetMapping("/documents")
    @Operation(summary = "List all ingested documents")
    public ResponseEntity<DocumentListResponse> listDocuments() {
        var docs = ingestionService.getAllDocuments().stream()
                .map(meta -> DocumentInfo.builder()
                        .documentId(meta.documentId())
                        .filename(meta.filename())
                        .chunkCount(meta.chunkCount())
                        .ingestedAt(meta.ingestedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(DocumentListResponse.builder()
                .documents(docs)
                .total(docs.size())
                .build());
    }

    // ── QUERY ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/query
     *
     * Ask a natural language question. The RAG pipeline will:
     * 1. Embed your question
     * 2. Find the most relevant document chunks
     * 3. Pass them to GPT-4o as context
     * 4. Return a grounded answer with source citations
     *
     * Example questions to try:
     *  - "What are the key risk factors mentioned in this report?"
     *  - "What was the total revenue for Q3?"
     *  - "Summarize the compliance requirements for cross-border payments"
     */
    @PostMapping("/query")
    @Operation(summary = "Ask a question", description = "RAG-powered Q&A over ingested financial documents")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        QueryResponse response = queryService.answer(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/conversations/{conversationId}
     *
     * Retrieve conversation history for a given session.
     */
    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Get conversation history")
    public ResponseEntity<List<ConversationTurn>> getHistory(@PathVariable String conversationId) {
        return ResponseEntity.ok(queryService.getConversationHistory(conversationId));
    }
}
