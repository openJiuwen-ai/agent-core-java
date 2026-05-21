/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.embedding;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI embedding model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/embedding/test_openai_embedding.py
 *
 * Note: Python tests use extensive mocking (httpx, openai, asyncio).
 * Java tests focus on configuration and initialization validation.
 * HTTP request tests require integration testing environment.
 */
class TestOpenAIEmbedding {

    private EmbeddingConfig createEmbeddingConfig() {
        return new EmbeddingConfig(
                "test-model",
                "test-api-key",
                "https://api.example.com/v1/embeddings",
                null,
                null,
                null
        );
    }

    private EmbeddingConfig createEmbeddingConfigNoKey() {
        return new EmbeddingConfig(
                "test-model",
                null,
                "https://api.example.com/v1/embeddings",
                null,
                null,
                null
        );
    }

    @Test
    void testInitWithApiKey() {
        // Test initialization with API key
        EmbeddingConfig config = createEmbeddingConfig();
        OpenAIEmbedding model = new OpenAIEmbedding(config);
        assertEquals("test-model", model.getModelName());
        assertEquals("test-api-key", model.getApiKey());
        // URL is normalized (embeddings suffix removed)
        assertEquals("https://api.example.com/v1", model.getApiUrl());
    }

    @Test
    void testInitWithoutApiKey() {
        // Test initialization without API key
        EmbeddingConfig config = createEmbeddingConfigNoKey();
        OpenAIEmbedding model = new OpenAIEmbedding(config);
        assertNull(model.getApiKey());
    }

    @Test
    void testInitWithExtraHeaders() {
        // Test initialization with extra headers
        EmbeddingConfig config = createEmbeddingConfig();
        Map<String, String> extraHeaders = Map.of("X-Custom-Header", "custom-value");
        OpenAIEmbedding model = new OpenAIEmbedding(config, 60, 3, extraHeaders, 8, 50, null, null);
        assertTrue(model.getHeaders().containsKey("X-Custom-Header"));
    }

    @Test
    void testInitWithCustomParams() {
        // Test initialization with custom parameters
        EmbeddingConfig config = createEmbeddingConfig();
        OpenAIEmbedding model = new OpenAIEmbedding(config, 120, 5, null, 16, 25, null, null);
        assertEquals(120, model.getTimeout());
        assertEquals(5, model.getMaxRetries());
        assertEquals(16, model.getMaxBatchSize());
        assertEquals(25, model.getMaxConcurrent());
    }

    @Test
    void testInitWithDimension() {
        // Test initialization with dimension (Matryoshka)
        EmbeddingConfig config = createEmbeddingConfig();
        OpenAIEmbedding model = new OpenAIEmbedding(config, 60, 3, null, 8, 50, 256, null);
        assertEquals(256, model.getDimension());
    }

    @Test
    void testClose() {
        // Test resource cleanup
        EmbeddingConfig config = createEmbeddingConfig();
        OpenAIEmbedding model = new OpenAIEmbedding(config);
        model.close();
        assertTrue(model.getExecutor().isShutdown());
    }

    // Note: HTTP request tests (embed_query, embed_documents)
    // are deferred to integration tests.
}