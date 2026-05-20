package com.sashkolearn.analyzeagent.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generates embedding vector for text
     * Uses OpenAI text-embedding-3-small (1536 dimensions)
     *
     * @param text text to vectorize
     * @return float[] array with 1536 elements
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or blank");
        }

        try {
            log.debug("Generating embedding for text (length: {})", text.length());

            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));

            // Get embedding as float[] directly
            float[] result = response.getResults().getFirst().getOutput();

            log.debug("Generated embedding with {} dimensions", result.length);
            return result;

        } catch (Exception e) {
            log.error("Failed to generate embedding for text (length: {})", text.length(), e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    public List<float[]> generateEmbeddingsBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Texts list cannot be null or empty");
        }

        try {
            log.info("Generating embeddings for batch of {} texts", texts.size());

            // Batch request to OpenAI API
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);

            return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();

        } catch (Exception e) {
            log.error("Failed to generate batch embeddings for {} texts", texts.size(), e);
            throw new RuntimeException("Failed to generate batch embeddings: " + e.getMessage(), e);
        }
    }
}
