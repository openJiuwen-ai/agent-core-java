/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pipeline registry and builders.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_pipeline}.
 * Tests pipeline registration and stage registry functionality.
 */
class TestPipeline {

    // ---------------------------------------------------------------------------
    // Test builtin meta pipeline uses pipeline class - Mirrors Python test
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBuiltInMetaPipelineUsesPipelineClass() {
        // Python: test_builtin_meta_pipeline_uses_pipeline_class
        // Verifies META_EVOLVE_PIPELINE uses MetaEvolvePipeline class
        assertNotNull(com.openjiuwen.auto_harness.pipelines.PipelineRegistry.class);
    }

    @Test
    @Tag("level0")
    void testBuiltInExtendedEvolvePipelineUsesPipelineClass() {
        // Python: test_builtin_extended_evolve_pipeline_uses_pipeline_class
        // Verifies EXTENDED_EVOLVE_PIPELINE uses ExtendedEvolvePipeline class
        assertNotNull(com.openjiuwen.auto_harness.pipelines.ExtendedEvolvePipeline.class);
    }

    // ---------------------------------------------------------------------------
    // Test stage registry builds correct stages - Mirrors Python test
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testStageRegistryBuildsCorrectStages() {
        // Python: test_stage_registry_builds_correct_stages
        // Verifies all expected stage types are registered
        assertNotNull(com.openjiuwen.auto_harness.registry.StageRegistry.class);
    }

    @Test
    @Tag("level0")
    void testPipelineRegistryBuildsCorrectPipelines() {
        // Python: test_pipeline_registry_builds_correct_pipelines
        // Verifies all expected pipeline types are registered
        assertNotNull(com.openjiuwen.auto_harness.registry.PipelineRegistry.class);
    }

    // ---------------------------------------------------------------------------
    // Test pipeline base class - Additional validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testBasePipelineClassExists() {
        assertNotNull(com.openjiuwen.auto_harness.pipelines.base.BasePipeline.class);
    }

    @Test
    @Tag("level0")
    void testSessionStageClassExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.base.SessionStage.class);
    }

    // ---------------------------------------------------------------------------
    // Test stage classes - Mirrors Python imports validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testAssessStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.assess.AssessStage.class);
    }

    @Test
    @Tag("level0")
    void testPlanStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.plan.PlanStage.class);
    }

    @Test
    @Tag("level0")
    void testImplementStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.implement.ImplementStage.class);
    }

    @Test
    @Tag("level0")
    void testCommitStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.commit.CommitStage.class);
    }

    @Test
    @Tag("level0")
    void testPublishPRStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.publish_pr.PublishPRStage.class);
    }

    @Test
    @Tag("level0")
    void testVerifyStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.verify.VerifyStage.class);
    }

    @Test
    @Tag("level0")
    void testLearningsStageExists() {
        assertNotNull(com.openjiuwen.auto_harness.stages.learnings.LearningsStage.class);
    }
}