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
 * Configuration for {@link MicroCompactProcessor}.
 * <p>
 * Mirrors Python's {@code MicroCompactProcessorConfig} from
 * {@code context_engine/processor/compressor/micro_compact_processor.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicroCompactProcessorConfig {

    /** Marker text used when clearing stale tool result content. */
    public static final String DEFAULT_CLEARED_MARKER = "[Old tool result content cleared]";

    /**
     * Clear stale results only after a tool has more than this many
     * clearable results beyond the kept tail.
     */
    @Builder.Default
    private int triggerThreshold = 5;

    /**
     * Tool names whose older ToolMessage contents may be cleared.
     */
    @Builder.Default
    private List<String> compactableToolNames = List.of(
            "grep", "glob", "read_file", "web_search", "web_fetch");

    /**
     * Number of most-recent ToolMessage contents preserved for each compactable tool.
     */
    @Builder.Default
    private int keepRecentPerTool = 15;

    /**
     * Replacement content used when clearing stale ToolMessage contents.
     */
    @Builder.Default
    private String clearedMarker = DEFAULT_CLEARED_MARKER;
}
