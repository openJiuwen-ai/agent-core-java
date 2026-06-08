/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.dataset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code openjiuwen/dev_tools/tune/dataset/__init__.py}.
 */
class TuneDatasetPackageTest {
    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/dev_tools/tune/dataset/__init__.py", TuneDatasetPackage.PYTHON_MODULE);
    }
}
