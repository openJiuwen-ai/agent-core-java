/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for {@link MicroCompactProcessor}.
 *
 * <p>Mirrors Python's {@code MicroCompactProcessorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/micro_compact_processor.py}.</p>
 */
public class MicroCompactProcessorConfig {
    public static final String DEFAULT_CLEARED_MARKER = "[Old tool result content cleared]";

    @JsonProperty("trigger_threshold")
    private int triggerThreshold = 5;

    @JsonProperty("compactable_tool_names")
    private List<String> compactableToolNames = List.of("grep", "glob", "read_file", "web_search", "web_fetch");

    @JsonProperty("keep_recent_per_tool")
    private int keepRecentPerTool = 15;

    @JsonProperty("cleared_marker")
    private String clearedMarker = DEFAULT_CLEARED_MARKER;

    public int getTriggerThreshold() {
        return triggerThreshold;
    }

    public void setTriggerThreshold(int triggerThreshold) {
        if (triggerThreshold <= 0) {
            throw new IllegalArgumentException("trigger_threshold must be > 0");
        }
        this.triggerThreshold = triggerThreshold;
    }

    public List<String> getCompactableToolNames() {
        return new ArrayList<>(compactableToolNames);
    }

    public void setCompactableToolNames(List<String> compactableToolNames) {
        if (compactableToolNames == null) {
            throw new IllegalArgumentException("compactable_tool_names must not be null");
        }
        this.compactableToolNames = new ArrayList<>(compactableToolNames);
    }

    public int getKeepRecentPerTool() {
        return keepRecentPerTool;
    }

    public void setKeepRecentPerTool(int keepRecentPerTool) {
        if (keepRecentPerTool < 0) {
            throw new IllegalArgumentException("keep_recent_per_tool must be >= 0");
        }
        this.keepRecentPerTool = keepRecentPerTool;
    }

    public String getClearedMarker() {
        return clearedMarker;
    }

    public void setClearedMarker(String clearedMarker) {
        if (clearedMarker == null) {
            throw new IllegalArgumentException("cleared_marker must not be null");
        }
        this.clearedMarker = clearedMarker;
    }
}
