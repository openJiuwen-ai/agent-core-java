/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code ContextEngineConfig} in
 * {@code openjiuwen/core/context_engine/schema/config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContextEngineConfig {
    @JsonProperty("max_context_message_num")
    private Integer maxContextMessageNum;

    @JsonProperty("default_window_message_num")
    private Integer defaultWindowMessageNum;

    @JsonProperty("default_window_round_num")
    private Integer defaultWindowRoundNum;

    @JsonProperty("enable_kv_cache_release")
    private boolean enableKvCacheRelease;

    @JsonProperty("enable_reload")
    private boolean enableReload;

    @JsonProperty("enable_tiktoken_counter")
    private boolean enableTiktokenCounter;

    @JsonProperty("context_window_tokens")
    private Integer contextWindowTokens;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("model_context_window_tokens")
    private Map<String, Integer> modelContextWindowTokens;

    @JsonProperty("enable_openrouter_model_context_window_tokens")
    private boolean enableOpenrouterModelContextWindowTokens;

    @JsonProperty("openrouter_request_timeout")
    private double openrouterRequestTimeout = 3.0;

    public Integer getMaxContextMessageNum() {
        return maxContextMessageNum;
    }

    public void setMaxContextMessageNum(Integer maxContextMessageNum) {
        validatePositive(maxContextMessageNum, "max_context_message_num");
        this.maxContextMessageNum = maxContextMessageNum;
    }

    public Integer getDefaultWindowMessageNum() {
        return defaultWindowMessageNum;
    }

    public void setDefaultWindowMessageNum(Integer defaultWindowMessageNum) {
        validatePositive(defaultWindowMessageNum, "default_window_message_num");
        this.defaultWindowMessageNum = defaultWindowMessageNum;
    }

    public Integer getDefaultWindowRoundNum() {
        return defaultWindowRoundNum;
    }

    public void setDefaultWindowRoundNum(Integer defaultWindowRoundNum) {
        validatePositive(defaultWindowRoundNum, "default_window_round_num");
        this.defaultWindowRoundNum = defaultWindowRoundNum;
    }

    public boolean isEnableKvCacheRelease() {
        return enableKvCacheRelease;
    }

    public void setEnableKvCacheRelease(boolean enableKvCacheRelease) {
        this.enableKvCacheRelease = enableKvCacheRelease;
    }

    public boolean isEnableReload() {
        return enableReload;
    }

    public void setEnableReload(boolean enableReload) {
        this.enableReload = enableReload;
    }

    public boolean isEnableTiktokenCounter() {
        return enableTiktokenCounter;
    }

    public void setEnableTiktokenCounter(boolean enableTiktokenCounter) {
        this.enableTiktokenCounter = enableTiktokenCounter;
    }

    public Integer getContextWindowTokens() {
        return contextWindowTokens;
    }

    public void setContextWindowTokens(Integer contextWindowTokens) {
        validatePositive(contextWindowTokens, "context_window_tokens");
        this.contextWindowTokens = contextWindowTokens;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Map<String, Integer> getModelContextWindowTokens() {
        return modelContextWindowTokens;
    }

    public void setModelContextWindowTokens(Map<String, Integer> modelContextWindowTokens) {
        this.modelContextWindowTokens = modelContextWindowTokens == null
                ? null
                : new LinkedHashMap<>(modelContextWindowTokens);
    }

    public boolean isEnableOpenrouterModelContextWindowTokens() {
        return enableOpenrouterModelContextWindowTokens;
    }

    public void setEnableOpenrouterModelContextWindowTokens(boolean enableOpenrouterModelContextWindowTokens) {
        this.enableOpenrouterModelContextWindowTokens = enableOpenrouterModelContextWindowTokens;
    }

    public double getOpenrouterRequestTimeout() {
        return openrouterRequestTimeout;
    }

    public void setOpenrouterRequestTimeout(double openrouterRequestTimeout) {
        if (openrouterRequestTimeout <= 0) {
            throw new IllegalArgumentException("openrouter_request_timeout must be > 0");
        }
        this.openrouterRequestTimeout = openrouterRequestTimeout;
    }

    private static void validatePositive(Integer value, String fieldName) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContextEngineConfig that)) {
            return false;
        }
        return enableKvCacheRelease == that.enableKvCacheRelease
                && enableReload == that.enableReload
                && enableTiktokenCounter == that.enableTiktokenCounter
                && enableOpenrouterModelContextWindowTokens == that.enableOpenrouterModelContextWindowTokens
                && Double.compare(openrouterRequestTimeout, that.openrouterRequestTimeout) == 0
                && Objects.equals(maxContextMessageNum, that.maxContextMessageNum)
                && Objects.equals(defaultWindowMessageNum, that.defaultWindowMessageNum)
                && Objects.equals(defaultWindowRoundNum, that.defaultWindowRoundNum)
                && Objects.equals(contextWindowTokens, that.contextWindowTokens)
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(modelContextWindowTokens, that.modelContextWindowTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maxContextMessageNum,
                defaultWindowMessageNum,
                defaultWindowRoundNum,
                enableKvCacheRelease,
                enableReload,
                enableTiktokenCounter,
                contextWindowTokens,
                modelName,
                modelContextWindowTokens,
                enableOpenrouterModelContextWindowTokens,
                openrouterRequestTimeout
        );
    }
}
