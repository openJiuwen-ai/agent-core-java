/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call;

import java.util.List;

/**
 * Tool-call optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/__init__.py}.</p>
 */
public final class ToolCallOptimizerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/optimizer/tool_call/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of("ToolOptimizerBase");

    public static final Class<ToolOptimizerBase> TOOL_OPTIMIZER_BASE = ToolOptimizerBase.class;

    private ToolCallOptimizerPackage() {
    }
}
