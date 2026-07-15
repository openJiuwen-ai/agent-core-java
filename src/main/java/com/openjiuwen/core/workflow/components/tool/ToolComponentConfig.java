/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.tool;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.tool.ToolComponentConfig}
 * with a positional constructor for test compatibility.
 * 
 * @since 0.1.7
 */
public class ToolComponentConfig extends com.openjiuwen.core.workflow.component.tool.ToolComponentConfig {
    /**
     * ToolComponentConfig.
     * 
     * @param toolId toolId
     * @since 0.1.7
     */
    public ToolComponentConfig(String toolId) {
        super();
        setToolId(toolId);
    }

    /**
     * ToolComponentConfig.
     * 
     * @since 0.1.7
     */
    public ToolComponentConfig() {
        super();
    }
}
