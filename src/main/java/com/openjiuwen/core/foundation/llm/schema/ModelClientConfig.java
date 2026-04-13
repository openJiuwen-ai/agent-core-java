/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    private final String clientId;
    private final String clientProvider;
    private final String apiKey;
    private final String apiBase;
    private final double timeout;
    private final ModelHttpVersion httpVersion;
    private final int maxRetries;
    private final boolean verifySsl;
    private final String sslCert;
    private final Map<String, String> headers;
    private final Map<String, Object> extraFields;

    private ModelClientConfig(Builder builder) {
        this.clientId = builder.clientId != null ? builder.clientId : UUID.randomUUID().toString();
        this.clientProvider = Objects.requireNonNull(builder.clientProvider, "clientProvider must not be null");
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey must not be null");
        this.apiBase = Objects.requireNonNull(builder.apiBase, "apiBase must not be null");
        
        // Validate timeout - must be > 0 (matches Python Pydantic Field(gt=0))
        if (builder.timeout <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + builder.timeout + ", input_type=" + 
                    (builder.timeout == (int)builder.timeout ? "int" : "float") + "]");
        }
        this.timeout = builder.timeout;
        this.httpVersion = builder.httpVersion;
        
        this.maxRetries = builder.maxRetries;
        this.verifySsl = builder.verifySsl;
        this.sslCert = builder.sslCert;
        this.headers = new LinkedHashMap<>(builder.headers);
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

    @JsonProperty("http_version")
    public ModelHttpVersion getHttpVersion() { return httpVersion; }

    @JsonProperty("max_retries")
    public int getMaxRetries() { return maxRetries; }

    @JsonProperty("verify_ssl")
    public boolean isVerifySsl() { return verifySsl; }

    @JsonProperty("ssl_cert")
    public String getSslCert() { return sslCert; }

    @JsonProperty("headers")
    public Map<String, String> getHeaders() { return new LinkedHashMap<>(headers); }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() { return extraFields; }

    // ==================== Builder ====================

    /**
     * Creates a new builder for ModelClientConfig.
     *
     * @return a new Builder instance
     */
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
        private ModelHttpVersion httpVersion;
        private int maxRetries = 3;
        private boolean verifySsl = true;
        private String sslCert;
        private final Map<String, String> headers = new LinkedHashMap<>();
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

        @JsonProperty("http_version")
        public Builder httpVersion(ModelHttpVersion httpVersion) { this.httpVersion = httpVersion; return this; }

        @JsonProperty("max_retries")
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        @JsonProperty("verify_ssl")
        public Builder verifySsl(boolean verifySsl) { this.verifySsl = verifySsl; return this; }

        @JsonProperty("ssl_cert")
        public Builder sslCert(String sslCert) { this.sslCert = sslCert; return this; }

        @JsonProperty("headers")
        public Builder headers(Map<String, ?> headers) {
            this.headers.clear();
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (key != null && value != null) {
                        this.headers.put(key, String.valueOf(value));
                    }
                });
            }
            return this;
        }

        public Builder header(String key, String value) {
            if (key != null && value != null) {
                this.headers.put(key, value);
            }
            return this;
        }

        @JsonAnySetter
        public Builder extraField(String key, Object value) {
            this.extraFields.put(key, value);
            return this;
        }

        /**
         * Builds the ModelClientConfig instance.
         *
         * @return a new ModelClientConfig instance
         */
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
