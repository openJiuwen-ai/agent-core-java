/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.contexts} in
 * {@code openjiuwen/auto_harness/contexts/__init__.py}.
 */
class AutoHarnessContextsPackageTest {

    @Test
    void exportsExecutionContextNamesInPythonAllOrder() {
        assertEquals("openjiuwen/auto_harness/contexts/__init__.py", AutoHarnessContextsPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "BaseExecutionContext",
                "SessionContext",
                "TaskContext",
                "TaskRuntime",
                "task_key"
        ), AutoHarnessContextsPackage.ALL);
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertTrue(AutoHarnessContextsPackage.exports("BaseExecutionContext"));
        assertTrue(AutoHarnessContextsPackage.exports("task_key"));
        assertFalse(AutoHarnessContextsPackage.exports("ExecutionContext"));
    }
}
