/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.embedding;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API embedding model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/embedding/test_api_embedding.py
 *
 * Note: Python tests use extensive mocking (unittest.mock, asyncio.to_thread).
 * Java tests focus on configuration and initialization validation.
 * HTTP request tests require integration testing environment or mock servers.
 */
class TestAPIEmbedding {

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
        APIEmbedding model = new APIEmbedding(config);
        assertEquals("test-model", model.getModelName());
        assertEquals("test-api-key", model.getApiKey());
        assertEquals("https://api.example.com/v1/embeddings", model.getApiUrl());
    }

    @Test
    void testInitWithoutApiKey() {
        // Test initialization without API key
        EmbeddingConfig config = createEmbeddingConfigNoKey();
        APIEmbedding model = new APIEmbedding(config);
        assertNull(model.getApiKey());
    }

    @Test
    void testInitWithExtraHeaders() {
        // Test initialization with extra headers
        EmbeddingConfig config = createEmbeddingConfig();
        Map<String, String> extraHeaders = Map.of("X-Custom-Header", "custom-value");
        APIEmbedding model = new APIEmbedding(config, 60, 3, extraHeaders, 8, 50);
        // Headers should contain the custom header
        assertTrue(model.getHeaders().containsKey("X-Custom-Header"));
        assertEquals("custom-value", model.getHeaders().get("X-Custom-Header"));
    }

    @Test
    void testInitWithCustomParams() {
        // Test initialization with custom parameters
        EmbeddingConfig config = createEmbeddingConfig();
        APIEmbedding model = new APIEmbedding(config, 120, 5, null, 16, 25);
        assertEquals(120, model.getTimeout());
        assertEquals(5, model.getMaxRetries());
        assertEquals(16, model.getMaxBatchSize());
        assertEquals(25, model.getMaxConcurrent());
    }

    @Test
    void testInitDefaultMaxConcurrent() {
        // Test default max_concurrent value
        EmbeddingConfig config = createEmbeddingConfig();
        APIEmbedding model = new APIEmbedding(config);
        assertEquals(50, model.getMaxConcurrent());
    }

    @Test
    void testClose() {
        // Test resource cleanup
        EmbeddingConfig config = createEmbeddingConfig();
        APIEmbedding model = new APIEmbedding(config);
        // Should be able to close the model
        model.close();
        // After close, executor should be shutdown
        assertTrue(model.getExecutor().isShutdown());
    }

    // Note: HTTP request tests (embed_query, embed_documents, retry behavior)
    // are deferred to integration tests as they require mock HTTP servers.
    // The Python tests use asyncio.to_thread mocking which doesn't translate
    // directly to Java's HttpClient. For proper testing, consider:
    // - Using WireMock or similar mock server
    // - Creating integration tests with real API endpoints
    // - Using CompletableFuture-based mock patterns
}