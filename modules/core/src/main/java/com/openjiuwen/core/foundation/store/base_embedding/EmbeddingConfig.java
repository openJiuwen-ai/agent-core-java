/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_embedding;

/**
 * Embedding model configuration.
 * <p>
 * Mirrors Python's {@code EmbeddingConfig} Pydantic model.
 * 
 * @since 0.1.7
 */
public class EmbeddingConfig {
    private final String modelName;
    private final String baseUrl;
    private final String apiKey;

    /**
     * EmbeddingConfig.
     * 
     * @param modelName modelName
     * @param baseUrl baseUrl
     * @param apiKey apiKey
     * @since 0.1.7
     */
    public EmbeddingConfig(String modelName, String baseUrl, String apiKey) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be null or blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /**
     * EmbeddingConfig.
     * 
     * @param modelName modelName
     * @param baseUrl baseUrl
     * @since 0.1.7
     */
    public EmbeddingConfig(String modelName, String baseUrl) {
        this(modelName, baseUrl, null);
    }

    /**
     * getModelName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * getBaseUrl.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * getApiKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getApiKey() {
        return apiKey;
    }

}
