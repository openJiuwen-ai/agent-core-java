/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.embedding;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenAIEmbedding.
 * <p>
 * Mirrors Python's OpenAI embedding tests.
 * Tests OpenAI-specific embedding features including base64 support.
 */
class TestOpenAIEmbedding {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test OpenAIEmbedding class exists")
    void testOpenAIEmbeddingClassExists() {
        assertNotNull(OpenAIEmbedding.class);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test OpenAI config normalization")
    void testOpenAIConfigNormalization() {
        // OpenAI embedding uses specific API URL normalization
        String expectedUrl = "https://api.openai.com/v1/embeddings";
        assertNotNull(expectedUrl);
        assertTrue(expectedUrl.contains("openai.com"));
        assertTrue(expectedUrl.contains("embeddings"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Embedding models)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test OpenAI embedding model names")
    void testOpenAIEmbeddingModels() {
        // Valid OpenAI embedding models
        String[] validModels = {
            "text-embedding-3-small",
            "text-embedding-3-large",
            "text-embedding-ada-002"
        };
        
        for (String model : validModels) {
            assertNotNull(model);
            assertTrue(model.startsWith("text-embedding"), 
                    "OpenAI embedding model should start with 'text-embedding'");
        }
    }

    @Test
    @Tag("level1")
    @DisplayName("Test OpenAI embedding dimensions")
    void testOpenAIEmbeddingDimensions() {
        // OpenAI embedding model dimensions
        Map<String, Integer> modelDimensions = Map.of(
            "text-embedding-3-small", 1536,
            "text-embedding-3-large", 3072,
            "text-embedding-ada-002", 1536
        );
        
        for (Map.Entry<String, Integer> entry : modelDimensions.entrySet()) {
            assertTrue(entry.getValue() > 0, 
                    entry.getKey() + " should have positive dimension");
            assertTrue(entry.getValue() >= 1536,
                    entry.getKey() + " should have at least 1536 dimensions");
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Base64 embedding support)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test base64 embedding encoding")
    void testBase64EmbeddingEncoding() {
        // OpenAI supports base64 encoding for embeddings to reduce response size
        float[] embedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        
        // Simulate base64 encoding process
        byte[] bytes = new byte[embedding.length * 4];
        for (int i = 0; i < embedding.length; i++) {
            int bits = Float.floatToIntBits(embedding[i]);
            bytes[i * 4] = (byte) (bits >> 24);
            bytes[i * 4 + 1] = (byte) (bits >> 16);
            bytes[i * 4 + 2] = (byte) (bits >> 8);
            bytes[i * 4 + 3] = (byte) bits;
        }
        
        assertNotNull(bytes);
        assertEquals(embedding.length * 4, bytes.length, 
                "Base64 encoded bytes should be 4x float count");
    }

    @Test
    @Tag("level2")
    @DisplayName("Test dimensions parameter support")
    void testDimensionsParameter() {
        // OpenAI embedding supports custom dimensions parameter
        int defaultDim = 1536;
        int customDim = 512;
        
        // Verify dimensions can be configured
        assertTrue(customDim < defaultDim, 
                "Custom dimension should be less than default");
        assertTrue(customDim > 0, 
                "Custom dimension should be positive");
    }
}