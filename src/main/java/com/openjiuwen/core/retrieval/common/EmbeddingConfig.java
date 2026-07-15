/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Embedding model configuration.
 * 
 * @since 0.1.7
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
     * EmbeddingConfig.
     * 
     * @since 0.1.7
     */
    public EmbeddingConfig() {
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
     * EmbeddingConfig.
     * 
     * @param modelName modelName
     * @param baseUrl baseUrl
     * @param apiKey apiKey
     * @since 0.1.7
     */
    public EmbeddingConfig(String modelName, String baseUrl, String apiKey) {
        setModelName(modelName);
        setBaseUrl(baseUrl);
        setApiKey(apiKey);
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
     * setModelName.
     * 
     * @param modelName modelName
     * @since 0.1.7
     */
    public void setModelName(String modelName) {
        RetrievalValidation.requireNonBlank(modelName, "EmbeddingConfig.modelName");
        this.modelName = modelName;
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
     * setBaseUrl.
     * 
     * @param baseUrl baseUrl
     * @since 0.1.7
     */
    public void setBaseUrl(String baseUrl) {
        RetrievalValidation.requireNonBlank(baseUrl, "EmbeddingConfig.baseUrl");
        this.baseUrl = baseUrl;
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

    /**
     * setApiKey.
     * 
     * @param apiKey apiKey
     * @since 0.1.7
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * isVerifySsl.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * setVerifySsl.
     * 
     * @param verifySsl verifySsl
     * @since 0.1.7
     */
    public void setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    /**
     * getSslCert.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSslCert() {
        return sslCert;
    }

    /**
     * setSslCert.
     * 
     * @param sslCert sslCert
     * @since 0.1.7
     */
    public void setSslCert(String sslCert) {
        this.sslCert = sslCert;
    }
}
