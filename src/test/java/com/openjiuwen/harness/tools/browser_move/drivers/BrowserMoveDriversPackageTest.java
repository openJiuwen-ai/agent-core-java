/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.drivers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen/harness/tools/browser_move/drivers/__init__.py}.
 */
class BrowserMoveDriversPackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals(
                "openjiuwen/harness/tools/browser_move/drivers/__init__.py",
                BrowserMoveDriversPackage.PYTHON_MODULE
        );
    }
}
