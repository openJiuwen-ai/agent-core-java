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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundLevelCompressorConfig {

    @Builder.Default
    private int triggerTotalTokens = 230000;

    @Builder.Default
    private int targetTotalTokens = 160000;

    @Builder.Default
    private int keepRecentMessages = 0;

    @Builder.Default
    private int compressionCallMaxTokens = 250000;

    @Builder.Default
    private int firstPassTargetTokens = 30000;

    @Builder.Default
    private int secondPassTargetTokens = 20000;

    @Builder.Default
    private int thirdPassTargetTokens = 10000;

    @Builder.Default
    private double truncateHeadRatio = 0.2;

    @Builder.Default
    private String truncatedMarker = "...[TRUNCATED]...";

    @Builder.Default
    private String compressionMarker = RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER;

    private ModelRequestConfig model;

    private ModelClientConfig modelClient;

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        if (triggerTotalTokens <= 0) {
            throw new IllegalArgumentException("triggerTotalTokens must be > 0, got " + triggerTotalTokens);
        }
        if (targetTotalTokens <= 0) {
            throw new IllegalArgumentException("targetTotalTokens must be > 0, got " + targetTotalTokens);
        }
        if (keepRecentMessages < 0) {
            throw new IllegalArgumentException("keepRecentMessages must be >= 0, got " + keepRecentMessages);
        }
        if (compressionCallMaxTokens <= 0) {
            throw new IllegalArgumentException("compressionCallMaxTokens must be > 0, got " + compressionCallMaxTokens);
        }
        if (firstPassTargetTokens <= 0) {
            throw new IllegalArgumentException("firstPassTargetTokens must be > 0, got " + firstPassTargetTokens);
        }
        if (secondPassTargetTokens <= 0) {
            throw new IllegalArgumentException("secondPassTargetTokens must be > 0, got " + secondPassTargetTokens);
        }
        if (thirdPassTargetTokens <= 0) {
            throw new IllegalArgumentException("thirdPassTargetTokens must be > 0, got " + thirdPassTargetTokens);
        }
        if (truncateHeadRatio <= 0.0 || truncateHeadRatio >= 1.0) {
            throw new IllegalArgumentException("truncateHeadRatio must be > 0 and < 1, got " + truncateHeadRatio);
        }
    }
}
