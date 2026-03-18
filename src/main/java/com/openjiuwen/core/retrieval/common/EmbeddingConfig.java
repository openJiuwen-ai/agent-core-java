/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Embedding model configuration.
 */
public class EmbeddingConfig {

    @JsonProperty("model_name")
    @JsonAlias("modelName")
    private String modelName;
    @JsonProperty("base_url")
    @JsonAlias("baseUrl")
    private String baseUrl;
    @JsonProperty("api_key")
    @JsonAlias("apiKey")
    private String apiKey;

    public EmbeddingConfig() {
    }

    public EmbeddingConfig(String modelName, String baseUrl) {
        this(modelName, baseUrl, null);
    }

    public EmbeddingConfig(String modelName, String baseUrl, String apiKey) {
        setModelName(modelName);
        setBaseUrl(baseUrl);
        setApiKey(apiKey);
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        RetrievalValidation.requireNonBlank(modelName, "EmbeddingConfig.modelName");
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        RetrievalValidation.requireNonBlank(baseUrl, "EmbeddingConfig.baseUrl");
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
