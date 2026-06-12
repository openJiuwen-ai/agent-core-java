/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Single LLM endpoint in a team's allocation pool.
 *
 * <p>Mirrors Python's {@code ModelPoolEntry} in
 * {@code openjiuwen/agent_teams/models/pool.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelPoolEntry {

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("api_base_url")
    private String apiBaseUrl;

    @JsonProperty("api_provider")
    private String apiProvider;

    private String description;

    @JsonProperty("model_id")
    private String modelId = UUID.randomUUID().toString();

    private Map<String, Object> metadata = new LinkedHashMap<>();

    public ModelPoolEntry() {
    }

    public ModelPoolEntry(String modelName, String apiKey, String apiBaseUrl, String apiProvider) {
        this(modelName, apiKey, apiBaseUrl, apiProvider, null, null, Map.of());
    }

    public ModelPoolEntry(
            String modelName,
            String apiKey,
            String apiBaseUrl,
            String apiProvider,
            Map<String, Object> metadata
    ) {
        this(modelName, apiKey, apiBaseUrl, apiProvider, null, null, metadata);
    }

    public ModelPoolEntry(
            String modelName,
            String apiKey,
            String apiBaseUrl,
            String apiProvider,
            String description,
            String modelId,
            Map<String, Object> metadata
    ) {
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
        this.apiProvider = apiProvider;
        this.description = description;
        this.modelId = modelId == null || modelId.isBlank() ? UUID.randomUUID().toString() : modelId;
        this.metadata = ModelPoolSupport.deepCopyMap(metadata);
    }

    public TeamModelConfig toTeamModelConfig() {
        Map<String, Object> clientExtra = mapValue(metadata.get("client"));
        Map<String, Object> requestExtra = mapValue(metadata.get("request"));

        ModelClientConfig clientConfig = new ModelClientConfig();
        applyClientExtras(clientConfig, clientExtra);
        clientConfig.setClientId(modelId);
        clientConfig.setClientProvider(apiProvider);
        clientConfig.setApiKey(apiKey);
        clientConfig.setApiBase(apiBaseUrl);

        ModelRequestConfig requestConfig = new ModelRequestConfig();
        applyRequestExtras(requestConfig, requestExtra);
        requestConfig.setModelName(modelName);
        return new TeamModelConfig(clientConfig, requestConfig);
    }

    public ModelPoolEntry copyWithModelId(String inheritedModelId) {
        return new ModelPoolEntry(
                modelName,
                apiKey,
                apiBaseUrl,
                apiProvider,
                description,
                inheritedModelId,
                metadata
        );
    }

    Map<String, Object> signaturePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_base_url", apiBaseUrl);
        payload.put("api_key", apiKey);
        payload.put("api_provider", apiProvider);
        payload.put("description", description);
        payload.put("metadata", ModelPoolSupport.deepCopyMap(metadata));
        payload.put("model_name", modelName);
        return payload;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(String apiProvider) {
        this.apiProvider = apiProvider;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId == null || modelId.isBlank() ? UUID.randomUUID().toString() : modelId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = ModelPoolSupport.deepCopyMap(metadata);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModelPoolEntry that)) {
            return false;
        }
        return Objects.equals(modelName, that.modelName)
                && Objects.equals(apiKey, that.apiKey)
                && Objects.equals(apiBaseUrl, that.apiBaseUrl)
                && Objects.equals(apiProvider, that.apiProvider)
                && Objects.equals(description, that.description)
                && Objects.equals(modelId, that.modelId)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName, apiKey, apiBaseUrl, apiProvider, description, modelId, metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), ModelPoolSupport.deepCopyValue(entry.getValue()));
            }
            return result;
        }
        return Map.of();
    }

    private static void applyClientExtras(ModelClientConfig config, Map<String, Object> extras) {
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "client_id" -> config.setClientId(stringValue(value));
                case "client_provider" -> config.setClientProvider(stringValue(value));
                case "api_key" -> config.setApiKey(stringValue(value));
                case "api_base" -> config.setApiBase(stringValue(value));
                case "timeout" -> config.setTimeout(doubleValue(value, config.getTimeout()));
                case "max_retries" -> config.setMaxRetries(intValue(value, config.getMaxRetries()));
                case "verify_ssl" -> config.setVerifySsl(booleanValue(value, config.isVerifySsl()));
                case "ssl_cert" -> config.setSslCert(stringValue(value));
                case "custom_headers" -> config.setCustomHeaders(mapValue(value));
                default -> config.setExtraField(key, value);
            }
        }
    }

    private static void applyRequestExtras(ModelRequestConfig config, Map<String, Object> extras) {
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "model" -> config.setModelName(stringValue(value));
                case "temperature" -> config.setTemperature(doubleValue(value, config.getTemperature()));
                case "top_p" -> config.setTopP(doubleValue(value, config.getTopP()));
                case "max_tokens" -> config.setMaxTokens(value == null ? null : intValue(value, 0));
                case "stop" -> config.setStop(stringValue(value));
                default -> config.setExtraField(key, value);
            }
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exc) {
            return fallback;
        }
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exc) {
            return fallback;
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Serializable model configuration materialized from a pool entry.
     *
     * <p>Mirrors Python's {@code TeamModelConfig} returned by
     * {@code ModelPoolEntry.to_team_model_config} in
     * {@code openjiuwen/agent_teams/models/pool.py}.</p>
     */
    public record TeamModelConfig(
            ModelClientConfig modelClientConfig,
            ModelRequestConfig modelRequestConfig
    ) {
        public ModelClientConfig getModelClientConfig() {
            return modelClientConfig;
        }

        public ModelRequestConfig getModelRequestConfig() {
            return modelRequestConfig;
        }
    }
}
