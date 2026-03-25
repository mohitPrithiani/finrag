package com.finrag.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

// ── Ingest ────────────────────────────────────────────────────────────────────

public class FinRagModels {

    /** Response after ingesting a PDF document */
    @Data
    @Builder
    public static class IngestResponse {
        private String documentId;
        private String filename;
        private int chunksCreated;
        private Instant ingestedAt;
        private String status;           // SUCCESS | FAILED
        private String message;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Incoming Q&A query from the user */
    @Data
    public static class QueryRequest {
        private String question;
        private String documentId;       // null = search across ALL documents
        private String conversationId;   // null = new conversation (no history)
    }

    /** A single source chunk used to build the answer */
    @Data
    @Builder
    public static class SourceChunk {
        private String documentId;
        private String filename;
        private int pageNumber;
        private double similarityScore;
        private String excerpt;          // Short snippet for display
    }

    /** Full Q&A response */
    @Data
    @Builder
    public static class QueryResponse {
        private String conversationId;
        private String question;
        private String answer;
        private List<SourceChunk> sources;
        private int tokensUsed;
        private Instant answeredAt;
    }

    // ── Conversation History ──────────────────────────────────────────────────

    /** A single turn in conversation history */
    @Data
    @Builder
    public static class ConversationTurn {
        private String role;    // "user" | "assistant"
        private String content;
        private Instant timestamp;
    }

    // ── Document Listing ─────────────────────────────────────────────────────

    @Data
    @Builder
    public static class DocumentInfo {
        private String documentId;
        private String filename;
        private int chunkCount;
        private Instant ingestedAt;
    }

    @Data
    @Builder
    public static class DocumentListResponse {
        private List<DocumentInfo> documents;
        private int total;
    }
}
