/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code openjiuwen/dev_tools/prompt_builder/builder/__init__.py}.
 */
class PromptBuilderBuilderPackageTest {
    @Test
    void exposesExactPythonModulePath() {
        assertEquals(
                "openjiuwen/dev_tools/prompt_builder/builder/__init__.py",
                PromptBuilderBuilderPackage.PYTHON_MODULE
        );
    }
}
