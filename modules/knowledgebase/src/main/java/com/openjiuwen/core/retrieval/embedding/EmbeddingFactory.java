/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.provider.EmbeddingProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Factory for embedding implementations discovered from the class path.
 *
 * @since 0.1.15
 */
public final class EmbeddingFactory {
    private static final List<EmbeddingProvider> PROVIDERS =
            ServiceLoader.load(EmbeddingProvider.class).stream().map(ServiceLoader.Provider::get).toList();

    private EmbeddingFactory() {
    }

    /**
     * Creates an embedding using the configured provider.
     *
     * @param config embedding configuration
     * @return embedding implementation
     * @since 0.1.15
     */
    public static Embedding create(EmbeddingConfig config) {
        return create(config, Map.of());
    }

    /**
     * Creates an embedding using the configured provider and options.
     *
     * @param config embedding configuration
     * @param options provider options
     * @return embedding implementation
     * @since 0.1.15
     */
    public static Embedding create(EmbeddingConfig config, Map<String, Object> options) {
        if (config == null) {
            throw RetrievalExceptions.validation("EmbeddingConfig is required");
        }
        Map<String, Object> resolvedOptions = options == null ? Map.of() : options;
        Optional<String> requested = providerOption(resolvedOptions);
        for (EmbeddingProvider provider : PROVIDERS) {
            if ((requested.isEmpty() || requested.get().equalsIgnoreCase(provider.providerName()))
                    && provider.supports(config, resolvedOptions)) {
                return provider.create(config, resolvedOptions);
            }
        }
        String providerName = requested.orElse("default");
        throw RetrievalExceptions.error(StatusCode.RETRIEVAL_EMBEDDING_MODEL_NOT_FOUND,
                "No embedding provider registered for: " + providerName);
    }

    private static Optional<String> providerOption(Map<String, Object> options) {
        Object provider = options.get("provider");
        if (provider == null) {
            provider = options.get("provider_name");
        }
        if (provider == null) {
            return Optional.empty();
        }
        String value = String.valueOf(provider).trim().toLowerCase(Locale.ROOT);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
