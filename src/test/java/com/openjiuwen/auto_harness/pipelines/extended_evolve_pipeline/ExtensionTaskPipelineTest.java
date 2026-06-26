/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code extension_task_pipeline.py} in
 * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extension_task_pipeline.py}.
 */
class ExtensionTaskPipelineTest {

    @Test
    void buildExtensionTaskWrapsDesignLikePythonHelper() {
        Map<String, String> filePlan = new LinkedHashMap<>();
        filePlan.put("root", "runtime/foo");
        filePlan.put("manifest", "runtime/foo/manifest.yaml");
        ExtensionDesign design = ExtensionDesign.builder()
                .extensionName("foo")
                .filePlan(filePlan)
                .build();

        OptimizationTask task = ExtensionTaskPipeline.buildExtensionTask(design);

        assertThat(task.getTopic()).isEqualTo("runtime-extension:foo");
        assertThat(task.getDescription()).isEqualTo("Implement and verify runtime extension foo");
        assertThat(task.getFiles()).containsExactly("runtime/foo", "runtime/foo/manifest.yaml");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stageOutputUsesParentStageAndExtensionMetadata() {
        TaskContext ctx = contextWithDesign("demo");
        OutputSchema output = ExtensionTaskPipeline.extensionStageOutput(
                ctx,
                new ScriptedStage("implement_ext", "implement", true),
                "running"
        );

        assertThat(output.getType()).isEqualTo("stage_result");
        Map<String, Object> payload = (Map<String, Object>) output.getPayload();
        assertThat(payload)
                .containsEntry("stage", "build_verify")
                .containsEntry("scope", "extension")
                .containsEntry("parent_stage", "build_verify")
                .containsEntry("extension_stage", "implement_ext")
                .containsEntry("extension_name", "demo")
                .containsEntry("task_id", "runtime-extension:demo")
                .containsEntry("status", "running")
                .containsEntry("error", "");
        assertThat(payload.get("messages")).isEqualTo(List.of());
        assertThat(payload.get("metrics")).isEqualTo(Map.of());
    }

    @Test
    void streamRunsStagesAndCreatesSuccessResultWhenActivatePasses() {
        TaskContext ctx = contextWithDesign("demo");
        ExtensionTaskPipeline pipeline = new ExtensionTaskPipeline(
                name -> new ScriptedStage(name, slotFor(name), true, List.of(name + "-done"))
        );

        List<Object> events = toList(pipeline.stream(ctx));

        assertThat(events).hasSize(10);
        CycleResult result = (CycleResult) ctx.getArtifact("task_result");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSummary()).isEqualTo("Extension activated: demo");
        assertThat(messageContents(events)).contains(
                "implement_ext-done",
                "verify_ext-done",
                "activate_ext-done",
                "Extension activated: demo"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamStopsAfterFailedStageAndRecordsFailureResult() {
        TaskContext ctx = contextWithDesign("demo");
        ExtensionTaskPipeline pipeline = new ExtensionTaskPipeline(name -> {
            if ("implement_ext".equals(name)) {
                return new ScriptedStage(name, "implement", false, List.of("implement failed"));
            }
            return new ScriptedStage(name, slotFor(name), true, List.of(name + "-done"));
        });

        List<Object> events = toList(pipeline.stream(ctx));

        assertThat(events).hasSize(3);
        Map<String, Object> failedPayload = (Map<String, Object>) ((OutputSchema) events.get(2)).getPayload();
        assertThat(failedPayload)
                .containsEntry("stage", "build_verify")
                .containsEntry("extension_stage", "implement_ext")
                .containsEntry("status", "failed")
                .containsEntry("error", "implement_ext failed");
        CycleResult result = (CycleResult) ctx.getArtifact("task_result");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("implement_ext failed");
        assertThat(messageContents(events)).doesNotContain("verify_ext-done", "activate_ext-done");
    }

    @Test
    void resolveTaskResultUpdatesTaskStatusAndReportsMissingResult() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator();
        OptimizationTask task = OptimizationTask.builder()
                .topic("runtime-extension:demo")
                .status(TaskStatus.RUNNING)
                .build();

        CycleResult missing = ExtensionTaskPipeline.resolveTaskResult(orchestrator, task);

        assertThat(missing.isSuccess()).isFalse();
        assertThat(missing.getError()).isEqualTo("missing result");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);

        task.setStatus(TaskStatus.RUNNING);
        orchestrator.getArtifacts().put(
                "task_result",
                CycleResult.builder().success(true).summary("ok").build(),
                TaskContext.taskKey(task)
        );
        CycleResult success = ExtensionTaskPipeline.resolveTaskResult(orchestrator, task);

        assertThat(success.isSuccess()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    void resolveBuildVerifyResultFallsBackToRuntimeExtensionArtifact() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator();
        OptimizationTask task = OptimizationTask.builder().topic("runtime-extension:demo").build();

        CycleResult missing = ExtensionTaskPipeline.resolveBuildVerifyResult(orchestrator, task);
        assertThat(missing.isSuccess()).isFalse();
        assertThat(missing.getError()).isEqualTo("missing runtime extension");

        orchestrator.getArtifacts().put(
                "runtime_extension",
                RuntimeExtensionArtifact.builder().extensionName("demo").build(),
                TaskContext.taskKey(task)
        );
        CycleResult success = ExtensionTaskPipeline.resolveBuildVerifyResult(orchestrator, task);

        assertThat(success.isSuccess()).isTrue();
        assertThat(success.getSummary()).isEqualTo("Extension verified: runtime-extension:demo");
    }

    private static TaskContext contextWithDesign(String extensionName) {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator();
        OptimizationTask task = OptimizationTask.builder()
                .topic("runtime-extension:" + extensionName)
                .status(TaskStatus.RUNNING)
                .build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        ctx.putArtifact("extension_target", ExtensionDesign.builder()
                .extensionName(extensionName)
                .build());
        return ctx;
    }

    private static String slotFor(String stageName) {
        return switch (stageName) {
            case "implement_ext" -> "implement";
            case "verify_ext" -> "verify";
            case "activate_ext" -> "activate";
            default -> stageName;
        };
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> events = new ArrayList<>();
        iterator.forEachRemaining(events::add);
        return events;
    }

    @SuppressWarnings("unchecked")
    private static List<String> messageContents(List<Object> events) {
        List<String> contents = new ArrayList<>();
        for (Object event : events) {
            if (!(event instanceof OutputSchema output) || !"message".equals(output.getType())) {
                continue;
            }
            Map<String, Object> payload = (Map<String, Object>) output.getPayload();
            contents.add(String.valueOf(payload.get("content")));
        }
        return contents;
    }

    private static final class ScriptedStage extends BaseStage {
        private final String name;
        private final String slot;
        private final boolean success;
        private final List<String> messages;

        private ScriptedStage(String name, String slot, boolean success) {
            this(name, slot, success, List.of());
        }

        private ScriptedStage(String name, String slot, boolean success, List<String> messages) {
            this.name = name;
            this.slot = slot;
            this.success = success;
            this.messages = List.copyOf(messages);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String slot() {
            return slot;
        }

        @Override
        public Iterator<Object> stream(BaseExecutionContext ctx) {
            StageResult result = StageResult.builder()
                    .status(success ? "success" : "failed")
                    .messages(messages)
                    .error(success ? "" : name + " failed")
                    .build();
            return List.of((Object) result).iterator();
        }
    }
}
