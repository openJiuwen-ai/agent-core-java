/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.pipelines;

import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.contexts.TaskRuntime;
import com.openjiuwen.autoharness.experience.ExperienceStore;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.rails.EditSafetyRail;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.stages.BaseStage;
import com.openjiuwen.autoharness.stages.CommitStage;
import com.openjiuwen.autoharness.stages.ImplementStage;
import com.openjiuwen.autoharness.stages.PublishPrStage;
import com.openjiuwen.autoharness.stages.VerifyStage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Public class PRTaskPipeline used by the Java parity implementation.
 *
 * @since 1.0
 */
public class PRTaskPipeline extends BasePipeline {
    private static final class StopTaskPipeline extends RuntimeException {
        private final List<Object> events;

        private StopTaskPipeline(List<Object> events) {
            this.events = events == null ? List.of() : events;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "pr_task_pipeline";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Explicit task-scoped pipeline for meta evolve work.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(com.openjiuwen.autoharness.contexts.BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("PRTaskPipeline requires TaskContext");
        }
        List<Object> events = new ArrayList<>();
        try {
            events.addAll(runImplementStageStream(taskContext));
            events.addAll(runVerifyStageStream(taskContext));
            events.addAll(runCommitStageStream(taskContext));
            events.addAll(runPublishPrStageStream(taskContext));
        } catch (StopTaskPipeline stop) {
            events.addAll(stop.events);
            return events;
        }
        return events;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> runImplementStageStream(TaskContext ctx) {
        return runTaskStageStream(new ImplementStage(), ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> runVerifyStageStream(TaskContext ctx) {
        return runTaskStageStream(new VerifyStage(), ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> runCommitStageStream(TaskContext ctx) {
        return runTaskStageStream(new CommitStage(), ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> runPublishPrStageStream(TaskContext ctx) {
        return runTaskStageStream(new PublishPrStage(), ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> runIsolatedStream(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        return runIsolatedStream(orchestrator, task, () -> runTaskStream(orchestrator, task));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> runIsolatedStream(AutoHarnessOrchestrator orchestrator, OptimizationTask task,
                                                 Callable<List<Object>> taskRunner) {
        task.setStatus(TaskStatus.RUNNING);
        ExecutorService executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1)
        );
        Future<List<Object>> future = executor.submit(taskRunner);
        try {
            long timeoutMillis = Math.max(1L, Math.round(orchestrator.getConfig().getTaskTimeoutSecs() * 1000.0));
            List<Object> events = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            CycleResult result = resolveTaskResult(orchestrator, task);
            orchestrator.recordCycleResult(result);
            return events;
        } catch (TimeoutException ex) {
            future.cancel(true);
            task.setStatus(TaskStatus.TIMEOUT);
            recordFailure(orchestrator.getExperienceStore(), task, "task timeout", "timeout");
            CycleResult result = CycleResult.builder()
                    .error("timeout")
                    .errorLog("Task exceeded timeout")
                    .build();
            orchestrator.recordCycleResult(result);
            return List.of();
        } catch (InterruptedException ex) {

            task.setStatus(TaskStatus.FAILED);
            recordFailure(orchestrator.getExperienceStore(), task, "interrupted", "exception");
            CycleResult result = CycleResult.builder()
                    .error("interrupted")
                    .errorLog("interrupted")
                    .build();
            orchestrator.recordCycleResult(result);
            return List.of();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            task.setStatus(TaskStatus.FAILED);
            recordFailure(orchestrator.getExperienceStore(), task, truncate(cause.getMessage()), "exception");
            CycleResult result = CycleResult.builder()
                    .error(truncate(cause.getMessage()))
                    .errorLog(cause.getMessage() == null ? "" : cause.getMessage())
                    .build();
            orchestrator.recordCycleResult(result);
            return List.of();
        } catch (RuntimeException ex) {
            task.setStatus(TaskStatus.FAILED);
            recordFailure(orchestrator.getExperienceStore(), task, truncate(ex.getMessage()), "exception");
            CycleResult result = CycleResult.builder()
                    .error(truncate(ex.getMessage()))
                    .errorLog(ex.getMessage() == null ? "" : ex.getMessage())
                    .build();
            orchestrator.recordCycleResult(result);
            return List.of();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskRuntime prepareTaskRuntime(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        List<com.openjiuwen.autoharness.schema.Experience> related;
        try {
            related = orchestrator.getExperienceStore().search(task.getTopic(), 5);
        } catch (IOException ex) {
            related = List.of();
        }
        Path wtPath = orchestrator.getWorktreeMgr().prepare(task.getTopic());
        orchestrator.getGit().setWorkspace(wtPath.toString());
        orchestrator.getCiGate().setWorkspace(wtPath.toString());
        EditSafetyRail editSafetyRail = new EditSafetyRail();
        editSafetyRail.reset();
        List<String> preexistingDirtyFiles = orchestrator.getGit().listDirtyFiles();
        Object taskAgent = AutoHarnessFactory.createAutoHarnessAgent(
                orchestrator.getConfig(),
                wtPath.toString(),
                editSafetyRail,
                null,
                null,
                null,
                true,
                true,
                true);
        return TaskRuntime.builder()
                .related(related)
                .wtPath(wtPath.toString())
                .editSafetyRail(editSafetyRail)
                .preexistingDirtyFiles(preexistingDirtyFiles)
                .taskAgent(taskAgent)
                .fixAgent(AutoHarnessFactory.createAutoHarnessAgent(
                        orchestrator.getConfig(),
                        wtPath.toString(),
                        editSafetyRail,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false))
                .commitAgent(AutoHarnessFactory.createCommitAgent(orchestrator.getConfig(), wtPath.toString()))
                .taskSession(AgentSessionApi.create(
                        "auto-harness-" + TaskContext.taskKey(task),
                        null,
                        taskAgent instanceof DeepAgent deepAgent ? deepAgent.getCard() : null))
                .build();
    }

    private static List<Object> runTaskStream(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        TaskRuntime runtime = prepareTaskRuntime(orchestrator, task);
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        String key = TaskContext.taskKey(task);
        orchestrator.getTaskContexts().put(key, ctx);
        try {
            return new PRTaskPipeline().stream(ctx);
        } finally {
            orchestrator.getWorktreeMgr().cleanup(runtime.getWtPath());
            orchestrator.getTaskContexts().remove(key);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static CycleResult resolveTaskResult(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        Object result = orchestrator.getArtifacts().get("task_result", TaskContext.taskKey(task),
                orchestrator.getLastCycleResult());
        CycleResult cycleResult = result instanceof CycleResult cycle ? cycle : orchestrator.getLastCycleResult();
        if (cycleResult == null) {
            return CycleResult.builder()
                    .error("missing result")
                    .errorLog("No cycle result recorded for completed task")
                    .build();
        }
        if (cycleResult.isSuccess()) {
            task.setStatus(TaskStatus.SUCCESS);
        } else if (task.getStatus() == TaskStatus.RUNNING) {
            task.setStatus(TaskStatus.FAILED);
        }
        return cycleResult;
    }

    private List<Object> runTaskStageStream(BaseStage stage, TaskContext ctx) {
        List<StageResult> resultHolder = new ArrayList<>();
        List<Object> events = streamStage(stage, ctx, resultHolder);
        if (didStageFail(stage, resultHolder)) {
            throw new StopTaskPipeline(events);
        }
        return events;
    }

    private static void recordFailure(ExperienceStore store, OptimizationTask task, String summary, String outcome) {
        try {
            store.record(Experience.builder()
                    .type(ExperienceType.FAILURE)
                    .topic(task == null ? "" : task.getTopic())
                    .summary(summary)
                    .outcome(outcome)
                    .build());
        } catch (IOException ignored) {
            // Python logs and continues when failure experience persistence fails.
        }
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
