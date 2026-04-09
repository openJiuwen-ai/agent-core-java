  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * Minimal tool registry contract required by {@link ToolCallOperator}.
 */
public interface ToolRegistry {

    default List<Map<String, Object>> getToolDefs() {
        return List.of();
    }

    default Map<String, Tool> getTools() {
        return Map.of();
    }

    void setToolDescription(String toolName, String description);
}
