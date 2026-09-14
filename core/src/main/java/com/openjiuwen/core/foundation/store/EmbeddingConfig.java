/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedding model configuration.
 * <p>
 * Mirrors Python's {@code EmbeddingConfig} in
 * {@code openjiuwen/core/foundation/store/base_embedding.py}.
 */
@Data
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingConfig {

    /** Model name. */
    private String modelName;

    /** API base URL. */
    private String baseUrl;

    /** Optional API key. */
    private String apiKey;

    public EmbeddingConfig(String modelName, String baseUrl, String apiKey) {
        setModelName(modelName);
        setBaseUrl(baseUrl);
        this.apiKey = apiKey;
    }

    public void setModelName(String modelName) {
        if (modelName == null) {
            throw new IllegalArgumentException("model_name is required");
        }
        this.modelName = modelName;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new IllegalArgumentException("base_url is required");
        }
        this.baseUrl = baseUrl;
    }
}
