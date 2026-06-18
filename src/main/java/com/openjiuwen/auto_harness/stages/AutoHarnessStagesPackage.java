/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import java.util.List;

/**
 * Package facade for built-in auto-harness stage exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages} in
 * {@code openjiuwen/auto_harness/stages/__init__.py}.</p>
 */
public final class AutoHarnessStagesPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/stages/__init__.py";
    public static final List<String> ALL = List.of(
            "AssessStage",
            "CommitStage",
            "DesignExtStage",
            "ExtendActivateStage",
            "ExtendAssessStage",
            "ExtendImplementStage",
            "ExtendPlanStage",
            "ExtendVerifyStage",
            "GapAnalysisStage",
            "ImplementExtStage",
            "ImplementStage",
            "LearningsStage",
            "MetaAssessStage",
            "MetaImplementStage",
            "MetaPlanStage",
            "MetaVerifyStage",
            "PlanStage",
            "PublishPRStage",
            "VerifyExtStage",
            "VerifyStage",
            "promote_runtime",
            "run_assess_stream",
            "run_implement_stream",
            "run_learnings",
            "run_plan_stream",
            "unload_extension"
    );

    private AutoHarnessStagesPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
