/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness} in
 * {@code openjiuwen/auto_harness/__init__.py}.
 */
class AutoHarnessPackageTest {

    @TempDir
    private Path tempDir;

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(AutoHarnessPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/auto_harness/__init__.py");
        assertThat(AutoHarnessPackage.ALL).containsExactly(
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
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertThat(AutoHarnessPackage.exports("AutoHarnessConfig")).isTrue();
        assertThat(AutoHarnessPackage.exports("normalize_pipeline_preference")).isTrue();
        assertThat(AutoHarnessPackage.exports("load_auto_harness_config")).isFalse();
    }

    @Test
    void delegatesNewPipelinePreferenceExportsToSchema() {
        assertThat(AutoHarnessPackage.PIPELINE_PREFERENCE_AUTO)
                .isEqualTo(AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO);
        assertThat(AutoHarnessPackage.normalizePipelinePreference("meta"))
                .isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(AutoHarnessPackage.normalizePipelinePreference("extended_harness_pipeline"))
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(AutoHarnessPackage.normalizePipelinePreference("unknown"))
                .isEqualTo(AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO);
    }

    @Test
    void delegatesOrchestratorFactory() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());

        AutoHarnessOrchestrator orchestrator = AutoHarnessPackage.createAutoHarnessOrchestrator(config);

        assertThat(orchestrator.getConfig()).isSameAs(config);
    }
}
