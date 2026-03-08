/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor.offloader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for the {@link MessageOffloader} ContextProcessor.
 * <p>
 * The offloader keeps conversation history within safe memory/token limits
 * by trimming or offloading messages once thresholds are exceeded.
 * <p>
 * Mirrors Python's {@code MessageOffloaderConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageOffloaderConfig {

    /**
     * Maximum number of messages allowed before offloading is triggered.
     */
    private Integer messagesThreshold;

    /**
     * Maximum accumulated token count before offloading is triggered.
     */
    @Builder.Default
    private int tokensThreshold = 20000;

    /**
     * Messages whose token count exceeds this value are considered 'large'.
     */
    @Builder.Default
    private int largeMessageThreshold = 1000;

    /**
     * Roles eligible for offloading (e.g., "user", "assistant", "tool").
     */
    @Builder.Default
    private List<String> offloadMessageType = List.of("tool");

    /**
     * Number of tokens to retain when a message is offloaded.
     */
    @Builder.Default
    private int trimSize = 100;

    /**
     * Number of most-recent messages to retain regardless of thresholds.
     */
    private Integer messagesToKeep;

    /**
     * If true, the most recent user-assistant round is always preserved.
     */
    @Builder.Default
    private boolean keepLastRound = true;
}
