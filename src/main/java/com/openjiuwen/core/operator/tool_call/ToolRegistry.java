/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.session.Session;

/**
 * Registry interface for tool description overrides.
 *
 * @since 0.1.7
 */
public interface ToolRegistry {

    /**
     * Override a registered tool description in memory.
     *
     * @param toolName    tool name
     * @param description replacement description
     */
    void setToolDescription(String toolName, String description);
}
