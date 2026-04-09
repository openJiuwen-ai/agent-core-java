  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.context.processor.compressor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Configuration for the {@link RoundLevelCompressor} ContextProcessor.
 * <p>
 * Mirrors Python's {@code RoundLevelCompressorConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundLevelCompressorConfig {

    /**
     * Maximum number of consecutive dialogue rounds before compression is triggered.
     * Must be > 1.
     */
    @Builder.Default
    private int roundsThreshold = 10;

    /**
     * Maximum accumulated token count before compression is triggered.
     * Must be > 0.
     */
    @Builder.Default
    private int tokensThreshold = 10000;

    /**
     * If true, the most recent user-assistant round is always preserved.
     */
    @Builder.Default
    private boolean keepLastRound = true;

    /**
     * User-defined prompt template for round compression.
     */
    private String customizedCompressionPrompt;

    /**
     * Model request configuration.
     */
    private ModelRequestConfig model;

    /**
     * Optional client-level configuration for the model.
     */
    private ModelClientConfig modelClient;

    /**
     * Validate configuration constraints matching Python Pydantic rules.
     */
    public void validate() {
        if (roundsThreshold <= 1) {
            throw new IllegalArgumentException("roundsThreshold must be > 1, got " + roundsThreshold);
        }
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0, got " + tokensThreshold);
        }
    }
}
