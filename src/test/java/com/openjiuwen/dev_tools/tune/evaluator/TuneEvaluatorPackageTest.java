/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.evaluator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code openjiuwen/dev_tools/tune/evaluator/__init__.py}.
 */
class TuneEvaluatorPackageTest {
    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/dev_tools/tune/evaluator/__init__.py", TuneEvaluatorPackage.PYTHON_MODULE);
    }
}
