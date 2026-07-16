/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration for the ContextEngine.
 * <p>
 * Mirrors Python's {@code ContextEngineConfig} from {@code context_engine/schema/config.py}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEngineConfig {
    private Integer maxContextMessageNum;

    /**
     * Default window size (number of messages) when building a context window. {@code null} means no
     * default limit. Must be > 0 if set.
     */
    private Integer defaultWindowMessageNum;

    /**
     * Default number of dialogue rounds to keep in the context window. {@code null} means no
     * round-based truncation by default. Must be > 0 if set.
     */
    private Integer defaultWindowRoundNum;

    /** Whether to enable KV cache release optimisation for inference-affinity models. */
    @Builder.Default
    private boolean enableKvCacheRelease = false;

    /** Whether to enable the reload tool that can re-inject offloaded messages. */
    @Builder.Default
    private boolean enableReload = false;

    /** Whether to enable the Python-compatible tiktoken counter flag. */
    @Builder.Default
    @JsonProperty("enable_tiktoken_counter")
    private boolean isTiktokenCounterEnabled = false;

    /** Optional total context window token limit used for compression telemetry. */
    @JsonProperty("context_window_tokens")
    private Integer contextWindowTokens;

    /** Optional model name used to resolve context window mappings. */
    @JsonProperty("model_name")
    private String modelName;

    /** Optional mapping from model name to total context window tokens. */
    @JsonProperty("model_context_window_tokens")
    private Map<String, Integer> modelContextWindowTokens;

    /**
     * ContextEngineConfigBuilder.
     * 
     * @since 0.1.7
     */
    public static class ContextEngineConfigBuilder {
        /**
         * enableTiktokenCounter.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public ContextEngineConfigBuilder enableTiktokenCounter(boolean value) {
            return this.isTiktokenCounterEnabled(value);
        }
    }

    /**
     * Validate configuration constraints matching Python Pydantic {@code Field(gt=0)} rules.
     * 
     * @since 0.1.7
     */
    public void validate() {
        if (maxContextMessageNum != null && maxContextMessageNum <= 0) {
            throw new IllegalArgumentException("Input should be greater than 0 [type=greater_than, input_value="
                    + maxContextMessageNum + ", input_type=int]");
        }
        if (defaultWindowMessageNum != null && defaultWindowMessageNum <= 0) {
            throw new IllegalArgumentException("Input should be greater than 0 [type=greater_than, input_value="
                    + defaultWindowMessageNum + ", input_type=int]");
        }
        if (defaultWindowRoundNum != null && defaultWindowRoundNum <= 0) {
            throw new IllegalArgumentException("Input should be greater than 0 [type=greater_than, input_value="
                    + defaultWindowRoundNum + ", input_type=int]");
        }
        if (contextWindowTokens != null && contextWindowTokens <= 0) {
            throw new IllegalArgumentException("Input should be greater than 0 [type=greater_than, input_value="
                    + contextWindowTokens + ", input_type=int]");
        }
    }
}
