package com.finrag.service;

import com.finrag.config.RagProperties;
import com.finrag.model.FinRagModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the QUERY side of the RAG pipeline:
 *
 *  user question  →  embed question  →  vector search  →  build prompt  →  LLM  →  answer
 *
 * Key concepts you'll learn here:
 *  - Semantic search: finding chunks similar in MEANING, not just keyword match
 *  - Prompt stuffing: injecting retrieved chunks into the LLM prompt as context
 *  - Conversation history: passing prior turns for multi-turn Q&A
 *  - Grounding: instructing the LLM to only answer from provided context
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagProperties ragProperties;

    // In-memory conversation store (replace with Redis or DB in production)
    private final Map<String, List<ConversationTurn>> conversations = new HashMap<>();

    /**
     * Main entry point: answer a question using RAG.
     */
    public QueryResponse answer(QueryRequest request) {
        String conversationId = request.getConversationId() != null
                ? request.getConversationId()
                : UUID.randomUUID().toString();

        log.info("Processing query [convId={}]: {}", conversationId, request.getQuestion());

        // Step 1: Retrieve semantically similar chunks from vector store
        List<Document> relevantChunks = retrieveRelevantChunks(request);
        log.debug("Retrieved {} relevant chunks", relevantChunks.size());

        if (relevantChunks.isEmpty()) {
            return buildNoContextResponse(conversationId, request.getQuestion());
        }

        // Step 2: Build the grounded system prompt with retrieved context
        String systemPrompt = buildSystemPrompt(relevantChunks);

        // Step 3: Build conversation history for multi-turn support
        List<Message> history = buildConversationHistory(conversationId);

        // Step 4: Call the LLM via Spring AI ChatClient
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .user(request.getQuestion())
                .call()
                .content();

        // Step 5: Persist this turn to conversation history
        storeConversationTurn(conversationId, request.getQuestion(), answer);

        // Step 6: Build response with source citations
        List<SourceChunk> sources = buildSourceChunks(relevantChunks);

        return QueryResponse.builder()
                .conversationId(conversationId)
                .question(request.getQuestion())
                .answer(answer)
                .sources(sources)
                .answeredAt(Instant.now())
                .build();
    }

    /**
     * Vector similarity search.
     *
     * Spring AI embeds the question using the same model (text-embedding-3-small)
     * and finds the top-K most similar document chunks in pgvector.
     * Similarity is measured by COSINE distance between embedding vectors.
     */
    private List<Document> retrieveRelevantChunks(QueryRequest request) {
        SearchRequest searchRequest = SearchRequest.query(request.getQuestion())
                .withTopK(ragProperties.getTopK())
                .withSimilarityThreshold(ragProperties.getSimilarityThreshold());

        // Optionally filter by document ID (search within one document)
        if (request.getDocumentId() != null) {
            // Validate UUID format to prevent filter expression injection
            if (!request.getDocumentId().matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                throw new IllegalArgumentException("Invalid documentId format");
            }
            searchRequest = searchRequest.withFilterExpression("documentId == '" + request.getDocumentId() + "'");
        }

        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * Build the system prompt with retrieved context injected.
     *
     * This is the core of RAG — we "stuff" the context into the prompt
     * and instruct the model to ONLY answer from that context.
     * This prevents hallucination and keeps answers grounded in your documents.
     */
    private String buildSystemPrompt(List<Document> chunks) {
        String context = chunks.stream()
                .map(doc -> String.format("[Source: %s, Page %s]\n%s",
                        doc.getMetadata().get("filename"),
                        doc.getMetadata().get("pageNumber"),
                        doc.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return """
                You are FinRAG, an expert financial document analyst assistant.
                Your role is to answer questions accurately based ONLY on the provided document context.
                
                RULES:
                1. Answer ONLY from the context provided below. Do not use outside knowledge.
                2. If the answer is not in the context, say: "I couldn't find information about this in the provided documents."
                3. Always cite which document and page number your answer comes from.
                4. Be concise but thorough. Use bullet points for lists.
                5. For financial figures, be precise and include units/currency.
                6. If asked about risks or compliance, be extra careful to present information accurately.
                
                DOCUMENT CONTEXT:
                %s
                """.formatted(context);
    }

    /**
     * Reconstruct conversation history as Spring AI Message objects.
     * This enables multi-turn Q&A where the LLM remembers prior exchanges.
     */
    private List<Message> buildConversationHistory(String conversationId) {
        List<ConversationTurn> history = conversations.getOrDefault(conversationId, List.of());

        return history.stream()
                .map(turn -> (Message) switch (turn.getRole()) {
                    case "user" -> new UserMessage(turn.getContent());
                    case "assistant" -> new AssistantMessage(turn.getContent());
                    default -> new UserMessage(turn.getContent());
                })
                .collect(Collectors.toList());
    }

    private void storeConversationTurn(String conversationId, String question, String answer) {
        List<ConversationTurn> history = conversations.computeIfAbsent(conversationId, k -> new ArrayList<>());

        history.add(ConversationTurn.builder()
                .role("user").content(question).timestamp(Instant.now()).build());
        history.add(ConversationTurn.builder()
                .role("assistant").content(answer).timestamp(Instant.now()).build());

        // Keep last 10 turns to avoid token limit overflow
        if (history.size() > 20) {
            history.subList(0, history.size() - 20).clear();
        }
    }

    private List<SourceChunk> buildSourceChunks(List<Document> chunks) {
        return chunks.stream()
                .map(doc -> {
                    String text = doc.getContent();
                    String excerpt = text.length() > 200 ? text.substring(0, 200) + "..." : text;

                    return SourceChunk.builder()
                            .documentId((String) doc.getMetadata().get("documentId"))
                            .filename((String) doc.getMetadata().get("filename"))
                            .pageNumber(doc.getMetadata().get("pageNumber") instanceof Integer p ? p : 0)
                            .excerpt(excerpt)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private QueryResponse buildNoContextResponse(String conversationId, String question) {
        return QueryResponse.builder()
                .conversationId(conversationId)
                .question(question)
                .answer("No relevant information found in the ingested documents for your question. " +
                        "Please ensure you've uploaded the relevant document, or try rephrasing your question.")
                .sources(List.of())
                .answeredAt(Instant.now())
                .build();
    }

    public List<ConversationTurn> getConversationHistory(String conversationId) {
        return conversations.getOrDefault(conversationId, List.of());
    }
}
