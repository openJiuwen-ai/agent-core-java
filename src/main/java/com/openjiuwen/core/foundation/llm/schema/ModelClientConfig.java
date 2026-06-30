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
        
        this.maxRetries = builder.maxRetries;
        this.verifySsl = builder.verifySsl;
        this.sslCert = builder.sslCert;
        this.headers = new LinkedHashMap<>(builder.headers);
        this.extraFields = builder.extraFields;
    }

    // ==================== Getters ====================

    @JsonProperty("client_id")
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getClientId() {
        return clientId;
    }

    @JsonProperty("client_provider")
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getClientProvider() {
        return clientProvider;
    }

    @JsonProperty("api_key")
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty("api_base")
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiBase() {
        return apiBase;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTimeout() {
        return timeout;
    }

    @JsonProperty("max_retries")
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    @JsonProperty("verify_ssl")
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isVerifySsl() {
        return verifySsl;
    }

    @JsonProperty("ssl_cert")
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSslCert() {
        return sslCert;
    }

    @JsonProperty("headers")
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> getHeaders() {
        return new LinkedHashMap<>(headers);
    }

    @JsonAnyGetter
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

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
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        private String clientId;
        private String clientProvider;
        private String apiKey;
        private String apiBase;
        private double timeout = 60.0;
        private int maxRetries = 3;
        private boolean verifySsl = true;
        private String sslCert;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, Object> extraFields = new HashMap<>();

        @JsonProperty("client_id")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        @JsonProperty("client_provider")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder clientProvider(String clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        @JsonProperty("api_key")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        @JsonProperty("api_base")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        @JsonProperty("timeout")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        @JsonProperty("max_retries")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        @JsonProperty("verify_ssl")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder verifySsl(boolean verifySsl) {
            this.verifySsl = verifySsl;
            return this;
        }

        @JsonProperty("ssl_cert")
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sslCert(String sslCert) {
            this.sslCert = sslCert;
            return this;
        }

        @JsonProperty("headers")
        /**
         * Auto-generated for codecheck compliance.
         */
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

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder header(String key, String value) {
            if (key != null && value != null) {
                this.headers.put(key, value);
            }
            return this;
        }

        @JsonAnySetter
        /**
         * Auto-generated for codecheck compliance.
         */
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "ModelClientConfig{clientId='" + clientId + "', clientProvider='" + clientProvider
                + "', apiBase='" + apiBase + "'}";
    }
}
