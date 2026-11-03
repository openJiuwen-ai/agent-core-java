/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;
import com.openjiuwen.auto_harness.stages.SelectPipelineStage;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Select pipeline stage parity tests.
 *
 * <p>Mirrors Python's {@code TestSelectPipelineStage} in
 * {@code tests/unit_tests/auto_harness/stages/test_select_pipeline.py}.</p>
 */
class TestSelectPipeline {

    @Test
    void testExplicitTaskPipelineWins() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setPipelinePreference(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        OptimizationTask task = OptimizationTask.builder().topic("t1").build();

        PipelineSelectionArtifact result = SelectPipelineStage.runSelectPipeline(config, task);

        assertEquals(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE, result.getPipelineName());
    }

    @Test
    void testNoModelDefaultsToMetaPipeline() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setModel(null);
        OptimizationTask task = OptimizationTask.builder().topic("t1").build();

        PipelineSelectionArtifact result = SelectPipelineStage.runSelectPipeline(config, task);

        assertEquals(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE, result.getPipelineName());
    }

    @Test
    void testRunSelectPipelineStreamBuildsQuery() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        OptimizationTask task = OptimizationTask.builder()
                .topic("t1")
                .description("desc")
                .files(List.of("a.py", "b.py"))
                .build();
        AtomicReference<Map<String, Object>> capturedInputs = new AtomicReference<>();

        Iterator<Map<String, Object>> chunks = SelectPipelineStage.runSelectPipelineStream(
                config,
                task,
                "  assessment  ",
                List.of(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE),
                ignored -> inputs -> {
                    capturedInputs.set(inputs);
                    return List.of(Map.of("chunk", (Object) "ok")).iterator();
                }
        );

        assertEquals(Map.of("chunk", "ok"), chunks.next());
        assertEquals(
                "任务主题: t1\n"
                        + "任务描述: desc\n"
                        + "目标文件: a.py, b.py\n"
                        + "评估摘要:\n"
                        + "assessment\n\n"
                        + "可选 pipeline:\n"
                        + "- " + AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                capturedInputs.get().get("query")
        );
    }
}
