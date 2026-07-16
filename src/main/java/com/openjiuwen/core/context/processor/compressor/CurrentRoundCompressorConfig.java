/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for {@link CurrentRoundCompressor}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentRoundCompressorConfig {
    @Builder.Default
    private int tokensThreshold = 100000;

    @Builder.Default
    private int messagesToKeep = 3;

    private ModelRequestConfig model;

    private ModelClientConfig modelClient;

    @Builder.Default
    private int minSelectedTokensForCompression = 20000;

    @Builder.Default
    private int compressionTargetTokens = 4000;

    @Builder.Default
    private int summaryMergeTargetTokens = 4000;

    @Builder.Default
    private int accumulatedSummaryTokenLimit = 20000;

    @Builder.Default
    private int summaryMergeMinBlocks = 3;

    @Builder.Default
    private int priorContextWindowSize = 10;

    private String customCompressionPrompt;

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0, got " + tokensThreshold);
        }
        if (messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0, got " + messagesToKeep);
        }
        if (minSelectedTokensForCompression <= 0) {
            throw new IllegalArgumentException(
                    "minSelectedTokensForCompression must be > 0, got " + minSelectedTokensForCompression);
        }
        if (compressionTargetTokens <= 0) {
            throw new IllegalArgumentException("compressionTargetTokens must be > 0, got " + compressionTargetTokens);
        }
        if (summaryMergeTargetTokens <= 0) {
            throw new IllegalArgumentException("summaryMergeTargetTokens must be > 0, got " + summaryMergeTargetTokens);
        }
        if (accumulatedSummaryTokenLimit <= 0) {
            throw new IllegalArgumentException(
                    "accumulatedSummaryTokenLimit must be > 0, got " + accumulatedSummaryTokenLimit);
        }
        if (summaryMergeMinBlocks < 2) {
            throw new IllegalArgumentException("summaryMergeMinBlocks must be >= 2, got " + summaryMergeMinBlocks);
        }
        if (priorContextWindowSize <= 0) {
            throw new IllegalArgumentException("priorContextWindowSize must be > 0, got " + priorContextWindowSize);
        }
    }
}
