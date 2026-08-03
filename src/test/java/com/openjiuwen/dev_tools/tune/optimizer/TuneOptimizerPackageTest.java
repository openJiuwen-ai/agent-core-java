/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code openjiuwen/dev_tools/tune/optimizer/__init__.py}.
 */
class TuneOptimizerPackageTest {
    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/dev_tools/tune/optimizer/__init__.py", TuneOptimizerPackage.PYTHON_MODULE);
    }
}
