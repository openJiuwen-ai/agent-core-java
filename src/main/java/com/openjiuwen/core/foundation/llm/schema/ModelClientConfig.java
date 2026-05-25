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
import lombok.Data;

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
 *
 * @since 0.1.7
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
                            (builder.timeout == (int) builder.timeout ? "int" : "float") + "]");
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

    /**
     * Returns the client identifier.
     *
     * @return the client identifier
     */
    @JsonProperty("client_id")
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the client provider.
     *
     * @return the client provider
     */
    @JsonProperty("client_provider")
    public String getClientProvider() {
        return clientProvider;
    }

    /**
     * Returns the API key.
     *
     * @return the API key
     */
    @JsonProperty("api_key")
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the API base URL.
     *
     * @return the API base URL
     */
    @JsonProperty("api_base")
    public String getApiBase() {
        return apiBase;
    }

    /**
     * Returns the request timeout in seconds.
     *
     * @return the request timeout
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * Returns the configured HTTP version.
     *
     * @return the HTTP version, or {@code null} if not set
     */
    @JsonProperty("http_version")
    public ModelHttpVersion getHttpVersion() {
        return httpVersion;
    }

    /**
     * Returns the maximum retry count.
     *
     * @return the maximum retry count
     */
    @JsonProperty("max_retries")
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns whether SSL certificates should be verified.
     *
     * @return {@code true} if SSL verification is enabled
     */
    @JsonProperty("verify_ssl")
    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * Returns the SSL certificate path or content.
     *
     * @return the SSL certificate value, or {@code null} if not set
     */
    @JsonProperty("ssl_cert")
    public String getSslCert() {
        return sslCert;
    }

    /**
     * Returns a copy of the configured headers.
     *
     * @return the configured headers
     */
    @JsonProperty("headers")
    public Map<String, String> getHeaders() {
        return new LinkedHashMap<>(headers);
    }

    /**
     * Returns additional unmapped configuration fields.
     *
     * @return the extra configuration fields
     */
    @JsonAnyGetter
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

    /**
     * Builder for {@link ModelClientConfig}.
     */
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

        /**
         * Sets the client identifier.
         *
         * @param clientId the client identifier
         * @return this builder
         */
        @JsonProperty("client_id")
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets the client provider.
         *
         * @param clientProvider the client provider
         * @return this builder
         */
        @JsonProperty("client_provider")
        public Builder clientProvider(String clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        /**
         * Sets the API key.
         *
         * @param apiKey the API key
         * @return this builder
         */
        @JsonProperty("api_key")
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL.
         *
         * @param apiBase the API base URL
         * @return this builder
         */
        @JsonProperty("api_base")
        public Builder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        /**
         * Sets the request timeout in seconds.
         *
         * @param timeout the request timeout
         * @return this builder
         */
        @JsonProperty("timeout")
        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the HTTP version.
         *
         * @param httpVersion the HTTP version
         * @return this builder
         */
        @JsonProperty("http_version")
        public Builder httpVersion(ModelHttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        /**
         * Sets the maximum retry count.
         *
         * @param maxRetries the maximum retry count
         * @return this builder
         */
        @JsonProperty("max_retries")
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets whether SSL certificates should be verified.
         *
         * @param verifySsl whether SSL verification is enabled
         * @return this builder
         */
        @JsonProperty("verify_ssl")
        public Builder verifySsl(boolean verifySsl) {
            this.verifySsl = verifySsl;
            return this;
        }

        /**
         * Sets the SSL certificate path or content.
         *
         * @param sslCert the SSL certificate value
         * @return this builder
         */
        @JsonProperty("ssl_cert")
        public Builder sslCert(String sslCert) {
            this.sslCert = sslCert;
            return this;
        }

        /**
         * Replaces all configured headers.
         *
         * @param headers the headers to copy
         * @return this builder
         */
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

        /**
         * Adds a single header entry.
         *
         * @param key the header name
         * @param value the header value
         * @return this builder
         */
        public Builder header(String key, String value) {
            if (key != null && value != null) {
                this.headers.put(key, value);
            }
            return this;
        }

        /**
         * Adds an extra configuration field.
         *
         * @param key the field name
         * @param value the field value
         * @return this builder
         */
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

    /**
     * Returns a concise string representation without exposing secrets.
     *
     * @return the string representation of this config
     */
    @Override
    public String toString() {
        return "ModelClientConfig{clientId='" + clientId + "', clientProvider='" + clientProvider
                + "', apiBase='" + apiBase + "'}";
    }
}
