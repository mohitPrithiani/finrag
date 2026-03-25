package com.finrag.service;

import com.finrag.config.RagProperties;
import com.finrag.model.FinRagModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    // In-memory document registry (replace with DB table in production)
    private final Map<String, DocumentMeta> documentRegistry = new ConcurrentHashMap<>();

    public IngestResponse ingest(MultipartFile file) {
        String documentId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();
        // Sanitize filename for logging to prevent log injection
        String safeFilename = filename != null ? filename.replaceAll("[\r\n]", "_") : "null";

        log.info("Starting ingestion for document: {} (id={})", safeFilename, documentId);

        try {
            List<PageText> pages = extractPagesFromPdf(file);
            log.debug("Extracted {} pages from {}", pages.size(), safeFilename);

            List<Document> chunks = chunkPages(pages, documentId, filename);
            log.debug("Created {} chunks from {} pages", chunks.size(), pages.size());

            // Spring AI automatically calls OpenAI embedding API here before storing
            vectorStore.add(chunks);
            log.info("Successfully stored {} chunks for document {}", chunks.size(), documentId);

            Instant now = Instant.now();
            documentRegistry.put(documentId, new DocumentMeta(documentId, filename, chunks.size(), now));

            return IngestResponse.builder()
                    .documentId(documentId)
                    .filename(filename)
                    .chunksCreated(chunks.size())
                    .ingestedAt(now)
                    .status("SUCCESS")
                    .message("Document ingested successfully. Ready for Q&A.")
                    .build();

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", safeFilename, e.getMessage(), e);
            return IngestResponse.builder()
                    .documentId(documentId)
                    .filename(filename)
                    .chunksCreated(0)
                    .ingestedAt(Instant.now())
                    .status("FAILED")
                    .message("Ingestion failed. Please ensure the file is a valid, non-corrupted PDF.")
                    .build();
        }
    }

    private List<PageText> extractPagesFromPdf(MultipartFile file) throws IOException {
        List<PageText> pages = new ArrayList<>();

        try (PDDocument pdDoc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= pdDoc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdDoc).trim();
                if (!text.isBlank()) {
                    pages.add(new PageText(page, text));
                }
            }
        }

        return pages;
    }

    private List<Document> chunkPages(List<PageText> pages, String documentId, String filename) {
        List<Document> chunks = new ArrayList<>();
        int chunkSize = ragProperties.getChunkSize();
        int overlap = ragProperties.getChunkOverlap();

        for (PageText page : pages) {
            String[] words = page.text().split("\\s+");

            int start = 0;
            while (start < words.length) {
                int end = Math.min(start + chunkSize, words.length);
                String chunkText = String.join(" ", Arrays.copyOfRange(words, start, end));

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("filename", filename);
                metadata.put("pageNumber", page.pageNumber());
                metadata.put("chunkIndex", chunks.size());

                chunks.add(new Document(chunkText, metadata));

                // advance by (chunkSize - overlap) to create overlap between chunks
                start += (chunkSize - overlap);
            }
        }

        return chunks;
    }

    public Optional<DocumentMeta> getDocumentMeta(String documentId) {
        return Optional.ofNullable(documentRegistry.get(documentId));
    }

    public Collection<DocumentMeta> getAllDocuments() {
        return documentRegistry.values();
    }

    private record PageText(int pageNumber, String text) {}

    public record DocumentMeta(String documentId, String filename, int chunkCount, Instant ingestedAt) {}
}
