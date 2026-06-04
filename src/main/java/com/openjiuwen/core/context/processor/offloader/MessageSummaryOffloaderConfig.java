/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;

/**
 * Configuration for the {@link MessageSummaryOffloader} ContextProcessor.
 * <p>
 * Mirrors Python's {@code MessageSummaryOffloaderConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSummaryOffloaderConfig {

    /**
     * Hard ceiling on message count.
     */
    private Integer messagesThreshold;

    /**
     * Hard ceiling on accumulated tokens.
     */
    @Builder.Default
    private int tokensThreshold = 20000;

    /**
     * Token length above which a single message is labelled large.
     */
    @Builder.Default
    private int largeMessageThreshold = 1000;

    /**
     * White-list of roles that may be compressed or off-loaded.
     */
    @Builder.Default
    private List<String> offloadMessageType = List.of("tool");

    /**
     * Tool names or {@code tool_name:glob_pattern} entries that should never be
     * summarized/offloaded.
     */
    @Builder.Default
    private List<String> protectedToolNames = List.of("reload_original_context_messages");

    /**
     * Guarantee that the newest N messages are never off-loaded.
     */
    private Integer messagesToKeep;

    /**
     * If true, the latest user-assistant round is immune to off-loading.
     */
    @Builder.Default
    private boolean keepLastRound = true;

    /**
     * Model request configuration.
     */
    private ModelRequestConfig model;

    /**
     * Optional client-level configuration.
     */
    private ModelClientConfig modelClient;

    /**
     * User-supplied prompt for the summary model.
     */
    private String customizedSummaryPrompt;

    /**
     * Maximum tokens requested for an adaptive summary.
     */
    @Builder.Default
    private int summaryMaxTokens = 900;

    /**
     * Whether to use an extra precise-step extraction call before compression.
     */
    @Builder.Default
    private boolean enablePreciseStep = false;

    /**
     * Maximum recent context messages used for precise step extraction.
     */
    @Builder.Default
    private int stepSummaryMaxContextMessages = 8;

    /**
     * Maximum original content characters sent to the compression model before fallback truncation.
     */
    @Builder.Default
    private int contentMaxCharsForCompression = 200000;

    /**
     * Validate configuration constraints matching Python Pydantic {@code Field(gt=0)} rules.
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
