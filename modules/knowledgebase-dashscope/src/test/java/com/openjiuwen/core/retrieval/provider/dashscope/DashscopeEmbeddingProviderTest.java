/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.dashscope;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.provider.EmbeddingProvider;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

/** Tests DashScope embedding provider discovery. */
class DashscopeEmbeddingProviderTest {
    private static final EmbeddingConfig CONFIG =
            new EmbeddingConfig("multimodal-embedding-v1", "https://dashscope.aliyuncs.com/api/v1", "test-key");

    @Test
    void serviceLoaderDiscoversDashscopeProvider() {
        boolean hasProvider = ServiceLoader.load(EmbeddingProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "dashscope".equals(provider.providerName()));

        assertTrue(hasProvider);
    }

    @Test
    void createsDashscopeEmbeddingWithoutExposingSdkTypes() {
        DashscopeEmbeddingProvider provider = new DashscopeEmbeddingProvider();
        assertTrue(provider.supports(CONFIG, Map.of("provider", "dashscope")));
        try (Embedding embedding = provider.create(CONFIG, Map.of())) {
            assertInstanceOf(DashscopeEmbedding.class, embedding);
        }
    }
}
