/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's provider logic in
 * {@code openjiuwen/core/memory/lite/embeddings.py}.
 */
class EmbeddingsTest {

    @Test
    void mockProviderIsDeterministicPerText() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        List<Float> first = provider.embedQuery("hello").join();
        List<Float> second = provider.embedQuery("hello").join();
        List<Float> third = provider.embedQuery("world").join();

        assertEquals(128, first.size());
        assertEquals(first, second);
        assertNotEquals(first, third);
        assertTrue(first.stream().allMatch(value -> value >= -1.0f && value <= 1.0f));
    }

    @Test
    void mockProviderEmbedsDocumentsByDelegatingPerText() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        List<Float> queryEmbedding = provider.embedQuery("alpha").join();
        List<List<Float>> documentEmbeddings = provider.embedDocuments(List.of("alpha", "beta")).join();

        assertEquals(queryEmbedding, documentEmbeddings.get(0));
        assertEquals(2, documentEmbeddings.size());
    }

    @Test
    void openAiProviderNormalizesBaseUrlSuffix() throws Exception {
        OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(
                "secret",
                "model-x",
                "https://example.test/v1/embeddings"
        );

        Field field = OpenAICompatibleEmbeddingProvider.class.getDeclaredField("baseUrl");
        field.setAccessible(true);

        assertEquals("https://example.test/v1", field.get(provider));
        assertEquals(1024, provider.getDims());
    }

    @Test
    void openAiProviderRejectsMissingApiKey() {
        OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(
                null,
                "model-x",
                "https://example.test/v1"
        );

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> provider.embedDocuments(List.of("hello")).join()
        );
        assertTrue(exception.getCause().getMessage().contains("Embedding API key not configured"));
    }

    @Test
    void openAiProviderSortsByIndexAndUpdatesDims() {
        OpenAICompatibleEmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(
                "secret",
                "model-x",
                "https://example.test/v1"
        ) {
            @Override
            protected java.util.concurrent.CompletableFuture<EmbeddingHttpResponse> sendEmbeddingsRequest(String requestBody) {
                String body = "{\"data\":["
                        + "{\"index\":1,\"embedding\":[3.0,4.0]},"
                        + "{\"index\":0,\"embedding\":[1.0,2.0]}]}";
                return java.util.concurrent.CompletableFuture.completedFuture(new EmbeddingHttpResponse(200, body));
            }
        };

        List<List<Float>> embeddings = provider.embedDocuments(List.of("a", "b")).join();

        assertEquals(List.of(1.0f, 2.0f), embeddings.get(0));
        assertEquals(List.of(3.0f, 4.0f), embeddings.get(1));
        assertEquals(2, provider.getDims());
    }

    @Test
    void createEmbeddingProviderReturnsMockForExplicitMockProvider() {
        EmbeddingProvider provider = EmbeddingProviders.createEmbeddingProvider("mock", null, "mock", null).join();
        assertInstanceOf(MockEmbeddingProvider.class, provider);
    }

    @Test
    void createEmbeddingProviderReturnsNullWhenConfigMissing() {
        EmbeddingProvider provider = EmbeddingProviders.createEmbeddingProvider("auto", null, "mock", null).join();
        assertNull(provider);
    }

    @Test
    void createEmbeddingProviderReturnsOpenAiProviderWhenApiKeyPresent() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("embed-1")
                .baseUrl("https://example.test/v1")
                .apiKey("secret")
                .build();

        EmbeddingProvider provider = EmbeddingProviders.createEmbeddingProvider("auto", null, "mock", config).join();

        assertInstanceOf(OpenAICompatibleEmbeddingProvider.class, provider);
    }

    @Test
    void createEmbeddingProviderFallsBackToMockWhenApiKeyMissing() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("embed-1")
                .baseUrl("https://example.test/v1")
                .apiKey(null)
                .build();

        EmbeddingProvider provider = EmbeddingProviders.createEmbeddingProvider("auto", null, "mock", config).join();

        assertInstanceOf(MockEmbeddingProvider.class, provider);
    }

    @Test
    void createEmbeddingProviderFailsWithoutFallbackWhenApiKeyMissing() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("embed-1")
                .baseUrl("https://example.test/v1")
                .apiKey(null)
                .build();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> EmbeddingProviders.createEmbeddingProvider("auto", null, "none", config).join()
        );
        assertTrue(exception.getCause().getMessage().contains("Embedding API key not configured"));
    }

    @Test
    void resolveEmbeddingConfigFromMapUsesEnvironmentAndFallbacks() {
        EmbeddingConfig config = EmbeddingProviders.resolveEmbeddingConfigFromMap(
                Map.of("EMBEDDING_MODEL_NAME", "env-model", "EMBEDDING_API_KEY", "env-key"),
                "fallback-model",
                "https://fallback.test",
                "fallback-key"
        );

        assertNotNull(config);
        assertEquals("env-model", config.getModelName());
        assertEquals("https://fallback.test", config.getBaseUrl());
        assertEquals("env-key", config.getApiKey());
    }
}
