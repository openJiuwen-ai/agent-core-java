/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.tool;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.tool.ToolComponentConfig}
 * with a positional constructor for test compatibility.
 *
 * <p>Mirrors Python's {@code ToolComponentConfig} in
 * {@code openjiuwen/core/workflow/components/tool/tool_comp.py}.</p>
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
