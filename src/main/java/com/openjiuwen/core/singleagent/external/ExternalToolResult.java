/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.external;

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
}
