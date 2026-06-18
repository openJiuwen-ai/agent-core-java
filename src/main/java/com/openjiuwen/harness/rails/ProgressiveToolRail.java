/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks tool-call progression hints.
 *
 * <p>Mirrors Python's {@code ProgressiveToolRail} in
 * {@code openjiuwen/harness/rails/progressive_tool_rail.py}.</p>
 */
public class ProgressiveToolRail extends DeepAgentRail {

    private final Map<String, Integer> toolUseCounts = new LinkedHashMap<>();

    public ProgressiveToolRail() {
        setPriority(60);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        String name = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if (!name.isBlank()) {
            toolUseCounts.merge(name, 1, Integer::sum);
        }
        ctx.put("tool_use_counts", new LinkedHashMap<>(toolUseCounts));
    }

    public Map<String, Integer> getToolUseCounts() {
        return new LinkedHashMap<>(toolUseCounts);
    }
}
