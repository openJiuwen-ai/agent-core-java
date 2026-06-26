/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.Locale;

/**
 * Session-memory trigger and updater configuration.
 *
 * <p>Mirrors Python's {@code SessionMemoryConfig} in
 * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
 */
public class SessionMemoryConfig {

    @JsonProperty("trigger_tokens")
    private int triggerTokens = 10000;

    @JsonProperty("trigger_add_tokens")
    private int triggerAddTokens = 5000;

    @JsonProperty("tool_min_")
    private int toolMin = 3;

    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    @JsonProperty("update_mode")
    private UpdateMode updateMode = UpdateMode.AGENT_EDIT;

    @JsonProperty("direct_replace_max_retries")
    private int directReplaceMaxRetries = 2;

    public int getTriggerTokens() {
        return triggerTokens;
    }

    public void setTriggerTokens(int triggerTokens) {
        if (triggerTokens <= 0) {
            throw new IllegalArgumentException("trigger_tokens must be greater than 0");
        }
        this.triggerTokens = triggerTokens;
    }

    public int getTriggerAddTokens() {
        return triggerAddTokens;
    }

    public void setTriggerAddTokens(int triggerAddTokens) {
        if (triggerAddTokens <= 0) {
            throw new IllegalArgumentException("trigger_add_tokens must be greater than 0");
        }
        this.triggerAddTokens = triggerAddTokens;
    }

    public int getToolMin() {
        return toolMin;
    }

    public void setToolMin(int toolMin) {
        if (toolMin <= 0) {
            throw new IllegalArgumentException("tool_min_ must be greater than 0");
        }
        this.toolMin = toolMin;
    }

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

    public UpdateMode getUpdateMode() {
        return updateMode;
    }

    public void setUpdateMode(UpdateMode updateMode) {
        this.updateMode = updateMode == null ? UpdateMode.AGENT_EDIT : updateMode;
    }

    @JsonProperty("update_mode")
    public void setUpdateMode(String updateMode) {
        this.updateMode = UpdateMode.fromValue(updateMode);
    }

    public int getDirectReplaceMaxRetries() {
        return directReplaceMaxRetries;
    }

    public void setDirectReplaceMaxRetries(int directReplaceMaxRetries) {
        if (directReplaceMaxRetries < 0) {
            throw new IllegalArgumentException("direct_replace_max_retries must be greater than or equal to 0");
        }
        this.directReplaceMaxRetries = directReplaceMaxRetries;
    }

    /**
     * Session memory update mode literals.
     *
     * <p>Mirrors Python's {@code Literal["agent_edit", "direct_replace"]} in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public enum UpdateMode {
        AGENT_EDIT("agent_edit"),
        DIRECT_REPLACE("direct_replace");

        private final String value;

        UpdateMode(String value) {
            this.value = value;
        }

        @JsonProperty
        public String getValue() {
            return value;
        }

        public static UpdateMode fromValue(String value) {
            if (value == null || value.isBlank()) {
                return AGENT_EDIT;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            for (UpdateMode mode : values()) {
                if (mode.value.equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported session memory update_mode: " + value);
        }
    }
}
