/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * Minimal tool registry contract required by {@link ToolCallOperator}.
 * 
 * @since 0.1.7
 */
public interface ToolRegistry {
    /**
     * getToolDefs.
     * 
     * @return the result
     * @since 0.1.7
     */
    default List<Map<String, Object>> getToolDefs() {
        return List.of();
    }

    /**
     * getTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    default Map<String, Tool> getTools() {
        return Map.of();
    }

    /**
     * setToolDescription.
     * 
     * @param toolName toolName
     * @param description description
     * @since 0.1.7
     */
    void setToolDescription(String toolName, String description);
}
