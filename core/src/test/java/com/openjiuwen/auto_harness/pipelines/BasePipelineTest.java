/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code BasePipeline} in
 * {@code openjiuwen/auto_harness/pipelines/base.py}.
 */
class BasePipelineTest {

    @Test
    void stageMapInstantiatesBoundStageOrFails() {
        PipelineStageMap stageMap = new PipelineStageMap(Map.of("plan", DemoStage.class));

        assertThat(stageMap.resolve("plan")).isInstanceOf(DemoStage.class);
        assertThatThrownBy(() -> stageMap.resolve("missing"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("No stage bound for slot 'missing'");
    }

    @Test
    void specMirrorsPipelineMetadata() {
        DemoPipeline pipeline = new DemoPipeline();

        PipelineSpec spec = pipeline.spec();

        assertThat(spec.getName()).isEqualTo("demo");
        assertThat(spec.getPipelineCls()).isEqualTo(DemoPipeline.class);
        assertThat(spec.getDescription()).isEqualTo("Demo pipeline");
        assertThat(spec.getExpectedOutputs()).containsExactly("artifact");
    }

    @Test
    void streamStageCapturesStageResultArtifactsAndMessages() {
        DemoPipeline pipeline = new DemoPipeline();
        SessionContext ctx = new SessionContext(new AutoHarnessOrchestrator());
        List<StageResult> holder = new ArrayList<>();

        List<Object> events = toList(pipeline.streamStage(new DemoStage(), ctx, holder));

        assertThat(holder).hasSize(1);
        assertThat(ctx.getArtifact("artifact")).isEqualTo("value");
        assertThat(events).hasSize(4);
        assertThat(((OutputSchema) events.get(0)).getPayload()).isEqualTo(Map.of(
                "content", "Planning",
                "stage", "plan"
        ));
        assertThat(((OutputSchema) events.get(1)).getPayload()).isEqualTo(Map.of("content", "raw"));
        assertThat(((OutputSchema) events.get(2)).getPayload()).isEqualTo(Map.of("content", "done"));
        assertThat(events.get(3)).isSameAs(holder.get(0));
    }

    @Test
    void requireStageResultAndFailureStatusMatchPythonBehavior() {
        DemoPipeline pipeline = new DemoPipeline();
        DemoStage stage = new DemoStage();
        StageResult failed = StageResult.builder().status("failed").build();

        assertThatThrownBy(() -> pipeline.requireStageResult(stage, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stage 'demo_stage' did not return a StageResult");
        assertThat(pipeline.didStageFail(stage, List.of(failed))).isTrue();
    }

    @Test
    void scopeOutputEventStageCopiesOnlyStageableOutputSchemaPayloads() {
        OutputSchema message = new OutputSchema("message", 0, new LinkedHashMap<>(Map.of("content", "hello")));
        Object scoped = BaseStage.scopeOutputEventStage(message, "plan");

        assertThat(scoped).isInstanceOf(OutputSchema.class);
        assertThat(((OutputSchema) scoped).getPayload()).isEqualTo(Map.of("content", "hello", "stage", "plan"));
        assertThat(BaseStage.scopeOutputEventStage("plain", "plan")).isEqualTo("plain");
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    public static class DemoPipeline extends BasePipeline {
        @Override
        public String name() {
            return "demo";
        }

        @Override
        public String description() {
            return "Demo pipeline";
        }

        @Override
        public List<String> expectedOutputs() {
            return List.of("artifact");
        }

        @Override
        public PipelineStageMap stageMap() {
            return new PipelineStageMap(Map.of("plan", DemoStage.class));
        }
    }

    public static class DemoStage extends BaseStage {
        @Override
        public String name() {
            return "demo_stage";
        }

        @Override
        public String slot() {
            return "plan";
        }

        @Override
        public String displayName() {
            return "Planning";
        }

        @Override
        public Iterator<Object> stream(com.openjiuwen.auto_harness.contexts.BaseExecutionContext ctx) {
            StageResult result = StageResult.builder()
                    .status("success")
                    .artifacts(Map.of("artifact", "value"))
                    .messages(List.of("done"))
                    .build();
            return List.of(new OutputSchema("message", 0, Map.of("content", "raw")), result).iterator();
        }
    }
}
