/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Model client configuration (connection-level settings).
 * <p>
 * Mirrors Python's {@code ModelClientConfig} model.
 * Supports extra fields via {@link #extraFields}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = ModelClientConfig.Builder.class)
public class ModelClientConfig {

    private String clientId;
    private String clientProvider;
    private String apiKey;
    private String apiBase;
    private double timeout;
    private int maxRetries;
    private boolean verifySsl;
    private String sslCert;
    private Map<String, Object> extraFields;

    /** No-arg constructor for setter-based construction (used in tests). */
    public ModelClientConfig() {
        this.clientId = UUID.randomUUID().toString();
        this.timeout = 60.0;
        this.maxRetries = 3;
        this.verifySsl = true;
        this.extraFields = new HashMap<>();
    }

    private ModelClientConfig(Builder builder) {
        this.clientId = builder.clientId != null ? builder.clientId : UUID.randomUUID().toString();
        this.clientProvider = builder.clientProvider;
        this.apiKey = builder.apiKey;
        this.apiBase = builder.apiBase;
        this.timeout = builder.timeout;
        this.maxRetries = builder.maxRetries;
        this.verifySsl = builder.verifySsl;
        this.sslCert = builder.sslCert;
        this.extraFields = builder.extraFields;
    }

    // ==================== Getters ====================

    @JsonProperty("client_id")
    public String getClientId() { return clientId; }

    @JsonProperty("client_provider")
    public String getClientProvider() { return clientProvider; }

    @JsonProperty("api_key")
    public String getApiKey() { return apiKey; }

    @JsonProperty("api_base")
    public String getApiBase() { return apiBase; }

    public double getTimeout() { return timeout; }

    @JsonProperty("max_retries")
    public int getMaxRetries() { return maxRetries; }

    @JsonProperty("verify_ssl")
    public boolean isVerifySsl() { return verifySsl; }

    @JsonProperty("ssl_cert")
    public String getSslCert() { return sslCert; }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() { return extraFields; }

    // ==================== Setters ====================

    @JsonProperty("client_id")
    public void setClientId(String clientId) { this.clientId = clientId; }

    @JsonProperty("client_provider")
    public void setClientProvider(String clientProvider) { this.clientProvider = clientProvider; }

    @JsonProperty("api_key")
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    @JsonProperty("api_base")
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }

    public void setTimeout(double timeout) { this.timeout = timeout; }

    @JsonProperty("max_retries")
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    @JsonProperty("verify_ssl")
    public void setVerifySsl(boolean verifySsl) { this.verifySsl = verifySsl; }

    @JsonProperty("ssl_cert")
    public void setSslCert(String sslCert) { this.sslCert = sslCert; }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extraFields == null) { extraFields = new HashMap<>(); }
        extraFields.put(key, value);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String clientId;
        private String clientProvider;
        private String apiKey;
        private String apiBase;
        private double timeout = 60.0;
        private int maxRetries = 3;
        private boolean verifySsl = true;
        private String sslCert;
        private final Map<String, Object> extraFields = new HashMap<>();

        @JsonProperty("client_id")
        public Builder clientId(String clientId) { this.clientId = clientId; return this; }

        @JsonProperty("client_provider")
        public Builder clientProvider(String clientProvider) { this.clientProvider = clientProvider; return this; }

        @JsonProperty("api_key")
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }

        @JsonProperty("api_base")
        public Builder apiBase(String apiBase) { this.apiBase = apiBase; return this; }

        @JsonProperty("timeout")
        public Builder timeout(double timeout) { this.timeout = timeout; return this; }

        @JsonProperty("max_retries")
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        @JsonProperty("verify_ssl")
        public Builder verifySsl(boolean verifySsl) { this.verifySsl = verifySsl; return this; }

        @JsonProperty("ssl_cert")
        public Builder sslCert(String sslCert) { this.sslCert = sslCert; return this; }

        @JsonAnySetter
        public Builder extraField(String key, Object value) {
            this.extraFields.put(key, value);
            return this;
        }

        public ModelClientConfig build() {
            return new ModelClientConfig(this);
        }
    }

    @Override
    public String toString() {
        return "ModelClientConfig{clientId='" + clientId + "', clientProvider='" + clientProvider
                + "', apiBase='" + apiBase + "'}";
    }
}
