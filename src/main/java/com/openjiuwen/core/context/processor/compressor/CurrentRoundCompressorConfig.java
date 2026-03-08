/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor.compressor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Configuration for the {@link CurrentRoundCompressor} ContextProcessor.
 * <p>
 * Mirrors Python's {@code CurrentRoundCompressorConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentRoundCompressorConfig {

    /**
     * Maximum number of messages allowed before compression is triggered.
     */
    private Integer messagesThreshold;

    /**
     * Maximum accumulated token count before compression is triggered.
     */
    @Builder.Default
    private int tokensThreshold = 10000;

    /**
     * Number of most-recent messages to retain, regardless of thresholds.
     */
    private Integer messagesToKeep;

    /**
     * Token count above which a single message is considered 'large'.
     */
    @Builder.Default
    private int largeMessageThreshold = 1000;

    /**
     * User-supplied prompt for compression; falls back to built-in prompt if null.
     */
    private String customizedCompressionPrompt;

    /**
     * Switch between single-message and whole-block compression.
     * false (default) → compress only individual messages exceeding token limit.
     * true → compress the entire contiguous message block as one unit.
     */
    @Builder.Default
    private boolean singleMultiCompression = false;

    /**
     * Model request configuration.
     */
    private ModelRequestConfig model;

    /**
     * Optional client-level configuration for the model.
     */
    private ModelClientConfig modelClient;
}
