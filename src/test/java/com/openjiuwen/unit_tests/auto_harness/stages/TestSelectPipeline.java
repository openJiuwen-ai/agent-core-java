/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PipelineSelectionArtifact;
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
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.stages.test_select_pipeline}.</p>
 */
class TestSelectPipeline {
    @Test
    void testExplicitTaskPipelineWins() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setModel(new Object());
        OptimizationTask task = new OptimizationTask("t1");
        task.setPipelineName(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);

        PipelineSelectionArtifact result = SelectPipelineStage.runSelectPipeline(config, task);

        assertEquals(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE, result.getPipelineName());
        assertEquals(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE, result.getFallbackPipeline());
        assertEquals(1.0, result.getConfidence());
    }

    @Test
    void testNoModelFallsBackToPr() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setModel(null);
        OptimizationTask task = new OptimizationTask("t1");

        PipelineSelectionArtifact result = SelectPipelineStage.runSelectPipeline(config, task);

        assertEquals(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE, result.getPipelineName());
        assertEquals(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE, result.getFallbackPipeline());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void testRunSelectPipelineStreamBuildsQuery() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setModel(new Object());
        OptimizationTask task = new OptimizationTask("t1");
        task.setDescription("desc");
        task.setFiles(List.of("a.py", "b.py"));
        AtomicReference<Map<String, Object>> capturedInputs = new AtomicReference<>();

        Iterator<Object> chunks = SelectPipelineStage.runSelectPipelineStream(
                config,
                task,
                "  assessment  ",
                List.of(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE),
                ignored -> inputs -> {
                    capturedInputs.set(inputs);
                    return List.of((Object) "chunk").iterator();
                }
        );

        assertEquals("chunk", chunks.next());
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
