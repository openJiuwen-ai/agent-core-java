/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.Objects;

/**
 * Embedding model configuration.
 * <p>
 * Placeholder implementation for memory module dependency.
 * Will be completed when retrieval module is converted.
 */
public class EmbeddingConfig {

    private final String modelName;
    private final String baseUrl;
    private final String apiKey;

    private EmbeddingConfig(Builder builder) {
        this.modelName = Objects.requireNonNull(builder.modelName, "modelName is required");
        this.baseUrl = Objects.requireNonNull(builder.baseUrl, "baseUrl is required");
        this.apiKey = builder.apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String modelName;
        private String baseUrl;
        private String apiKey;

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public EmbeddingConfig build() {
            return new EmbeddingConfig(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingConfig that = (EmbeddingConfig) o;
        return Objects.equals(modelName, that.modelName) &&
               Objects.equals(baseUrl, that.baseUrl) &&
               Objects.equals(apiKey, that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, baseUrl, apiKey);
    }

    @Override
    public String toString() {
        return "EmbeddingConfig{" +
               "modelName='" + modelName + '\'' +
               ", baseUrl='" + baseUrl + '\'' +
               ", apiKey='***'" +
               '}';
    }
}

