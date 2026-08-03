/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen/harness/workspace/__init__.py}.
 */
class WorkspacePackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/harness/workspace/__init__.py", WorkspacePackage.PYTHON_MODULE);
    }
}
