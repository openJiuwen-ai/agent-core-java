/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy LLM call configuration.
 *
 * <p>Mirrors Python's {@code LLMCallConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmCallConfig {
    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    @JsonProperty("system_prompt")
    private List<Map<String, Object>> systemPrompt = new ArrayList<>();

    @JsonProperty("user_prompt")
    private List<Map<String, Object>> userPrompt = new ArrayList<>();

    @JsonProperty("freeze_system_prompt")
    private boolean freezeSystemPrompt = false;

    @JsonProperty("freeze_user_prompt")
    private boolean freezeUserPrompt = true;

    public ModelRequestConfig getModel() {
        return model;
    }

    public void setModel(ModelRequestConfig model) {
        this.model = model;
    }

    public ModelClientConfig getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClientConfig modelClient) {
        this.modelClient = modelClient;
    }

    public List<Map<String, Object>> getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(List<Map<String, Object>> systemPrompt) {
        this.systemPrompt = copyPrompt(systemPrompt);
    }

    public List<Map<String, Object>> getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(List<Map<String, Object>> userPrompt) {
        this.userPrompt = copyPrompt(userPrompt);
    }

    public boolean isFreezeSystemPrompt() {
        return freezeSystemPrompt;
    }

    public void setFreezeSystemPrompt(boolean freezeSystemPrompt) {
        this.freezeSystemPrompt = freezeSystemPrompt;
    }

    public boolean isFreezeUserPrompt() {
        return freezeUserPrompt;
    }

    public void setFreezeUserPrompt(boolean freezeUserPrompt) {
        this.freezeUserPrompt = freezeUserPrompt;
    }

    private static List<Map<String, Object>> copyPrompt(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> item : source) {
                copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
            }
        }
        return copy;
    }
}
