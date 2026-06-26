/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Utility entry points for embedding-provider creation.
 * <p>
 * Mirrors Python's module-level functions {@code create_embedding_provider}
 * and {@code resolve_embedding_config_from_env} in
 * {@code openjiuwen/core/memory/lite/embeddings.py}.
 */
public final class EmbeddingProviders {

    private EmbeddingProviders() {
    }

    public static CompletableFuture<EmbeddingProvider> createEmbeddingProvider(
            String provider,
            String model,
            String fallback,
            EmbeddingConfig embeddingConfig
    ) {
        String providerName = provider == null ? "auto" : provider;
        String fallbackName = fallback == null ? "mock" : fallback;

        if ("mock".equals(providerName)) {
            return CompletableFuture.completedFuture(new MockEmbeddingProvider());
        }

        if (embeddingConfig == null) {
            Loggers.MEMORY.error("Embedding provider not configured.");
            return CompletableFuture.completedFuture(null);
        }

        String apiKey = embeddingConfig.getApiKey();
        String baseUrl = embeddingConfig.getBaseUrl();
        String modelName = embeddingConfig.getModelName();

        if (apiKey != null && !apiKey.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new OpenAICompatibleEmbeddingProvider(apiKey, modelName, baseUrl)
            );
        }

        if ("mock".equals(fallbackName)) {
            Loggers.MEMORY.warning("Embedding API key not found, using mock provider");
            return CompletableFuture.completedFuture(new MockEmbeddingProvider());
        }

        return CompletableFuture.failedFuture(new IllegalArgumentException(
                "Embedding API key not configured. "
                        + "Set EMBED_API_KEY environment variable or provide embedding_config parameter."
        ));
    }

    public static EmbeddingConfig resolveEmbeddingConfigFromEnv(
            String modelName,
            String fallbackBaseUrl,
            String fallbackApiKey
    ) {
        return resolveEmbeddingConfigFromMap(System.getenv(), modelName, fallbackBaseUrl, fallbackApiKey);
    }

    static EmbeddingConfig resolveEmbeddingConfigFromMap(
            Map<String, String> environment,
            String modelName,
            String fallbackBaseUrl,
            String fallbackApiKey
    ) {
        String resolvedModelName = environment.getOrDefault("EMBEDDING_MODEL_NAME", modelName);
        String baseUrl = environment.getOrDefault("EMBEDDING_BASE_URL", fallbackBaseUrl);
        String apiKey = environment.getOrDefault("EMBEDDING_API_KEY", fallbackApiKey);
        if (resolvedModelName != null && baseUrl != null && apiKey != null) {
            return EmbeddingConfig.builder()
                    .modelName(resolvedModelName)
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();
        }
        return null;
    }
}
