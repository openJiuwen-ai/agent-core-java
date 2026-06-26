/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import java.util.List;

/**
 * Package facade for auto-harness registry exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.registry} in
 * {@code openjiuwen/auto_harness/registry/__init__.py}.</p>
 */
public final class AutoHarnessRegistryPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/registry/__init__.py";
    public static final List<String> ALL = List.of(
            "PipelineRegistry",
            "StageRegistry",
            "build_pipeline_registry",
            "build_stage_registry",
            "register_builtin_stages"
    );

    private AutoHarnessRegistryPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
