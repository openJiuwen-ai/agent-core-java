/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable vision model configuration.
 *
 * <p>Mirrors Python's {@code VisionModelSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class VisionModelSpec {

    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENAI_VISION_MODEL = "gpt-4o";

    private String apiKey = "";
    private String baseUrl = DEFAULT_OPENAI_BASE_URL;
    private String model = DEFAULT_OPENAI_VISION_MODEL;
    private int maxRetries = 3;

    public Map<String, Object> build() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("api_key", apiKey);
        values.put("base_url", baseUrl);
        values.put("model", model);
        values.put("max_retries", maxRetries);
        return values;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? DEFAULT_OPENAI_BASE_URL : baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? DEFAULT_OPENAI_VISION_MODEL : model;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
