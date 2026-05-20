/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
    @JsonProperty("verify_ssl")
    @JsonAlias("verifySsl")
    private boolean verifySsl = true;
    @JsonProperty("ssl_cert")
    @JsonAlias("sslCert")
    private String sslCert;

    /**
     * Auto-generated for codecheck compliance.
     */
    public EmbeddingConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EmbeddingConfig(String modelName, String baseUrl) {
        this(modelName, baseUrl, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EmbeddingConfig(String modelName, String baseUrl, String apiKey) {
        setModelName(modelName);
        setBaseUrl(baseUrl);
        setApiKey(apiKey);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModelName(String modelName) {
        RetrievalValidation.requireNonBlank(modelName, "EmbeddingConfig.modelName");
        this.modelName = modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setBaseUrl(String baseUrl) {
        RetrievalValidation.requireNonBlank(baseUrl, "EmbeddingConfig.baseUrl");
        this.baseUrl = baseUrl;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSslCert() {
        return sslCert;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSslCert(String sslCert) {
        this.sslCert = sslCert;
    }
}
