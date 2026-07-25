/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for the {@link MicroCompactProcessor} ContextProcessor.
 * <p>
 * Mirrors Python's {@code MicroCompactProcessorConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicroCompactProcessorConfig {
    @Builder.Default
    private int triggerThreshold = 5;

    @Builder.Default
    private List<String> compactableToolNames = List.of("grep", "glob", "read_file", "web_search", "web_fetch");

    @Builder.Default
    private int keepRecentPerTool = 15;

    @Builder.Default
    private String clearedMarker = "[Old tool result content cleared]";

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        if (triggerThreshold <= 0) {
            throw new IllegalArgumentException("triggerThreshold must be > 0, got " + triggerThreshold);
        }
        if (keepRecentPerTool < 0) {
            throw new IllegalArgumentException("keepRecentPerTool must be >= 0, got " + keepRecentPerTool);
        }
    }
}
