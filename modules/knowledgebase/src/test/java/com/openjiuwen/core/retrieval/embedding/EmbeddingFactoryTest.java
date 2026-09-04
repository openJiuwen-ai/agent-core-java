/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

/** Tests embedding provider selection. */
class EmbeddingFactoryTest {
    private static final EmbeddingConfig CONFIG =
            new EmbeddingConfig("test-model", "https://api.example.com/v1", "test-key");

    @Test
    void createsBuiltInHttpProviders() {
        try (Embedding openAi = EmbeddingFactory.create(CONFIG, Map.of("provider", "openai"));
                Embedding vllm = EmbeddingFactory.create(CONFIG, Map.of("provider", "vllm"))) {
            assertInstanceOf(OpenAIEmbedding.class, openAi);
            assertInstanceOf(VLLMEmbedding.class, vllm);
        }
    }

    @Test
    void rejectsUnknownProviderWithoutFallback() {
        assertThrows(BaseError.class,
                () -> EmbeddingFactory.create(CONFIG, Map.of("provider", "unregistered-provider")));
    }
}
