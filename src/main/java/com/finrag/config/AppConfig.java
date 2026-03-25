package com.finrag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Swagger / OpenAPI metadata.
     * Access UI at: http://localhost:8080/swagger-ui.html
     */
    @Bean
    public OpenAPI finRagOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinRAG - Financial Document Q&A API")
                        .description("""
                                RAG-based Q&A assistant for financial documents.
                                Upload PDFs and ask natural language questions against them.
                                Built with Spring AI + pgvector + OpenAI GPT-4o.
                                """)
                        .version("1.0.0"));
    }
}
