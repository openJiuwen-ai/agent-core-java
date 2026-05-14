/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the ContextEngine.
 * <p>
 * Mirrors Python's {@code ContextEngineConfig} from
 * {@code context_engine/schema/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEngineConfig {

    /**
     * Maximum number of messages retained in the context message buffer.
     * {@code null} means unlimited. Must be > 0 if set.
     */
    private Integer maxContextMessageNum;

    /**
     * Default window size (number of messages) when building a context window.
     * {@code null} means no default limit. Must be > 0 if set.
     */
    private Integer defaultWindowMessageNum;

    /**
     * Default number of dialogue rounds to keep in the context window.
     * {@code null} means no round-based truncation by default. Must be > 0 if set.
     */
    private Integer defaultWindowRoundNum;

    /**
     * Whether to enable KV cache release optimisation for inference-affinity models.
     */
    @Builder.Default
    private boolean enableKvCacheRelease = false;

    /**
     * Whether to enable the reload tool that can re-inject offloaded messages.
     */
    @Builder.Default
    private boolean enableReload = false;

    public static ContextEngineConfigBuilder builder() {
        return new ContextEngineConfigBuilder();
    }

    public Integer getMaxContextMessageNum() {
        return maxContextMessageNum;
    }

    public void setMaxContextMessageNum(Integer maxContextMessageNum) {
        this.maxContextMessageNum = maxContextMessageNum;
    }

    public Integer getDefaultWindowMessageNum() {
        return defaultWindowMessageNum;
    }

    public void setDefaultWindowMessageNum(Integer defaultWindowMessageNum) {
        this.defaultWindowMessageNum = defaultWindowMessageNum;
    }

    public Integer getDefaultWindowRoundNum() {
        return defaultWindowRoundNum;
    }

    public void setDefaultWindowRoundNum(Integer defaultWindowRoundNum) {
        this.defaultWindowRoundNum = defaultWindowRoundNum;
    }

    public boolean isEnableKvCacheRelease() {
        return enableKvCacheRelease;
    }

    public void setEnableKvCacheRelease(boolean enableKvCacheRelease) {
        this.enableKvCacheRelease = enableKvCacheRelease;
    }

    public void setEnableReload(boolean enableReload) {
        this.enableReload = enableReload;
    }

    /**
     * Validate configuration constraints matching Python Pydantic {@code Field(gt=0)} rules.
     *
     * @throws IllegalArgumentException if any constraint is violated
     */
    public void validate() {
        if (maxContextMessageNum != null && maxContextMessageNum <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + maxContextMessageNum + ", input_type=int]");
        }
        if (defaultWindowMessageNum != null && defaultWindowMessageNum <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + defaultWindowMessageNum + ", input_type=int]");
        }
        if (defaultWindowRoundNum != null && defaultWindowRoundNum <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + defaultWindowRoundNum + ", input_type=int]");
        }
    }

    public boolean isEnableReload() {
        return enableReload;
    }

    public static final class ContextEngineConfigBuilder {
        private Integer maxContextMessageNum;
        private Integer defaultWindowMessageNum;
        private Integer defaultWindowRoundNum;
        private boolean enableKvCacheRelease = false;
        private boolean enableReload = false;

        public ContextEngineConfigBuilder maxContextMessageNum(Integer maxContextMessageNum) {
            this.maxContextMessageNum = maxContextMessageNum;
            return this;
        }

        public ContextEngineConfigBuilder defaultWindowMessageNum(Integer defaultWindowMessageNum) {
            this.defaultWindowMessageNum = defaultWindowMessageNum;
            return this;
        }

        public ContextEngineConfigBuilder defaultWindowRoundNum(Integer defaultWindowRoundNum) {
            this.defaultWindowRoundNum = defaultWindowRoundNum;
            return this;
        }

        public ContextEngineConfigBuilder enableKvCacheRelease(boolean enableKvCacheRelease) {
            this.enableKvCacheRelease = enableKvCacheRelease;
            return this;
        }

        public ContextEngineConfigBuilder enableReload(boolean enableReload) {
            this.enableReload = enableReload;
            return this;
        }

        public ContextEngineConfig build() {
            ContextEngineConfig config = new ContextEngineConfig();
            config.maxContextMessageNum = this.maxContextMessageNum;
            config.defaultWindowMessageNum = this.defaultWindowMessageNum;
            config.defaultWindowRoundNum = this.defaultWindowRoundNum;
            config.enableKvCacheRelease = this.enableKvCacheRelease;
            config.enableReload = this.enableReload;
            return config;
        }
    }
}
