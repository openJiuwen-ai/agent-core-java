/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's package surface in
 * {@code openjiuwen/core/operator/tool_call/__init__.py}.
 */
class ToolCallPackageTest {

    @Test
    void exposesPythonPackageBridge() {
        assertEquals(
                "openjiuwen/core/operator/tool_call/__init__.py",
                ToolCallPackage.PYTHON_MODULE
        );
        assertEquals(
                "Tool invocation operator: ToolCallOperator with enabled/retries tunables.",
                ToolCallPackage.DESCRIPTION
        );
        assertEquals(List.of("ToolCallOperator"), ToolCallPackage.EXPORTED_SYMBOLS);
        assertSame(ToolCallOperator.class, ToolCallPackage.TOOL_CALL_OPERATOR);
    }
}
