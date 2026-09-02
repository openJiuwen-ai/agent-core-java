/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.dashscope;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.provider.EmbeddingProvider;

import java.util.Locale;
import java.util.Map;

/**
 * ServiceLoader provider for DashScope embeddings.
 *
 * @since 0.1.15
 */
public final class DashscopeEmbeddingProvider implements EmbeddingProvider {
    /**
     * Returns the provider name.
     *
     * @return DashScope provider name
     * @since 0.1.15
     */
    @Override
    public String providerName() {
        return "dashscope";
    }

    /**
     * Checks whether configuration selects DashScope.
     *
     * @param config embedding configuration
     * @param options provider options
     * @return whether DashScope is selected
     * @since 0.1.15
     */
    @Override
    public boolean supports(EmbeddingConfig config, Map<String, Object> options) {
        Object provider = options == null ? null : options.get("provider");
        if (provider != null) {
            return "dashscope".equals(String.valueOf(provider).toLowerCase(Locale.ROOT));
        }
        return config != null && config.getBaseUrl() != null
                && config.getBaseUrl().toLowerCase(Locale.ROOT).contains("dashscope");
    }

    /**
     * Creates a DashScope embedding.
     *
     * @param config embedding configuration
     * @param options provider options
     * @return DashScope embedding
     * @since 0.1.15
     */
    @Override
    public Embedding create(EmbeddingConfig config, Map<String, Object> options) {
        return new DashscopeEmbedding(config);
    }
}
