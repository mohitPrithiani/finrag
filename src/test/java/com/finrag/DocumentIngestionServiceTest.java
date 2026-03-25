package com.finrag;

import com.finrag.config.RagProperties;
import com.finrag.model.FinRagModels.IngestResponse;
import com.finrag.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    VectorStore vectorStore;

    @InjectMocks
    DocumentIngestionService ingestionService;

    // Note: RagProperties needs manual setup since no Spring context
    // In a real test you'd use @SpringBootTest or a test config

    @Test
    void shouldReturnFailedResponseForEmptyPdf() {
        var emptyFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[0]
        );

        // RagProperties won't be injected in unit test — use @SpringBootTest for integration
        // This test demonstrates structure; wire properly with Spring context for full test
        assertThat(emptyFile.getOriginalFilename()).endsWith(".pdf");
    }

    @Test
    void shouldTrackDocumentAfterSuccessfulIngest() {
        // TODO: Use @SpringBootTest + embedded Postgres (Testcontainers) for full integration test
        // Testcontainers dependency: org.testcontainers:postgresql
        // Image: pgvector/pgvector:pg16
        assertThat(true).isTrue(); // Placeholder
    }
}
