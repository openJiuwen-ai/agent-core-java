package com.openjiuwen.autoharness.pipelines;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PRTaskPipelineCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void runIsolatedStreamShouldRecordTimeoutResultAndExperience() throws Exception {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .taskTimeoutSecs(0.02)
                .build());
        OptimizationTask task = OptimizationTask.builder().topic("timeout task").build();

        List<Object> events = PRTaskPipeline.runIsolatedStream(orchestrator, task, () -> {
            Thread.sleep(500);
            return List.of(BaseExecutionContext.message("late event"));
        });

        assertThat(events).isEmpty();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TIMEOUT);
        assertThat(orchestrator.getResults()).hasSize(1);
        CycleResult result = orchestrator.getResults().get(0);
        assertThat(result.getError()).isEqualTo("timeout");
        assertThat(result.getErrorLog()).isEqualTo("Task exceeded timeout");
        assertThat(Files.readString(orchestrator.getConfig().experiencePath().resolve("experiences.jsonl")))
                .contains("timeout task")
                .contains("task timeout")
                .contains("timeout");
    }

    @Test
    void runIsolatedStreamShouldPassthroughTaskEventsBeforeRecordingResult() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data-success").toString())
                .taskTimeoutSecs(10.0)
                .build());
        OptimizationTask task = OptimizationTask.builder().topic("streamed task").build();
        List<Object> taskEvents = List.of(
                BaseExecutionContext.message("first chunk"),
                BaseExecutionContext.message("second chunk")
        );

        List<Object> events = PRTaskPipeline.runIsolatedStream(orchestrator, task, () -> {
            orchestrator.getArtifacts().put("task_result",
                    CycleResult.builder().isSuccess(true).summary("done").build(),
                    "streamed task");
            return taskEvents;
        });

        assertThat(events).containsExactlyElementsOf(taskEvents);
        assertThat(orchestrator.getResults()).hasSize(1);
        assertThat(orchestrator.getResults().get(0).isSuccess()).isTrue();
        assertThat(orchestrator.getResults().get(0).getSummary()).isEqualTo("done");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }
}
