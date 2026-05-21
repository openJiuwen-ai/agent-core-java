/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

import java.util.*;

/**
 * Tool usage tracker for CLI mode.
 * <p>
 * Mirrors Python's {@code ToolTracker} in
 * {@code openjiuwen.harness.cli.rails.tool_tracker}.
 */
public class ToolTracker {

    private final Map<String, Integer> callCounts = new LinkedHashMap<>();
    private final List<String> callHistory = new ArrayList<>();

    public void recordCall(String toolName) {
        callCounts.merge(toolName, 1, Integer::sum);
        callHistory.add(toolName);
    }

    public int getCallCount(String toolName) {
        return callCounts.getOrDefault(toolName, 0);
    }

    public Map<String, Integer> getAllCallCounts() {
        return Collections.unmodifiableMap(callCounts);
    }

    public List<String> getCallHistory() {
        return Collections.unmodifiableList(callHistory);
    }

    public int getTotalCalls() {
        return callHistory.size();
    }

    public void reset() {
        callCounts.clear();
        callHistory.clear();
    }
}
