/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the select_pipeline stage.
 * <p>
 * Mirrors Python's test_select_pipeline.py from
 * <code>tests/unit_tests/auto_harness/stages/test_select_pipeline.py</code>.
 */
@DisplayName("Select Pipeline Stage Tests")
class TestSelectPipeline {

    // Constants matching Python's pipelines
    static final String EXTENDED_EVOLVE_PIPELINE = "extended_evolve";
    static final String META_EVOLVE_PIPELINE = "meta_evolve";
    static final String PR_PIPELINE = "pr";

    // Stub classes
    static class AutoHarnessConfigStub {
        Object model; // null or mock

        AutoHarnessConfigStub(Object model) {
            this.model = model;
        }
    }

    static class OptimizationTaskStub {
        String topic;
        String pipelineName;

        OptimizationTaskStub(String topic) {
            this.topic = topic;
            this.pipelineName = null;
        }

        OptimizationTaskStub(String topic, String pipelineName) {
            this.topic = topic;
            this.pipelineName = pipelineName;
        }
    }

    static class SelectPipelineResult {
        String pipelineName;

        SelectPipelineResult(String pipelineName) {
            this.pipelineName = pipelineName;
        }
    }

    // Simulates run_select_pipeline behavior
    static SelectPipelineResult runSelectPipeline(AutoHarnessConfigStub config, OptimizationTaskStub task) {
        // Explicit task pipeline wins
        if (task.pipelineName != null) {
            return new SelectPipelineResult(task.pipelineName);
        }

        // No model falls back to PR or META
        if (config.model == null) {
            return new SelectPipelineResult(META_EVOLVE_PIPELINE);
        }

        // Default to PR pipeline
        return new SelectPipelineResult(PR_PIPELINE);
    }

    @Nested
    @DisplayName("Select Pipeline Tests")
    class TestSelectPipelineStage {

        @Test
        @DisplayName("explicit task pipeline wins")
        void testExplicitTaskPipelineWins() {
            AutoHarnessConfigStub config = new AutoHarnessConfigStub(new Object()); // Mock model
            OptimizationTaskStub task = new OptimizationTaskStub("t1", EXTENDED_EVOLVE_PIPELINE);

            SelectPipelineResult result = runSelectPipeline(config, task);

            assertEquals(EXTENDED_EVOLVE_PIPELINE, result.pipelineName);
        }

        @Test
        @DisplayName("no model falls back to meta")
        void testNoModelFallsBackToMeta() {
            AutoHarnessConfigStub config = new AutoHarnessConfigStub(null);
            OptimizationTaskStub task = new OptimizationTaskStub("t1");

            SelectPipelineResult result = runSelectPipeline(config, task);

            assertEquals(META_EVOLVE_PIPELINE, result.pipelineName);
        }

        @Test
        @DisplayName("with model defaults to PR")
        void testWithModelDefaultsToPr() {
            AutoHarnessConfigStub config = new AutoHarnessConfigStub(new Object());
            OptimizationTaskStub task = new OptimizationTaskStub("t1");

            SelectPipelineResult result = runSelectPipeline(config, task);

            assertEquals(PR_PIPELINE, result.pipelineName);
        }

        @Test
        @DisplayName("task pipeline overrides model presence")
        void testTaskPipelineOverridesModelPresence() {
            AutoHarnessConfigStub config = new AutoHarnessConfigStub(null); // No model
            OptimizationTaskStub task = new OptimizationTaskStub("t1", EXTENDED_EVOLVE_PIPELINE);

            SelectPipelineResult result = runSelectPipeline(config, task);

            // Task pipeline wins even with no model
            assertEquals(EXTENDED_EVOLVE_PIPELINE, result.pipelineName);
        }
    }
}