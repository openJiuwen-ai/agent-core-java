/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for adaptive summary offloading.
 * <p>
 * Mirrors Python's {@code MessageSummaryOffloaderConfig}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSummaryOffloaderConfig {
    private Integer messagesThreshold;

    @Builder.Default
    private int tokensThreshold = 20000;

    @Builder.Default
    private int largeMessageThreshold = 1000;

    @Builder.Default
    /**
     * List.of.
     * 
     * @since 0.1.7
     */
    private List<String> offloadMessageType = List.of("tool");

    @Builder.Default
    /**
     * List.of.
     * 
     * @since 0.1.7
     */
    private List<String> protectedToolNames = List.of("reload_original_context_messages");

    private Integer messagesToKeep;

    @Builder.Default
    private boolean keepLastRound = true;

    private ModelRequestConfig model;

    private ModelClientConfig modelClient;

    @Builder.Default
    private int summaryMaxTokens = 900;

    @Builder.Default
    private boolean enablePreciseStep = false;

    @Builder.Default
    private int stepSummaryMaxContextMessages = 8;

    @Builder.Default
    private int contentMaxCharsForCompression = 200000;

    /**
     * validate.
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
        if (largeMessageThreshold <= 0) {
            throw new IllegalArgumentException("largeMessageThreshold must be > 0, got " + largeMessageThreshold);
        }
        if (messagesToKeep != null && messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0, got " + messagesToKeep);
        }
        if (summaryMaxTokens <= 0) {
            throw new IllegalArgumentException("summaryMaxTokens must be > 0, got " + summaryMaxTokens);
        }
        if (stepSummaryMaxContextMessages <= 0) {
            throw new IllegalArgumentException(
                    "stepSummaryMaxContextMessages must be > 0, got " + stepSummaryMaxContextMessages);
        }
        if (contentMaxCharsForCompression <= 0) {
            throw new IllegalArgumentException(
                    "contentMaxCharsForCompression must be > 0, got " + contentMaxCharsForCompression);
        }
    }
}
