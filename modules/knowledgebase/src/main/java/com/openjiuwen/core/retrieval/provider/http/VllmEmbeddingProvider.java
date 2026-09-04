/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.http;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.embedding.VLLMEmbedding;
import com.openjiuwen.core.retrieval.provider.EmbeddingProvider;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Provider for vLLM-compatible embedding endpoints.
 *
 * @since 0.1.15
 */
public final class VllmEmbeddingProvider implements EmbeddingProvider {
    @Override
    public String providerName() {
        return "vllm";
    }

    @Override
    public boolean supports(EmbeddingConfig config, Map<String, Object> options) {
        Optional<String> provider = providerOption(options);
        if (provider.isPresent()) {
            return "vllm".equals(provider.get());
        }
        String baseUrl = config == null ? null : config.getBaseUrl();
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("vllm");
    }

    @Override
    public Embedding create(EmbeddingConfig config, Map<String, Object> options) {
        return new VLLMEmbedding(config);
    }

    private static Optional<String> providerOption(Map<String, Object> options) {
        if (options == null) {
            return Optional.empty();
        }
        Object value = options.get("provider");
        if (value == null) {
            value = options.get("provider_name");
        }
        return value == null ? Optional.empty() : Optional.of(String.valueOf(value).toLowerCase(Locale.ROOT));
    }
}
