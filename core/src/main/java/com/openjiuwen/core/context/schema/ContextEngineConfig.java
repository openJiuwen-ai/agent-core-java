/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context.offload.model.OffloadConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code ContextEngineConfig} in
 * {@code openjiuwen/core/context_engine/schema/config.py}.
 *
 * <p>v2 (架构文档 §3.4): carries the Context Offload switch
 * ({@code enable_offload}, default off) and the nested
 * {@link OffloadConfig} block; unknown keys keep parsing.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class ContextEngineConfig {

    /** Default bound applied when {@code max_context_message_num} is left unconfigured. */
    public static final int DEFAULT_MAX_CONTEXT_MESSAGE_NUM = 200;

    /** Default bound applied when {@code default_window_round_num} is left unconfigured. */
    public static final int DEFAULT_WINDOW_ROUND_NUM = 10;

    /** Default bound applied when {@code default_window_message_num} is left unconfigured. */
    public static final int DEFAULT_WINDOW_MESSAGE_NUM = 200;

    @JsonProperty("max_context_message_num")
    private Integer maxContextMessageNum;

    @JsonProperty("default_window_message_num")
    private Integer defaultWindowMessageNum;

    @JsonProperty("default_window_round_num")
    private Integer defaultWindowRoundNum;


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

    @JsonProperty("enable_offload")
    private boolean isOffloadEnabled;

    @JsonProperty("offload")
    private OffloadConfig offload;

    /**
     * When true (default), {@code ContextEngine.recoverFromModelException} may
     * compress and signal a model-call retry on context-overflow provider errors.
     */
    @JsonProperty("enable_model_context_overflow_recovery")
    private boolean enableModelContextOverflowRecovery = true;

    /**
     * Extra phrases matched by {@code ContextEngine.isContextOverflowError}.
     */
    @JsonProperty("extra_context_overflow_keywords")
    private List<String> extraContextOverflowKeywords;

    public ContextEngineConfig() {
    }

    public ContextEngineConfig(Integer maxContextMessageNum, Integer defaultWindowMessageNum,
                               Integer defaultWindowRoundNum, boolean isReloadEnabled,
                               boolean enableTiktokenCounter, Integer contextWindowTokens, String modelName,
                               Map<String, Integer> modelContextWindowTokens) {
        this(maxContextMessageNum, defaultWindowMessageNum, defaultWindowRoundNum, isReloadEnabled,
                enableTiktokenCounter, contextWindowTokens, modelName, modelContextWindowTokens, false, 3.0);
    }

    public ContextEngineConfig(Integer maxContextMessageNum, Integer defaultWindowMessageNum,
                               Integer defaultWindowRoundNum, boolean isReloadEnabled,
                               boolean enableTiktokenCounter, Integer contextWindowTokens, String modelName,
                               Map<String, Integer> modelContextWindowTokens,
                               boolean enableOpenrouterModelContextWindowTokens,
                               double openrouterRequestTimeout) {
        validatePositive(maxContextMessageNum, "max_context_message_num");
        validatePositive(defaultWindowMessageNum, "default_window_message_num");
        validatePositive(defaultWindowRoundNum, "default_window_round_num");
        validatePositive(contextWindowTokens, "context_window_tokens");
        if (openrouterRequestTimeout <= 0) {
            throw new IllegalArgumentException("openrouter_request_timeout must be > 0");
        }
        this.maxContextMessageNum = maxContextMessageNum;
        this.defaultWindowMessageNum = defaultWindowMessageNum;
        this.defaultWindowRoundNum = defaultWindowRoundNum;
        this.enableReload = isReloadEnabled;
        this.enableTiktokenCounter = enableTiktokenCounter;
        this.contextWindowTokens = contextWindowTokens;
        this.modelName = modelName;
        this.modelContextWindowTokens = modelContextWindowTokens == null
                ? null
                : new LinkedHashMap<>(modelContextWindowTokens);
        this.enableOpenrouterModelContextWindowTokens = enableOpenrouterModelContextWindowTokens;
        this.openrouterRequestTimeout = openrouterRequestTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isTiktokenCounterEnabled() {
        return isEnableTiktokenCounter();
    }

    public void setTiktokenCounterEnabled(boolean tiktokenCounterEnabled) {
        setEnableTiktokenCounter(tiktokenCounterEnabled);
    }

    public void validate() {
        setMaxContextMessageNum(getMaxContextMessageNum());
        setDefaultWindowMessageNum(getDefaultWindowMessageNum());
        setDefaultWindowRoundNum(getDefaultWindowRoundNum());
        setContextWindowTokens(getContextWindowTokens());
        setOpenrouterRequestTimeout(getOpenrouterRequestTimeout());
    }

    public Integer getMaxContextMessageNum() {
        return Objects.requireNonNullElse(maxContextMessageNum, DEFAULT_MAX_CONTEXT_MESSAGE_NUM);
    }

    public void setMaxContextMessageNum(Integer maxContextMessageNum) {
        validatePositive(maxContextMessageNum, "max_context_message_num");
        this.maxContextMessageNum = maxContextMessageNum;
    }

    public Integer getDefaultWindowMessageNum() {
        return Objects.requireNonNullElse(defaultWindowMessageNum, DEFAULT_WINDOW_MESSAGE_NUM);
    }

    public void setDefaultWindowMessageNum(Integer defaultWindowMessageNum) {
        validatePositive(defaultWindowMessageNum, "default_window_message_num");
        this.defaultWindowMessageNum = defaultWindowMessageNum;
    }

    public Integer getDefaultWindowRoundNum() {
        return Objects.requireNonNullElse(defaultWindowRoundNum, DEFAULT_WINDOW_ROUND_NUM);
    }

    public void setDefaultWindowRoundNum(Integer defaultWindowRoundNum) {
        validatePositive(defaultWindowRoundNum, "default_window_round_num");
        this.defaultWindowRoundNum = defaultWindowRoundNum;
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

    /**
     * Returns whether the Context Offload v2 pipeline is enabled (default off).
     *
     * @return {@code true} when the Context Offload v2 pipeline is enabled
     */
    public boolean isEnableOffload() {
        return isOffloadEnabled;
    }

    /**
     * Sets whether the Context Offload v2 pipeline is enabled.
     *
     * @param isOffloadEnabled whether to enable the Context Offload v2 pipeline
     */
    public void setEnableOffload(boolean isOffloadEnabled) {
        this.isOffloadEnabled = isOffloadEnabled;
    }

    /**
     * Returns the nested offload pipeline configuration.
     *
     * @return the nested offload pipeline configuration, or {@code null} for defaults
     */
    public OffloadConfig getOffload() {
        return offload;
    }

    public void setOffload(OffloadConfig offload) {
        this.offload = offload;
    }

    /**
     * Returns whether model context-overflow recovery is enabled.
     *
     * @return {@code true} when compress-and-retry recovery is allowed (default)
     */
    public boolean isEnableModelContextOverflowRecovery() {
        return enableModelContextOverflowRecovery;
    }

    /**
     * Sets whether model context-overflow recovery is enabled.
     *
     * @param enableModelContextOverflowRecovery {@code true} to allow recovery
     */
    public void setEnableModelContextOverflowRecovery(boolean enableModelContextOverflowRecovery) {
        this.enableModelContextOverflowRecovery = enableModelContextOverflowRecovery;
    }

    /**
     * Returns extra overflow keyword phrases for provider-specific error text.
     *
     * @return extra keywords, or {@code null} when unset
     */
    public List<String> getExtraContextOverflowKeywords() {
        return extraContextOverflowKeywords == null ? null : new ArrayList<>(extraContextOverflowKeywords);
    }

    /**
     * Sets extra overflow keyword phrases for provider-specific error text.
     *
     * @param extraContextOverflowKeywords additional phrases; may be {@code null}
     */
    public void setExtraContextOverflowKeywords(List<String> extraContextOverflowKeywords) {
        this.extraContextOverflowKeywords = extraContextOverflowKeywords == null
                ? null
                : new ArrayList<>(extraContextOverflowKeywords);
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
        return enableReload == that.enableReload
                && enableTiktokenCounter == that.enableTiktokenCounter
                && enableOpenrouterModelContextWindowTokens == that.enableOpenrouterModelContextWindowTokens
                && isOffloadEnabled == that.isOffloadEnabled
                && enableModelContextOverflowRecovery == that.enableModelContextOverflowRecovery
                && Double.compare(openrouterRequestTimeout, that.openrouterRequestTimeout) == 0
                && Objects.equals(getMaxContextMessageNum(), that.getMaxContextMessageNum())
                && Objects.equals(getDefaultWindowMessageNum(), that.getDefaultWindowMessageNum())
                && Objects.equals(getDefaultWindowRoundNum(), that.getDefaultWindowRoundNum())
                && Objects.equals(contextWindowTokens, that.contextWindowTokens)
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(modelContextWindowTokens, that.modelContextWindowTokens)
                && Objects.equals(offload, that.offload)
                && Objects.equals(extraContextOverflowKeywords, that.extraContextOverflowKeywords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getMaxContextMessageNum(),
                getDefaultWindowMessageNum(),
                getDefaultWindowRoundNum(),
                enableReload,
                enableTiktokenCounter,
                contextWindowTokens,
                modelName,
                modelContextWindowTokens,
                enableOpenrouterModelContextWindowTokens,
                openrouterRequestTimeout,
                isOffloadEnabled,
                offload,
                enableModelContextOverflowRecovery,
                extraContextOverflowKeywords
        );
    }

    public static final class Builder {
        private Integer maxContextMessageNum;
        private Integer defaultWindowMessageNum;
        private Integer defaultWindowRoundNum;
        private boolean enableReload;
        private boolean enableTiktokenCounter;
        private Integer contextWindowTokens;
        private String modelName;
        private Map<String, Integer> modelContextWindowTokens;
        private boolean enableOpenrouterModelContextWindowTokens;
        private double openrouterRequestTimeout = 3.0;
        private boolean isOffloadEnabled;
        private OffloadConfig offload;
        private boolean enableModelContextOverflowRecovery = true;
        private List<String> extraContextOverflowKeywords;

        private Builder() {
        }

        public Builder maxContextMessageNum(Integer maxContextMessageNum) {
            this.maxContextMessageNum = maxContextMessageNum;
            return this;
        }

        public Builder defaultWindowMessageNum(Integer defaultWindowMessageNum) {
            this.defaultWindowMessageNum = defaultWindowMessageNum;
            return this;
        }

        public Builder defaultWindowRoundNum(Integer defaultWindowRoundNum) {
            this.defaultWindowRoundNum = defaultWindowRoundNum;
            return this;
        }

        public Builder enableReload(boolean enableReload) {
            this.enableReload = enableReload;
            return this;
        }

        public Builder enableTiktokenCounter(boolean enableTiktokenCounter) {
            this.enableTiktokenCounter = enableTiktokenCounter;
            return this;
        }

        public Builder isTiktokenCounterEnabled(boolean tiktokenCounterEnabled) {
            this.enableTiktokenCounter = tiktokenCounterEnabled;
            return this;
        }

        public Builder contextWindowTokens(Integer contextWindowTokens) {
            this.contextWindowTokens = contextWindowTokens;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelContextWindowTokens(Map<String, Integer> modelContextWindowTokens) {
            this.modelContextWindowTokens = modelContextWindowTokens == null
                    ? null
                    : new LinkedHashMap<>(modelContextWindowTokens);
            return this;
        }

        public Builder enableOpenrouterModelContextWindowTokens(boolean enableOpenrouterModelContextWindowTokens) {
            this.enableOpenrouterModelContextWindowTokens = enableOpenrouterModelContextWindowTokens;
            return this;
        }

        public Builder openrouterRequestTimeout(double openrouterRequestTimeout) {
            this.openrouterRequestTimeout = openrouterRequestTimeout;
            return this;
        }

        /**
         * Sets whether the Context Offload v2 pipeline is enabled.
         *
         * @param isOffloadEnabled {@code true} to enable the offload pipeline, {@code false} to disable (default)
         * @return this builder for chaining
         */
        public Builder enableOffload(boolean isOffloadEnabled) {
            this.isOffloadEnabled = isOffloadEnabled;
            return this;
        }

        /**
         * Sets the nested offload pipeline configuration.
         *
         * @param offload the offload configuration block, or {@code null} to use defaults
         * @return this builder for chaining
         */
        public Builder offload(OffloadConfig offload) {
            this.offload = offload;
            return this;
        }

        /**
         * Sets whether model context-overflow recovery is enabled.
         *
         * @param enableModelContextOverflowRecovery {@code true} to allow compress-and-retry
         * @return this builder for chaining
         */
        public Builder enableModelContextOverflowRecovery(boolean enableModelContextOverflowRecovery) {
            this.enableModelContextOverflowRecovery = enableModelContextOverflowRecovery;
            return this;
        }

        /**
         * Sets extra overflow keyword phrases for provider-specific error text.
         *
         * @param extraContextOverflowKeywords additional phrases; may be {@code null}
         * @return this builder for chaining
         */
        public Builder extraContextOverflowKeywords(List<String> extraContextOverflowKeywords) {
            this.extraContextOverflowKeywords = extraContextOverflowKeywords;
            return this;
        }

        public ContextEngineConfig build() {
            ContextEngineConfig config = new ContextEngineConfig(maxContextMessageNum, defaultWindowMessageNum,
                    defaultWindowRoundNum, enableReload, enableTiktokenCounter,
                    contextWindowTokens, modelName, modelContextWindowTokens,
                    enableOpenrouterModelContextWindowTokens, openrouterRequestTimeout);
            config.setEnableOffload(isOffloadEnabled);
            config.setOffload(offload);
            config.setEnableModelContextOverflowRecovery(enableModelContextOverflowRecovery);
            config.setExtraContextOverflowKeywords(extraContextOverflowKeywords);
            return config;
        }
    }
}
