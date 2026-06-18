/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSelectorTest {

    @Test
    void detectsCompetitorSignalFromTaskText() {
        String signal = PipelineSelector.detectPipelineSignal(
                List.of(OptimizationTask.builder()
                        .topic("吸收 hermes 的动态能力创建机制")
                        .build()),
                new AutoHarnessConfig()
        );

        assertThat(signal).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
    }

    @Test
    void detectsCompetitorSignalFromConfigGoal() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setOptimizationGoal("cursor");

        String signal = PipelineSelector.detectPipelineSignal(
                List.of(OptimizationTask.builder().topic("优化 harness").build()),
                config
        );

        assertThat(signal).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
    }

    @Test
    void chooseSessionPipelinePrefersSignal() {
        PipelineSelectionArtifact result = PipelineSelector.chooseSessionPipeline(
                List.of(OptimizationTask.builder().topic("吸收 hermes 的主动审查能力").build()),
                new AutoHarnessConfig(),
                List.of(
                        AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                        AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
                )
        );

        assertThat(result.getPipelineName()).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(result.getReason()).contains("signal");
    }

    @Test
    void configMetaPreferenceWinsOverSignal() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setPipelinePreference(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);

        PipelineSelectionArtifact result = PipelineSelector.chooseSessionPipeline(
                List.of(OptimizationTask.builder().topic("吸收 hermes").build()),
                config,
                List.of(
                        AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                        AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
                )
        );

        assertThat(result.getPipelineName()).isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(result.getReason()).contains("config");
    }

    @Test
    void configExtendedPreferenceWinsForPlainTask() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setPipelinePreference(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);

        PipelineSelectionArtifact result = PipelineSelector.chooseSessionPipeline(
                List.of(OptimizationTask.builder().topic("优化 harness").build()),
                config,
                List.of(
                        AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                        AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
                )
        );

        assertThat(result.getPipelineName()).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(result.getReason()).contains("config");
    }

    @Test
    void autoPlainTaskDefaultsToMeta() {
        PipelineSelectionArtifact result = PipelineSelector.chooseSessionPipeline(
                List.of(OptimizationTask.builder().topic("优化 harness").build()),
                new AutoHarnessConfig(),
                List.of(
                        AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                        AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
                )
        );

        assertThat(result.getPipelineName()).isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
    }
}
