/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model client connection configuration.
 *
 * <p>Mirrors Python's {@code ModelClientConfig} in
 * {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelClientConfig {

    @JsonProperty("client_id")
    private String clientId = UUID.randomUUID().toString();

    @JsonProperty("client_provider")
    private String clientProvider;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("api_base")
    private String apiBase;

    private double timeout = 60.0;

    @JsonProperty("max_retries")
    private int maxRetries = 3;

    @JsonProperty("verify_ssl")
    private boolean verifySsl = true;

    @JsonProperty("ssl_cert")
    private String sslCert;

    @JsonProperty("custom_headers")
    private Map<String, Object> customHeaders = new LinkedHashMap<>();

    @JsonProperty("http_version")
    private ModelHttpVersion httpVersion;

    private Map<String, Object> extraFields = new LinkedHashMap<>();

    public ModelClientConfig(String clientId, String clientProvider, String apiKey, String apiBase, double timeout,
                             int maxRetries, boolean verifySsl, String sslCert,
                             Map<String, Object> customHeaders, Map<String, Object> extraFields) {
        this.clientId = clientId;
        this.clientProvider = clientProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.timeout = validatePositiveTimeout(timeout);
        this.maxRetries = maxRetries;
        this.verifySsl = verifySsl;
        this.sslCert = sslCert;
        this.customHeaders = customHeaders;
        this.extraFields = extraFields;
    }

    public static ModelClientConfigBuilder builder() {
        return new ModelClientConfigBuilder();
    }

    public static ModelClientConfig of(ProviderType provider, String apiKey, String apiBase) {
        return builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .build();
    }

    public void setClientProvider(String clientProvider) {
        this.clientProvider = normalizeClientProvider(clientProvider);
    }

    public void setClientProvider(ProviderType clientProvider) {
        this.clientProvider = clientProvider == null ? null : clientProvider.getValue();
    }

    public void setTimeout(double timeout) {
        this.timeout = validatePositiveTimeout(timeout);
    }

    /**
     * Mirrors Python's {@code validate_client_provider} in
     * {@code openjiuwen/core/foundation/llm/schema/config.py}.
     *
     * @param provider provider string or enum value
     * @return normalized provider string
     */
    public static String normalizeClientProvider(Object provider) {
        if (provider == null) {
            return null;
        }
        if (provider instanceof ProviderType providerType) {
            return providerType.getValue();
        }
        String normalized = String.valueOf(provider).strip();
        ProviderType member = ProviderType.fromPythonMemberName(normalized);
        if (member != null) {
            return normalized;
        }
        ProviderType lowerValue = ProviderType.fromLowercaseValue(normalized);
        if (lowerValue != null) {
            return lowerValue.getValue();
        }
        List<String> supportedTypes = supportedTypes();
        if (supportedTypes.contains(normalized)) {
            return normalized;
        }
        throw unavailableProvider(normalized);
    }

    private static RuntimeException unavailableProvider(String normalized) {
        throw ErrorHelper.buildError(
                StatusCode.MODEL_PROVIDER_INVALID,
                "error_msg",
                "unavailable model provider: " + normalized + ",and available providers are: " + supportedTypes()
        );
    }

    private static List<String> supportedTypes() {
        LinkedHashSet<String> supported = new LinkedHashSet<>();
        for (String name : ClientRegistry.getClientRegistry().listClients()) {
            if (name != null && name.startsWith("llm_")) {
                supported.add(name.substring(4));
            }
        }
        for (ProviderType providerType : ProviderType.values()) {
            supported.add(providerType.getValue());
        }
        return new ArrayList<>(supported);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extraFields == null) {
            extraFields = new LinkedHashMap<>();
        }
        extraFields.put(key, value);
    }

    public static final class ModelClientConfigBuilder {
        private String clientId = UUID.randomUUID().toString();
        private Object clientProvider;
        private String apiKey;
        private String apiBase;
        private double timeout = 60.0;
        private int maxRetries = 3;
        private boolean verifySsl = true;
        private String sslCert;
        private Map<String, Object> customHeaders = new LinkedHashMap<>();
        private ModelHttpVersion httpVersion;
        private Map<String, Object> extraFields = new LinkedHashMap<>();

        private ModelClientConfigBuilder() {
        }

        public ModelClientConfigBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public ModelClientConfigBuilder clientProvider(String clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        public ModelClientConfigBuilder clientProvider(ProviderType clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        public ModelClientConfigBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ModelClientConfigBuilder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        public ModelClientConfigBuilder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        public ModelClientConfigBuilder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ModelClientConfigBuilder verifySsl(boolean verifySsl) {
            this.verifySsl = verifySsl;
            return this;
        }

        public ModelClientConfigBuilder sslCert(String sslCert) {
            this.sslCert = sslCert;
            return this;
        }

        public ModelClientConfigBuilder customHeaders(Map<String, Object> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public ModelClientConfigBuilder httpVersion(ModelHttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public ModelClientConfigBuilder extraFields(Map<String, Object> extraFields) {
            this.extraFields = extraFields;
            return this;
        }

        public ModelClientConfig build() {
            ModelClientConfig config = new ModelClientConfig();
            config.clientId = clientId == null ? UUID.randomUUID().toString() : clientId;
            config.clientProvider = normalizeClientProvider(clientProvider);
            config.apiKey = apiKey;
            config.apiBase = apiBase;
            config.timeout = validatePositiveTimeout(timeout);
            config.maxRetries = maxRetries;
            config.verifySsl = verifySsl;
            config.sslCert = sslCert;
            config.customHeaders = customHeaders == null ? null : new LinkedHashMap<>(customHeaders);
            config.httpVersion = httpVersion;
            config.extraFields = extraFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraFields);
            return config;
        }
    }

    private static double validatePositiveTimeout(double timeout) {
        if (Double.isNaN(timeout) || timeout <= 0.0D) {
            throw new IllegalArgumentException("timeout must be greater than 0 (greater_than)");
        }
        return timeout;
    }
}
