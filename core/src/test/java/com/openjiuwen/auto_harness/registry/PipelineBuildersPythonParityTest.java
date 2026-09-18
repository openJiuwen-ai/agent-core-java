/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.ExtendedEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSpec;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.auto_harness.stages.ExtendAssessStage;
import com.openjiuwen.auto_harness.stages.ExtendImplementStage;
import com.openjiuwen.auto_harness.stages.ExtendPlanStage;
import com.openjiuwen.auto_harness.stages.ExtendVerifyStage;
import com.openjiuwen.auto_harness.stages.LearningsStage;
import com.openjiuwen.auto_harness.stages.MetaAssessStage;
import com.openjiuwen.auto_harness.stages.MetaImplementStage;
import com.openjiuwen.auto_harness.stages.MetaPlanStage;
import com.openjiuwen.auto_harness.stages.MetaVerifyStage;
import com.openjiuwen.auto_harness.stages.PublishPRStage;
import com.openjiuwen.auto_harness.stages.SessionStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/auto_harness/test_pipeline.py}.
 */
class PipelineBuildersPythonParityTest {

    @Test
    void builtinMetaPipelineUsesPipelineClass() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        StageRegistry stageRegistry = AutoHarnessBuiltinRegistry.buildStageRegistry(config);
        PipelineRegistry pipelineRegistry = AutoHarnessBuiltinRegistry.buildPipelineRegistry(config, stageRegistry);

        PipelineSpec spec = pipelineRegistry.require(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);

        assertThat(spec.getPipelineCls()).isEqualTo(MetaEvolvePipeline.class);
        assertThat(spec.getExpectedOutputs()).containsExactly("session_results");
    }

    @Test
    void extendedPipelineUsesPipelineClass() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        StageRegistry stageRegistry = AutoHarnessBuiltinRegistry.buildStageRegistry(config);
        PipelineRegistry pipelineRegistry = AutoHarnessBuiltinRegistry.buildPipelineRegistry(config, stageRegistry);

        PipelineSpec spec = pipelineRegistry.require(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);

        assertThat(spec.getPipelineCls()).isEqualTo(ExtendedEvolvePipeline.class);
    }

    @Test
    void builtinStageRegistryUsesStageClasses() {
        StageRegistry registry = AutoHarnessBuiltinRegistry.buildStageRegistry(new AutoHarnessConfig());

        assertThat(registry.require("assess").getStageCls()).isEqualTo(MetaAssessStage.class);
        assertThat(registry.require("plan").getStageCls()).isEqualTo(MetaPlanStage.class);
        assertThat(registry.require("implement").getStageCls()).isEqualTo(MetaImplementStage.class);
        assertThat(registry.require("verify").getStageCls()).isEqualTo(MetaVerifyStage.class);
        assertThat(registry.require("assess_ext").getStageCls()).isEqualTo(ExtendAssessStage.class);
        assertThat(registry.require("plan_ext").getStageCls()).isEqualTo(ExtendPlanStage.class);
        assertThat(registry.require("implement_ext").getStageCls()).isEqualTo(ExtendImplementStage.class);
        assertThat(registry.require("verify_ext").getStageCls()).isEqualTo(ExtendVerifyStage.class);
        assertThat(registry.require("commit").getStageCls()).isEqualTo(CommitStage.class);
        assertThat(registry.require("publish_pr").getStageCls()).isEqualTo(PublishPRStage.class);
        assertThat(registry.require("learnings").getStageCls()).isEqualTo(LearningsStage.class);
    }

    @Test
    void buildStageRegistryLoadsRegistrars() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setStageRegistrars(List.of(TestRegistrars.class.getName() + ":registerTestStage"));

        StageRegistry registry = AutoHarnessBuiltinRegistry.buildStageRegistry(config);

        assertThat(registry.require("custom_stage").getStageCls()).isEqualTo(DummyStage.class);
    }

    @Test
    void buildPipelineRegistryLoadsRegistrars() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setStageRegistrars(List.of(TestRegistrars.class.getName() + ":registerTestStage"));
        config.setPipelineRegistrars(List.of(TestRegistrars.class.getName() + ":registerTestPipeline"));
        StageRegistry stageRegistry = AutoHarnessBuiltinRegistry.buildStageRegistry(config);

        PipelineRegistry pipelineRegistry = AutoHarnessBuiltinRegistry.buildPipelineRegistry(config, stageRegistry);

        assertThat(pipelineRegistry.require("custom_pipeline").getPipelineCls()).isEqualTo(DummyPipeline.class);
    }

    public static final class TestRegistrars {
        private TestRegistrars() {
        }

        public static void registerTestStage(StageRegistry registry) {
            registry.register(StageSpec.builder()
                    .name("custom_stage")
                    .stageCls(DummyStage.class)
                    .produces(List.of("custom_artifact"))
                    .build());
        }

        public static void registerTestPipeline(PipelineRegistry registry, StageRegistry stageRegistry) {
            assertThat(stageRegistry.require("custom_stage").getStageCls()).isEqualTo(DummyStage.class);
            registry.register(PipelineSpec.builder()
                    .name("custom_pipeline")
                    .pipelineCls(DummyPipeline.class)
                    .expectedOutputs(List.of("custom_artifact"))
                    .build());
        }
    }

    static final class DummyStage extends SessionStage {
        @Override
        public String name() {
            return "custom_stage";
        }

        @Override
        public List<String> produces() {
            return List.of("custom_artifact");
        }
    }

    static final class DummyPipeline extends BasePipeline {
        @Override
        public String name() {
            return "custom_pipeline";
        }

        @Override
        public List<String> expectedOutputs() {
            return List.of("custom_artifact");
        }
    }
}
