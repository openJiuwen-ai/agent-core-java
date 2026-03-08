/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
}
