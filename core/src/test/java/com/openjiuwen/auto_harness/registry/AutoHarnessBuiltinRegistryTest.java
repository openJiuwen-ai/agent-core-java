/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's module helpers in
 * {@code openjiuwen/auto_harness/registry/builtin.py}.
 */
class AutoHarnessBuiltinRegistryTest {

    @Test
    void registerBuiltinStagesMatchesPythonStageOrder() {
        StageRegistry registry = AutoHarnessBuiltinRegistry.registerBuiltinStages(new StageRegistry());

        assertThat(registry.names()).containsExactly(
                "assess",
                "assess_ext",
                "plan",
                "plan_ext",
                "implement",
                "implement_ext",
                "verify",
                "verify_ext",
                "commit",
                "publish_pr",
                "learnings"
        );
        assertThat(registry.require("implement").getSlot()).isEqualTo("implement");
        assertThat(registry.require("assess").getScope()).isEqualTo("session");
        assertThat(registry.require("commit").getScope()).isEqualTo("task");
    }

    @Test
    void buildStageRegistryInvokesConfiguredRegistrars() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setStageRegistrars(List.of(TestRegistrars.class.getName() + ":registerStage"));

        StageRegistry registry = AutoHarnessBuiltinRegistry.buildStageRegistry(config);

        assertThat(registry.names()).contains("custom_stage");
    }

    @Test
    void buildPipelineRegistryRegistersBuiltinsAndOneArgRegistrar() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setPipelineRegistrars(List.of(TestRegistrars.class.getName() + ":registerPipeline"));

        PipelineRegistry registry = AutoHarnessBuiltinRegistry.buildPipelineRegistry(config, new StageRegistry());

        assertThat(registry.names()).contains(
                AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE,
                "custom_pipeline"
        );
    }

    @Test
    void buildPipelineRegistryPassesStageRegistryToTwoArgRegistrar() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setPipelineRegistrars(List.of(TestRegistrars.class.getName() + ":registerPipelineWithStages"));
        StageRegistry stages = new StageRegistry();
        stages.register(StageSpec.builder()
                .name("stage_for_pipeline")
                .stageCls(Object.class)
                .build());

        PipelineRegistry registry = AutoHarnessBuiltinRegistry.buildPipelineRegistry(config, stages);

        assertThat(registry.names()).contains("custom_pipeline_with_stage_for_pipeline");
    }

    @Test
    void invalidRegistrarSyntaxIsRejected() {
        assertThatThrownBy(() -> AutoHarnessBuiltinRegistry.loadRegistrar("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module:callable");
    }

    /**
     * Test registrar methods for Java's reflected callable equivalent.
     *
     * <p>Mirrors Python's extension registrar callables in
     * {@code openjiuwen/auto_harness/registry/builtin.py}.</p>
     */
    public static final class TestRegistrars {
        private TestRegistrars() {
        }

        public static void registerStage(StageRegistry registry) {
            registry.register(StageSpec.builder()
                    .name("custom_stage")
                    .stageCls(Object.class)
                    .build());
        }

        public static void registerPipeline(PipelineRegistry registry) {
            registry.register(PipelineSpec.builder()
                    .name("custom_pipeline")
                    .pipelineCls(Object.class)
                    .build());
        }

        public static void registerPipelineWithStages(PipelineRegistry registry, StageRegistry stages) {
            registry.register(PipelineSpec.builder()
                    .name("custom_pipeline_with_" + stages.names().get(0))
                    .pipelineCls(Object.class)
                    .build());
        }
    }
}
