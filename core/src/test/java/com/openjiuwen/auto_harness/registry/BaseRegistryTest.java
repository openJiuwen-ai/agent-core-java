/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseRegistryTest {

    @Test
    void registersAndRequiresStageSpecsByName() {
        StageRegistry registry = new StageRegistry();
        StageSpec spec = StageSpec.builder()
                .name("custom_stage")
                .stageCls(DummyStage.class)
                .build();

        registry.register(spec);

        assertThat(registry.get("custom_stage")).isSameAs(spec);
        assertThat(registry.require("custom_stage")).isSameAs(spec);
        assertThat(registry.names()).containsExactly("custom_stage");
    }

    @Test
    void namesPreserveRegistrationOrder() {
        StageRegistry registry = new StageRegistry();
        registry.register(StageSpec.builder().name("assess").stageCls(DummyStage.class).build());
        registry.register(StageSpec.builder().name("plan").stageCls(DummyStage.class).build());
        registry.register(StageSpec.builder().name("implement").stageCls(DummyStage.class).build());

        assertThat(registry.names()).containsExactly("assess", "plan", "implement");
    }

    @Test
    void duplicateRegistrationRaisesValueErrorEquivalent() {
        StageRegistry registry = new StageRegistry();
        StageSpec spec = StageSpec.builder()
                .name("custom_stage")
                .stageCls(DummyStage.class)
                .build();

        registry.register(spec);

        assertThatThrownBy(() -> registry.register(spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate registration: custom_stage");
    }

    @Test
    void requireUnknownRaisesKeyErrorEquivalent() {
        StageRegistry registry = new StageRegistry();

        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Unknown item 'missing'");
    }

    @Test
    void pipelineRegistryUsesPipelineSpecName() {
        PipelineRegistry registry = new PipelineRegistry();
        PipelineSpec spec = PipelineSpec.builder()
                .name("custom_pipeline")
                .pipelineCls(DummyPipeline.class)
                .build();

        registry.register(spec);

        assertThat(registry.names()).containsExactly("custom_pipeline");
        assertThat(registry.require("custom_pipeline")).isSameAs(spec);
    }

    /**
     * Mirrors Python's custom stage class metadata in
     * {@code openjiuwen/auto_harness/registry/base.py}.
     */
    static final class DummyStage {
    }

    /**
     * Mirrors Python's custom pipeline class metadata in
     * {@code openjiuwen/auto_harness/registry/base.py}.
     */
    static final class DummyPipeline {
    }
}
