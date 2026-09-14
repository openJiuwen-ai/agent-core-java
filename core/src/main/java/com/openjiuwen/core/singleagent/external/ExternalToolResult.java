/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExternalToolResult {
    private final String toolCallId;
    private final Object result;
    private final String error;

    public ExternalToolResult(String toolCallId, Object result, String error) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("External tool result toolCallId must not be blank.");
        }
        this.toolCallId = toolCallId;
        this.result = result;
        this.error = error;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public Object getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public static List<ExternalToolResult> fromInput(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("external_tool_results must be a list.");
        }
        List<ExternalToolResult> results = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("external_tool_results items must be maps.");
            }
            Object id = map.get("tool_call_id");
            if (!(id instanceof String toolCallId) || toolCallId.isBlank()) {
                throw new IllegalArgumentException("external_tool_results items must include tool_call_id.");
            }
            boolean hasResult = map.containsKey("result");
            boolean hasError = map.containsKey("error");
            if (!hasResult && !hasError) {
                throw new IllegalArgumentException("external_tool_results items must include result or error.");
            }
            Object result = hasResult ? map.get("result") : "";
            Object errorValue = map.get("error");
            String error = errorValue == null ? null : String.valueOf(errorValue);
            results.add(new ExternalToolResult(toolCallId, result, error));
        }
        return List.copyOf(results);
    }
}
