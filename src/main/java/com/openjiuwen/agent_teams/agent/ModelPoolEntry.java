/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry in the model pool.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.schema.team.ModelPoolEntry}.
 */
public class ModelPoolEntry {

    private String modelName;
    private String apiBaseUrl;
    private String apiKey;
    private String apiProvider = "OpenAI";
    private String description;
    private String modelId = UUID.randomUUID().toString();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public ModelPoolEntry() {
    }

    public ModelPoolEntry(String modelName, String apiBaseUrl, String apiKey) {
        this.modelName = modelName;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
    }

    public ModelPoolEntry(String modelName, String apiBaseUrl, String apiKey, Map<String, Object> metadata) {
        this.modelName = modelName;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.metadata = copyMap(metadata);
    }

    public ModelPoolEntry(String modelName, String apiKey, String apiBaseUrl, String apiProvider) {
        this.modelName = modelName;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.apiProvider = apiProvider != null ? apiProvider : "OpenAI";
    }

    public ModelPoolEntry(
            String modelName,
            String apiKey,
            String apiBaseUrl,
            String apiProvider,
            Map<String, Object> metadata
    ) {
        this(modelName, apiKey, apiBaseUrl, apiProvider);
        this.metadata = copyMap(metadata);
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(String apiProvider) {
        this.apiProvider = apiProvider != null ? apiProvider : "OpenAI";
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
        this.modelId = modelId != null ? modelId : UUID.randomUUID().toString();
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = copyMap(metadata);
    }

    /**
     * Materialize a TeamModelConfig from this pool entry.
     *
     * @return typed model configuration for a team member
     */
    public TeamModelConfig toTeamModelConfig() {
        Map<String, Object> clientExtra = asMap(metadata.get("client"));
        Map<String, Object> requestExtra = asMap(metadata.get("request"));

        ModelClientConfig.Builder clientBuilder = ModelClientConfig.builder();
        applyClientMetadata(clientBuilder, clientExtra);
        clientBuilder
                .clientId(modelId)
                .clientProvider(apiProvider)
                .apiKey(apiKey)
                .apiBase(apiBaseUrl);

        ModelRequestConfig.ModelRequestConfigBuilder requestBuilder = ModelRequestConfig.builder();
        applyRequestMetadata(requestBuilder, requestExtra);
        requestBuilder.modelName(modelName);

        return new TeamModelConfig(clientBuilder.build(), requestBuilder.build());
    }

    /**
     * Carry model_id from current entries into bit-exact replacement entries.
     *
     * <p>Mirrors Python's {@code inherit_pool_ids} in
     * {@code openjiuwen.agent_teams.schema.team}.</p>
     *
     * @param currentPool current pool
     * @param newPool replacement pool
     * @return replacement entries with inherited IDs where safe
     */
    public static List<ModelPoolEntry> inheritPoolIds(
            List<ModelPoolEntry> currentPool,
            List<ModelPoolEntry> newPool
    ) {
        Map<EntrySignature, Deque<ModelPoolEntry>> oldBySignature = new HashMap<>();
        for (ModelPoolEntry entry : safeList(currentPool)) {
            oldBySignature.computeIfAbsent(EntrySignature.from(entry), ignored -> new ArrayDeque<>()).add(entry);
        }

        List<ModelPoolEntry> result = new ArrayList<>();
        for (ModelPoolEntry newEntry : safeList(newPool)) {
            Deque<ModelPoolEntry> bucket = oldBySignature.get(EntrySignature.from(newEntry));
            if (bucket != null && !bucket.isEmpty()) {
                result.add(newEntry.copyWithModelId(bucket.removeFirst().getModelId()));
            } else {
                result.add(newEntry);
            }
        }
        return result;
    }

    private ModelPoolEntry copyWithModelId(String inheritedModelId) {
        ModelPoolEntry copy = new ModelPoolEntry();
        copy.setModelName(modelName);
        copy.setApiBaseUrl(apiBaseUrl);
        copy.setApiKey(apiKey);
        copy.setApiProvider(apiProvider);
        copy.setDescription(description);
        copy.setMetadata(metadata);
        copy.setModelId(inheritedModelId);
        return copy;
    }

    private static void applyClientMetadata(
            ModelClientConfig.Builder builder,
            Map<String, Object> clientExtra
    ) {
        for (Map.Entry<String, Object> entry : clientExtra.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("client_id".equals(key) || "clientProvider".equals(key) || "client_provider".equals(key)
                    || "api_key".equals(key) || "api_base".equals(key)) {
                continue;
            }
            switch (key) {
                case "timeout" -> builder.timeout(toDouble(value, 60.0));
                case "verify_ssl" -> builder.verifySsl(toBoolean(value, true));
                case "max_retries" -> builder.maxRetries(toInt(value, 3));
                case "ssl_cert" -> builder.sslCert(value != null ? String.valueOf(value) : null);
                case "headers", "custom_headers" -> builder.headers(asMap(value));
                default -> builder.extraField(key, value);
            }
        }
    }

    private static void applyRequestMetadata(
            ModelRequestConfig.ModelRequestConfigBuilder builder,
            Map<String, Object> requestExtra
    ) {
        Map<String, Object> extras = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : requestExtra.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("model".equals(key) || "model_name".equals(key)) {
                continue;
            }
            switch (key) {
                case "temperature" -> builder.temperature(toDouble(value, 0.95));
                case "top_p" -> builder.topP(toDouble(value, 0.1));
                case "max_tokens" -> builder.maxTokens(toInteger(value));
                case "stop" -> builder.stop(value != null ? String.valueOf(value) : null);
                case "user" -> builder.user(value != null ? String.valueOf(value) : null);
                case "seed" -> builder.seed(toInteger(value));
                default -> extras.put(key, value);
            }
        }
        if (!extras.isEmpty()) {
            builder.extraFields(extras);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> {
                if (key != null) {
                    result.put(String.valueOf(key), val);
                }
            });
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int toInt(Object value, int defaultValue) {
        Integer converted = toInteger(value);
        return converted != null ? converted : defaultValue;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean toBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private static List<ModelPoolEntry> safeList(List<ModelPoolEntry> entries) {
        return entries != null ? entries : List.of();
    }

    private record EntrySignature(
            String modelName,
            String apiBaseUrl,
            String apiKey,
            String apiProvider,
            String description,
            Map<String, Object> metadata
    ) {
        static EntrySignature from(ModelPoolEntry entry) {
            return new EntrySignature(
                    entry != null ? entry.getModelName() : null,
                    entry != null ? entry.getApiBaseUrl() : null,
                    entry != null ? entry.getApiKey() : null,
                    entry != null ? entry.getApiProvider() : null,
                    entry != null ? entry.getDescription() : null,
                    entry != null ? entry.getMetadata() : Map.of()
            );
        }

        EntrySignature {
            metadata = metadata != null ? new LinkedHashMap<>(metadata) : Map.of();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EntrySignature other)) {
                return false;
            }
            return Objects.equals(modelName, other.modelName)
                    && Objects.equals(apiBaseUrl, other.apiBaseUrl)
                    && Objects.equals(apiKey, other.apiKey)
                    && Objects.equals(apiProvider, other.apiProvider)
                    && Objects.equals(description, other.description)
                    && Objects.equals(metadata, other.metadata);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelName, apiBaseUrl, apiKey, apiProvider, description, metadata);
        }
    }
}
