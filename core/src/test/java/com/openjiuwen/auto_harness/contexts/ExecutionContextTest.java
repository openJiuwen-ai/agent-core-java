/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's execution context behavior in
 * {@code openjiuwen/auto_harness/contexts/execution.py}.
 */
class ExecutionContextTest {

    @Test
    void taskKeyUsesTopicOrPythonFallback() {
        assertThat(TaskContext.taskKey(OptimizationTask.builder().topic("topic-a").build())).isEqualTo("topic-a");
        assertThat(TaskContext.taskKey(OptimizationTask.builder().topic("").build())).isEqualTo("task");
        assertThat(TaskContext.taskKey(OptimizationTask.builder().build())).isEqualTo("task");
    }

    @Test
    void taskContextScopesArtifactsByTaskId() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator();
        SessionContext session = new SessionContext(orchestrator);
        TaskContext task = new TaskContext(
                orchestrator,
                OptimizationTask.builder().topic("task-one").build(),
                new TaskRuntime()
        );

        session.putArtifact("shared", "session-value");
        task.putArtifact("local", "task-value");

        assertThat(session.getTaskId()).isEmpty();
        assertThat(task.getTaskId()).isEqualTo("task-one");
        assertThat(task.getArtifact("local")).isEqualTo("task-value");
        assertThat(session.getArtifact("local", "missing")).isEqualTo("missing");
        assertThat(task.requireArtifact("shared")).isEqualTo("session-value");
    }

    @Test
    void messageOptionallyIncludesStage() {
        OutputSchema message = BaseExecutionContext.message("hello");
        assertThat(message.getType()).isEqualTo("message");
        assertThat(message.getIndex()).isZero();
        assertThat(message.getPayload()).isEqualTo(Map.of("content", "hello"));

        OutputSchema staged = BaseExecutionContext.message("done", "plan");
        assertThat(staged.getPayload()).isEqualTo(Map.of("content", "done", "stage", "plan"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stageResultOutputMatchesPythonPayloadDefaults() {
        OutputSchema output = BaseExecutionContext.stageResultOutput("verify", "failed");
        assertThat(output.getType()).isEqualTo("stage_result");
        assertThat(output.getIndex()).isZero();
        Map<String, Object> payload = (Map<String, Object>) output.getPayload();
        assertThat(payload)
                .containsEntry("stage", "verify")
                .containsEntry("status", "failed")
                .containsEntry("error", "");
        assertThat(payload.get("messages")).isEqualTo(List.of());
        assertThat(payload.get("metrics")).isEqualTo(Map.of());

        OutputSchema populated = BaseExecutionContext.stageResultOutput(
                "assess",
                "success",
                "",
                List.of("m1"),
                Map.of("score", 1)
        );
        Map<String, Object> populatedPayload = (Map<String, Object>) populated.getPayload();
        assertThat(populatedPayload.get("messages")).isEqualTo(List.of("m1"));
        assertThat(populatedPayload.get("metrics")).isEqualTo(Map.of("score", 1));
    }

    @Test
    void taskRuntimeDefaultsOptionalPythonFieldsToNull() {
        TaskRuntime runtime = new TaskRuntime();
        assertThat(runtime.getRelated()).isEmpty();
        assertThat(runtime.getPreexistingDirtyFiles()).isEmpty();
        assertThat(runtime.getTaskSession()).isNull();
        assertThat(runtime.getFixAgent()).isNull();
    }
}
