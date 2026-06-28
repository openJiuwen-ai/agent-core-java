/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.external;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExternalToolCallRequest {
    private final String toolCallId;
    private final String toolName;
    private final String arguments;

    public ExternalToolCallRequest(String toolCallId, String toolName, String arguments) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("External tool call toolCallId must not be blank.");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("External tool call toolName must not be blank.");
        }
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.arguments = arguments == null ? "" : arguments;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tool_call_id", toolCallId);
        item.put("tool_name", toolName);
        item.put("arguments", arguments);
        return item;
    }
}
