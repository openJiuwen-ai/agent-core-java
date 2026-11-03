/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Tool workflow component (composable wrapper).
 * <p>
 * Binds a {@link Tool} and creates a {@link ToolExecutable} for graph execution.
 * <p>
 * Mirrors Python's {@code ToolComponent} in
 * {@code openjiuwen/core/workflow/components/tool/tool_comp.py}.
 */
public class ToolComponent implements ComponentComposable {

    private final ToolComponentConfig config;
    private Tool tool;

    public ToolComponent(ToolComponentConfig config) {
        this.config = config;
        // If toolId is set, tool lookup would happen via Runner.resourceMgr.getTool()
        // in the full framework. For now we support explicit binding.
    }

    @Override
    public Executable<?, ?> toExecutable() {
        Tool executableTool = tool != null ? tool : resolveToolById();
        if (executableTool == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_INIT_FAILED,
                    "error_msg", "tool component not bind a valid tool");
        }
        return new ToolExecutable(config).setTool(executableTool);
    }

    /**
     * Bind a tool instance to this component.
     */
    public ToolComponent bindTool(Tool tool) {
        this.tool = tool;
        return this;
    }

    private Tool resolveToolById() {
        if (config == null || config.getToolId() == null || config.getToolId().isBlank()) {
            return null;
        }
        try {
            Class<?> runnerClass = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceManager = runnerClass.getMethod("resourceMgr").invoke(null);
            Object resolved = resourceManager.getClass()
                    .getMethod("getTool", String.class)
                    .invoke(resourceManager, config.getToolId());
            return resolved instanceof Tool ? (Tool) resolved : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
