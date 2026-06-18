/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.registry} in
 * {@code openjiuwen/auto_harness/registry/__init__.py}.
 */
class AutoHarnessRegistryPackageTest {

    @Test
    void pythonModulePathIsPreserved() {
        assertThat(AutoHarnessRegistryPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/auto_harness/registry/__init__.py");
    }

    @Test
    void exportsMatchPythonAll() {
        assertThat(AutoHarnessRegistryPackage.ALL).containsExactly(
                "PipelineRegistry",
                "StageRegistry",
                "build_pipeline_registry",
                "build_stage_registry",
                "register_builtin_stages"
        );
        assertThat(AutoHarnessRegistryPackage.exports("PipelineRegistry")).isTrue();
        assertThat(AutoHarnessRegistryPackage.exports("register_builtin_stages")).isTrue();
        assertThat(AutoHarnessRegistryPackage.exports("unknown")).isFalse();
    }
}
