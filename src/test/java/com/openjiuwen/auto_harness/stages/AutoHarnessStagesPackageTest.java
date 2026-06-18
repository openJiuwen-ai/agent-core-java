/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.stages} in
 * {@code openjiuwen/auto_harness/stages/__init__.py}.
 */
class AutoHarnessStagesPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(AutoHarnessStagesPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/auto_harness/stages/__init__.py");
        assertThat(AutoHarnessStagesPackage.ALL).containsExactly(
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
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertThat(AutoHarnessStagesPackage.exports("GapAnalysisStage")).isTrue();
        assertThat(AutoHarnessStagesPackage.exports("unload_extension")).isTrue();
        assertThat(AutoHarnessStagesPackage.exports("SelectPipelineStage")).isFalse();
    }
}
