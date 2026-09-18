/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single-endpoint router configuration shared across many model names.
 *
 * <p>Mirrors Python's {@code ModelRouterConfig} in
 * {@code openjiuwen/agent_teams/models/pool.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelRouterConfig {

    @JsonProperty("api_base_url")
    private String apiBaseUrl;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("api_provider")
    private String apiProvider;

    @JsonProperty("model_names")
    private List<String> modelNames = new ArrayList<>();

    private Map<String, Object> metadata = new java.util.LinkedHashMap<>();

    public ModelRouterConfig() {
    }

    public ModelRouterConfig(
            String apiBaseUrl,
            String apiKey,
            String apiProvider,
            List<String> modelNames
    ) {
        this(apiBaseUrl, apiKey, apiProvider, modelNames, Map.of());
    }

    public ModelRouterConfig(
            String apiBaseUrl,
            String apiKey,
            String apiProvider,
            List<String> modelNames,
            Map<String, Object> metadata
    ) {
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.apiProvider = apiProvider;
        this.modelNames = modelNames == null ? new ArrayList<>() : new ArrayList<>(modelNames);
        this.metadata = ModelPoolSupport.deepCopyMap(metadata);
        validateModelNames();
    }

    public List<ModelPoolEntry> toPoolEntries() {
        validateModelNames();
        List<ModelPoolEntry> entries = new ArrayList<>();
        for (String name : modelNames) {
            entries.add(new ModelPoolEntry(
                    name,
                    apiKey,
                    apiBaseUrl,
                    apiProvider,
                    ModelPoolSupport.deepCopyMap(metadata)
            ));
        }
        return entries;
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
        this.apiProvider = apiProvider;
    }

    public List<String> getModelNames() {
        return new ArrayList<>(modelNames);
    }

    public void setModelNames(List<String> modelNames) {
        this.modelNames = modelNames == null ? new ArrayList<>() : new ArrayList<>(modelNames);
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = ModelPoolSupport.deepCopyMap(metadata);
    }

    private void validateModelNames() {
        if (modelNames == null || modelNames.isEmpty()) {
            throw new IllegalArgumentException("ModelRouterConfig.model_names must not be empty");
        }
        List<Integer> blankIndexes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (int i = 0; i < modelNames.size(); i++) {
            String name = modelNames.get(i);
            if (name == null || name.strip().isEmpty()) {
                blankIndexes.add(i);
                continue;
            }
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        if (!blankIndexes.isEmpty()) {
            throw new IllegalArgumentException(
                    "ModelRouterConfig.model_names must contain non-empty strings; blank at indices: "
                            + blankIndexes);
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "ModelRouterConfig.model_names must be unique; duplicates: " + duplicates);
        }
    }
}
