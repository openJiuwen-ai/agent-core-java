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
 * <p>
 * This processor does not use the base {@link MessageOffloader} trigger/range logic.
 * It keeps {@code messagesThreshold} and {@code messagesToKeep} only as compatibility
 * placeholders for callers that handle offloader-like configs generically.
 * <p>
 * Mirrors Python's {@code ToolResultBudgetProcessorConfig} from
 * {@code context_engine/processor/offloader/tool_result_budget_processor.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultBudgetProcessorConfig {

    /**
     * Maximum accumulated tool-result tokens allowed in one dialogue round.
     */
    @Builder.Default
    private int tokensThreshold = 50000;

    /**
     * Minimum size for a single tool message to be eligible for offload.
     */
    @Builder.Default
    private int largeMessageThreshold = 10000;

    /**
     * Number of leading characters kept in the context placeholder after offloading.
     */
    @Builder.Default
    private int trimSize = 3000;

    /**
     * Tool names protected from offloading. {@code null} to allow all tools.
     */
    private List<String> toolNameAllowlist;

    /**
     * Compatibility field. Only tool messages are supported by this processor.
     */
    @Builder.Default
    private List<String> offloadMessageType = List.of("tool");

    /**
     * File prefix used when persisting offloaded tool results to disk.
     */
    @Builder.Default
    private String offloadFilePrefix = "ToolResultBudgetProcessor";

    /**
     * Validate configuration constraints.
     *
     * @throws IllegalArgumentException if any constraint is violated
     */
    public void validate() {
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException(
                    "tokensThreshold must be > 0, got " + tokensThreshold);
        }
        if (largeMessageThreshold <= 0) {
            throw new IllegalArgumentException(
                    "largeMessageThreshold must be > 0, got " + largeMessageThreshold);
        }
        if (trimSize <= 0) {
            throw new IllegalArgumentException(
                    "trimSize must be > 0, got " + trimSize);
        }
    }
}
