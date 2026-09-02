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
 * Configuration for the {@link DialogueCompressor} ContextProcessor.
 * <p>
 * Mirrors Python's {@code DialogueCompressorConfig}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueCompressorConfig {
    private Integer messagesThreshold;

    /**
     * Maximum accumulated token count before compression is triggered.
     */
    @Builder.Default
    private int tokensThreshold = 10000;

    /**
     * Number of most-recent messages to retain regardless of thresholds.
     */
    private Integer messagesToKeep;

    /**
     * If true, the most recent user-assistant round is always preserved.
     */
    @Builder.Default
    private boolean keepLastRound = true;

    /**
     * Per-block summary size hint used in the compression prompt.
     */
    @Builder.Default
    private int compressionTargetTokens = 1800;

    /**
     * User-supplied prompt for the compression step.
     */
    private String customCompressionPrompt;

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
     * 
     * @since 0.1.7
     */
    public void validate() {
        if (messagesThreshold != null && messagesThreshold <= 0) {
            throw new IllegalArgumentException("messagesThreshold must be > 0, got " + messagesThreshold);
        }
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0, got " + tokensThreshold);
        }
        if (messagesToKeep != null && messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0, got " + messagesToKeep);
        }
        if (compressionTargetTokens <= 0) {
            throw new IllegalArgumentException("compressionTargetTokens must be > 0, got " + compressionTargetTokens);
        }
    }
}
