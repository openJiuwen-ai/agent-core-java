/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backward-compatible alias for the pre-0.1.14 context engine config package.
 * <p>
 * Mirrors Python's {@code ContextEngineConfig} in
 * {@code openjiuwen/core/context_engine/schema/config.py}.
 */
public class ContextEngineConfig extends com.openjiuwen.core.context_engine.schema.ContextEngineConfig {
    public ContextEngineConfig() {
    }

    public ContextEngineConfig(Integer maxContextMessageNum, Integer defaultWindowMessageNum,
                               Integer defaultWindowRoundNum, boolean enableKvCacheRelease, boolean enableReload,
                               boolean enableTiktokenCounter, Integer contextWindowTokens, String modelName,
                               Map<String, Integer> modelContextWindowTokens) {
        this(maxContextMessageNum, defaultWindowMessageNum, defaultWindowRoundNum, enableKvCacheRelease, enableReload,
                enableTiktokenCounter, contextWindowTokens, modelName, modelContextWindowTokens, false, 3.0);
    }

    public ContextEngineConfig(Integer maxContextMessageNum, Integer defaultWindowMessageNum,
                               Integer defaultWindowRoundNum, boolean enableKvCacheRelease, boolean enableReload,
                               boolean enableTiktokenCounter, Integer contextWindowTokens, String modelName,
                               Map<String, Integer> modelContextWindowTokens,
                               boolean enableOpenrouterModelContextWindowTokens,
                               double openrouterRequestTimeout) {
        setMaxContextMessageNum(maxContextMessageNum);
        setDefaultWindowMessageNum(defaultWindowMessageNum);
        setDefaultWindowRoundNum(defaultWindowRoundNum);
        setEnableKvCacheRelease(enableKvCacheRelease);
        setEnableReload(enableReload);
        setEnableTiktokenCounter(enableTiktokenCounter);
        setContextWindowTokens(contextWindowTokens);
        setModelName(modelName);
        setModelContextWindowTokens(modelContextWindowTokens);
        setEnableOpenrouterModelContextWindowTokens(enableOpenrouterModelContextWindowTokens);
        setOpenrouterRequestTimeout(openrouterRequestTimeout);
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

    public static final class Builder {
        private Integer maxContextMessageNum;
        private Integer defaultWindowMessageNum;
        private Integer defaultWindowRoundNum;
        private boolean enableKvCacheRelease;
        private boolean enableReload;
        private boolean enableTiktokenCounter;
        private Integer contextWindowTokens;
        private String modelName;
        private Map<String, Integer> modelContextWindowTokens;
        private boolean enableOpenrouterModelContextWindowTokens;
        private double openrouterRequestTimeout = 3.0;

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

        public Builder enableKvCacheRelease(boolean enableKvCacheRelease) {
            this.enableKvCacheRelease = enableKvCacheRelease;
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

        public ContextEngineConfig build() {
            return new ContextEngineConfig(maxContextMessageNum, defaultWindowMessageNum, defaultWindowRoundNum,
                    enableKvCacheRelease, enableReload, enableTiktokenCounter, contextWindowTokens, modelName,
                    modelContextWindowTokens, enableOpenrouterModelContextWindowTokens, openrouterRequestTimeout);
        }
    }
}
