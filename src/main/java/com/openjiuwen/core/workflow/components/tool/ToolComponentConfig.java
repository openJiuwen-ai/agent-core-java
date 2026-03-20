// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.workflow.components.tool;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.tool.ToolComponentConfig}
 * with a positional constructor for test compatibility.
 */
public class ToolComponentConfig
        extends com.openjiuwen.core.workflow.component.tool.ToolComponentConfig {

    /**
     * Positional constructor: ToolComponentConfig(toolId).
     */
    public ToolComponentConfig(String toolId) {
        super();
        setToolId(toolId);
    }

    public ToolComponentConfig() {
        super();
    }
}
