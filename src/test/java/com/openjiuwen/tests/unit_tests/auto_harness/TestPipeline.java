// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.tests.unit_tests.auto_harness;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.registry.BuiltinRegistries;
import com.openjiuwen.auto_harness.registry.PipelineRegistry;
import com.openjiuwen.auto_harness.registry.StageRegistry;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.StageSpec;
import com.openjiuwen.auto_harness.stages.AssessStage;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.LearningsStage;
import com.openjiuwen.auto_harness.stages.PlanStage;
import com.openjiuwen.auto_harness.stages.PublishPrStage;
import com.openjiuwen.auto_harness.stages.SessionStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_pipeline} in {@code tests.unit_tests.auto_harness.test_pipeline}.
 * Pipeline registry tests for auto-harness.
 */
class TestPipeline {

    /**
     * Dummy stage for testing custom stage registration.
     * Mirrors Python's {@code _DummyStage} inner class.
     */
    private static class DummyStage extends SessionStage {
        @Override
        public String name() {
            return "custom_stage";
        }

        @Override
        public List<String> produces() {
            return List.of("custom_artifact");
        }

        @Override
        public StageResult run(Object context) {
            return new StageResult();
        }
    }

    /**
     * Dummy pipeline for testing custom pipeline registration.
     * Mirrors Python's {@code _DummyPipeline} inner class.
     */
    private static class DummyPipeline extends BasePipeline {
        @Override
        public String name() {
            return "custom_pipeline";
        }

        @Override
        public List<String> expectedOutputs() {
            return List.of("custom_artifact");
        }
    }

    /**
     * Helper method to register a test stage.
     * Mirrors Python's {@code register_test_stage} function.
     */
    private static void registerTestStage(StageRegistry registry) {
        registry.register(new StageSpec(
                "custom_stage",
                DummyStage.class,
                "session",
                List.of(),
                List.of("custom_artifact"),
                ""
        ));
    }

    /**
     * Helper method to register a test pipeline.
     * Mirrors Python's {@code register_test_pipeline} function.
     */
    private static void registerTestPipeline(PipelineRegistry registry, StageRegistry stageRegistry) {
        assertNotNull(stageRegistry.require("custom_stage"));
        registry.register(new PipelineSpec(
                "custom_pipeline",
                DummyPipeline.class,
                "",
                List.of("custom_artifact")
        ));
    }

    /**
     * Test that builtin meta pipeline uses pipeline class.
     * Mirrors Python's {@code test_builtin_meta_pipeline_uses_pipeline_class}.
     */
    @Test
    void testBuiltinMetaPipelineUsesPipelineClass() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        StageRegistry stageRegistry = BuiltinRegistries.buildStageRegistry(cfg);
        PipelineRegistry pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(cfg, stageRegistry);

        PipelineSpec spec = pipelineRegistry.require(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertEquals(MetaEvolvePipeline.class, spec.getPipelineClass());
        assertEquals(List.of("session_results"), spec.getExpectedOutputs());
    }

    /**
     * Test that extended pipeline uses pipeline class.
     * Mirrors Python's {@code test_extended_pipeline_uses_pipeline_class}.
     */
    @Test
    void testExtendedPipelineUsesPipelineClass() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        StageRegistry stageRegistry = BuiltinRegistries.buildStageRegistry(cfg);
        PipelineRegistry pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(cfg, stageRegistry);

        PipelineSpec spec = pipelineRegistry.require(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertEquals(ExtendedEvolvePipeline.class, spec.getPipelineClass());
    }

    /**
     * Test that builtin stage registry uses stage classes.
     * Mirrors Python's {@code test_builtin_stage_registry_uses_stage_classes}.
     */
    @Test
    void testBuiltinStageRegistryUsesStageClasses() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        StageRegistry registry = BuiltinRegistries.buildStageRegistry(cfg);

        assertEquals(AssessStage.class, registry.require("assess").getStageClass());
        assertEquals(PlanStage.class, registry.require("plan").getStageClass());
        assertEquals(ImplementStage.class, registry.require("implement").getStageClass());
        assertEquals(VerifyStage.class, registry.require("verify").getStageClass());
        assertEquals(CommitStage.class, registry.require("commit").getStageClass());
        assertEquals(PublishPrStage.class, registry.require("publish_pr").getStageClass());
        assertEquals(LearningsStage.class, registry.require("learnings").getStageClass());
    }

    /**
     * Test that build stage registry loads registrars.
     * Mirrors Python's {@code test_build_stage_registry_loads_registrars}.
     * Note: Java version manually registers test stage instead of using dynamic registrars.
     */
    @Test
    void testBuildStageRegistryLoadsRegistrars() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        StageRegistry registry = BuiltinRegistries.buildStageRegistry(cfg);

        // In Python, registrars are loaded via config.stage_registrars path strings.
        // In Java, we manually register the test stage for this test.
        registerTestStage(registry);

        StageSpec spec = registry.require("custom_stage");
        assertEquals(DummyStage.class, spec.getStageClass());
    }

    /**
     * Test that build pipeline registry loads registrars.
     * Mirrors Python's {@code test_build_pipeline_registry_loads_registrars}.
     * Note: Java version manually registers test stage and pipeline instead of using dynamic registrars.
     */
    @Test
    void testBuildPipelineRegistryLoadsRegistrars() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        StageRegistry stageRegistry = BuiltinRegistries.buildStageRegistry(cfg);
        PipelineRegistry pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(cfg, stageRegistry);

        // In Python, registrars are loaded via config.stage_registrars and config.pipeline_registrars.
        // In Java, we manually register the test stage and pipeline for this test.
        registerTestStage(stageRegistry);
        registerTestPipeline(pipelineRegistry, stageRegistry);

        PipelineSpec spec = pipelineRegistry.require("custom_pipeline");
        assertEquals(DummyPipeline.class, spec.getPipelineClass());
    }
}