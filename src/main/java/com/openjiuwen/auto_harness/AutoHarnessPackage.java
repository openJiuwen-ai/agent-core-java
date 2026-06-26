/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;

import java.util.List;

/**
 * Public facade for auto-harness package exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness} in
 * {@code openjiuwen/auto_harness/__init__.py}.</p>
 */
public final class AutoHarnessPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/__init__.py";
    public static final String PIPELINE_PREFERENCE_AUTO = AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO;
    public static final List<String> ALL = List.of(
            "AutoHarnessConfig",
            "AutoHarnessPaths",
            "AutoHarnessOrchestrator",
            "CycleResult",
            "Experience",
            "Gap",
            "OptimizationTask",
            "PIPELINE_PREFERENCE_AUTO",
            "PipelineRegistry",
            "PipelineSpec",
            "ResearchContext",
            "StageRegistry",
            "StageSpec",
            "create_auto_harness_orchestrator",
            "normalize_pipeline_preference"
    );

    private AutoHarnessPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }

    public static String normalizePipelinePreference(Object value) {
        return AutoHarnessSchema.normalizePipelinePreference(value);
    }

    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config) {
        return AutoHarnessOrchestrator.createAutoHarnessOrchestrator(config);
    }
}
