/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/__init__.py}.
 */
class ToolCallOptimizerPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals(
                "openjiuwen/agent_evolving/optimizer/tool_call/__init__.py",
                ToolCallOptimizerPackage.PYTHON_MODULE
        );
        assertEquals(List.of("ToolOptimizerBase"), ToolCallOptimizerPackage.EXPORTED_SYMBOLS);
        assertEquals(ToolOptimizerBase.class, ToolCallOptimizerPackage.TOOL_OPTIMIZER_BASE);
    }
}
