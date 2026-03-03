/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public class ModelClientConfig {

    private final String clientId;
    private final String clientProvider;
    private final String apiKey;
    private final String apiBase;
    private final double timeout;
    private final int maxRetries;
    private final boolean verifySsl;
    private final String sslCert;
    private final Map<String, Object> extraFields;

    private ModelClientConfig(Builder builder) {
        this.clientId = builder.clientId != null ? builder.clientId : UUID.randomUUID().toString();
        this.clientProvider = Objects.requireNonNull(builder.clientProvider, "clientProvider must not be null");
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey must not be null");
        this.apiBase = Objects.requireNonNull(builder.apiBase, "apiBase must not be null");
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

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

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

        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder clientProvider(String clientProvider) { this.clientProvider = clientProvider; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder apiBase(String apiBase) { this.apiBase = apiBase; return this; }
        public Builder timeout(double timeout) { this.timeout = timeout; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder verifySsl(boolean verifySsl) { this.verifySsl = verifySsl; return this; }
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
