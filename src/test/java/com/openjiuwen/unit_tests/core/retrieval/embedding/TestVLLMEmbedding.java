/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.embedding;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.VLLMEmbedding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VLLM embedding model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/embedding/test_vllm_embedding.py
 *
 * Note: Python tests use extensive mocking.
 * Java tests focus on configuration and initialization validation.
 */
class TestVLLMEmbedding {

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

    @Test
    void testInitWithApiKey() {
        // Test initialization with API key
        EmbeddingConfig config = createEmbeddingConfig();
        VLLMEmbedding model = new VLLMEmbedding(config);
        assertEquals("test-model", model.getModelName());
        assertEquals("test-api-key", model.getApiKey());
    }

    @Test
    void testInitWithCustomParams() {
        // Test initialization with custom parameters
        EmbeddingConfig config = createEmbeddingConfig();
        VLLMEmbedding model = new VLLMEmbedding(config, 120, 5, null, 16, 25);
        assertEquals(120, model.getTimeout());
        assertEquals(5, model.getMaxRetries());
        assertEquals(16, model.getMaxBatchSize());
    }

    @Test
    void testClose() {
        // Test resource cleanup
        EmbeddingConfig config = createEmbeddingConfig();
        VLLMEmbedding model = new VLLMEmbedding(config);
        model.close();
        assertTrue(model.getExecutor().isShutdown());
    }

    // Note: HTTP request tests deferred to integration tests.
}