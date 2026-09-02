/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.util.Map;

/**
 * Provider for optional embedding implementations.
 *
 * @since 0.1.15
 */
public interface EmbeddingProvider {
    /**
     * Returns the provider name.
     *
     * @return provider name
     * @since 0.1.15
     */
    String providerName();

    /**
     * Returns whether this provider can handle the supplied configuration.
     *
     * @param config embedding configuration
     * @param options provider options
     * @return {@code true} when this provider should handle the request
     * @since 0.1.15
     */
    default boolean supports(EmbeddingConfig config, Map<String, Object> options) {
        return true;
    }

    /**
     * Creates an embedding implementation.
     *
     * @param config embedding configuration
     * @param options creation options
     * @return embedding implementation
     * @since 0.1.15
     */
    Embedding create(EmbeddingConfig config, Map<String, Object> options);
}
