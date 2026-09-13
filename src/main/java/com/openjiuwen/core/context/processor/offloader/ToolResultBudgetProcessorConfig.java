/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Per-round budget control for large tool results.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultBudgetProcessorConfig {
    @Builder.Default
    private int tokensThreshold = 50000;

    @Builder.Default
    private int largeMessageThreshold = 10000;

    @Builder.Default
    private int trimSize = 3000;

    private List<String> toolNameAllowlist;

    @Builder.Default
    /**
     * List.of.
     * 
     * @since 0.1.7
     */
    private List<String> offloadMessageType = List.of("tool");

    @Builder.Default
    private String offloadFilePrefix = "ToolResultBudgetProcessor";

    private Integer messagesThreshold;

    private Integer messagesToKeep;

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0");
        }
        if (largeMessageThreshold <= 0) {
            throw new IllegalArgumentException("largeMessageThreshold must be > 0");
        }
        if (trimSize <= 0) {
            throw new IllegalArgumentException("trimSize must be > 0");
        }
        if (messagesThreshold != null && messagesThreshold <= 0) {
            throw new IllegalArgumentException("messagesThreshold must be > 0");
        }
        if (messagesToKeep != null && messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0");
        }
    }
}
