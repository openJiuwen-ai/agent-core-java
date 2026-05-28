/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entry in the model pool.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.schema.team.ModelPoolEntry}.
 */
public class ModelPoolEntry {

    private final String modelName;
    private final String apiBaseUrl;
    private final String apiKey;
    private final Map<String, Object> metadata;

    public ModelPoolEntry(String modelName, String apiBaseUrl, String apiKey) {
        this.modelName = modelName;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.metadata = new LinkedHashMap<>();
    }

    public ModelPoolEntry(String modelName, String apiBaseUrl, String apiKey, Map<String, Object> metadata) {
        this.modelName = modelName;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }

    public String getModelName() {
        return modelName;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Map<String, Object> toTeamModelConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("model_name", modelName);
        config.put("api_base_url", apiBaseUrl);
        config.put("api_key", apiKey);
        if (metadata != null) {
            config.putAll(metadata);
        }
        return config;
    }
}