/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.PublishPrStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Explicit task-scoped pipeline for meta evolve work.
 *
 * <p>Mirrors Python's {@code PRTaskPipeline} in {@code openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.meta_evolve_task_pipeline}.</p>
 */
public class PRTaskPipeline extends BasePipeline {

    private static final Logger logger = Logger.getLogger(PRTaskPipeline.class.getName());
    private static volatile RuntimeDependencies runtimeDependencies = new DefaultRuntimeDependencies();
    private static volatile TaskStreamRunner taskStreamRunner = PRTaskPipeline::runTaskStreamDefault;

    public interface RuntimeDependencies {
        List<Experience> searchRelated(AutoHarnessOrchestrator orchestrator, OptimizationTask task);

        String prepareWorktree(AutoHarnessOrchestrator orchestrator, OptimizationTask task);

        void setWorkspace(AutoHarnessOrchestrator orchestrator, String worktreePath);

        List<String> listDirtyFiles(AutoHarnessOrchestrator orchestrator);

        Object createTaskAgent(AutoHarnessConfig config, String workspaceOverride, Object editSafetyRail,
                boolean enableTaskLoop, boolean enableTaskPlanning, boolean enableProgressRepeat);

        Object createCommitAgent(AutoHarnessConfig config, String workspaceOverride);

        Object createTaskSession(String sessionId, Object taskAgent);

        void cleanup(AutoHarnessOrchestrator orchestrator, String worktreePath);
    }

    @FunctionalInterface
    public interface TaskStreamRunner {
        void run(AutoHarnessOrchestrator orchestrator, OptimizationTask task, Consumer<Object> eventSink)
                throws Exception;
    }

    public static void setRuntimeDependencies(RuntimeDependencies dependencies) {
        runtimeDependencies = dependencies != null ? dependencies : new DefaultRuntimeDependencies();
    }

    public static void resetRuntimeDependencies() {
        runtimeDependencies = new DefaultRuntimeDependencies();
    }

    public static void setTaskStreamRunner(TaskStreamRunner runner) {
        taskStreamRunner = runner != null ? runner : PRTaskPipeline::runTaskStreamDefault;
    }

    public static void resetTaskStreamRunner() {
        taskStreamRunner = PRTaskPipeline::runTaskStreamDefault;
    }

    @Override
    public String name() {
        return "pr_task";
    }

    @Override
    public String description() {
        return "Task-scoped pipeline for PR workflow: implement -> verify -> commit -> publish.";
    }

    @Override
    public List<String> expectedOutputs() {
        return List.of("cycle_result");
    }

    /**
     * Execute the pipeline stages in sequence.
     *
     * @param ctx       the task context
     * @param eventSink the callback for events
     */
    @Override
    public void execute(TaskContext ctx, Consumer<Object> eventSink) {
        try {
            runImplementStage(ctx, eventSink);
            runVerifyStage(ctx, eventSink);
            runCommitStage(ctx, eventSink);
            runPublishPrStage(ctx, eventSink);
        } catch (StopTaskPipelineException e) {
            logger.info("Task pipeline stopped due to stage failure: " + ctx.getTask().getTopic());
        }
    }

    /**
     * Run the implement stage.
     *
     * @param ctx       the task context
     * @param eventSink the event callback
     */
    protected void runImplementStage(TaskContext ctx, Consumer<Object> eventSink) {
        ImplementStage stage = new ImplementStage();
        List<com.openjiuwen.auto_harness.schema.StageResult> resultHolder = new ArrayList<>();
        streamStage(stage, ctx, resultHolder, eventSink);
        if (didStageFail(stage, resultHolder)) {
            throw new StopTaskPipelineException();
        }
    }

    /**
     * Run the verify stage.
     *
     * @param ctx       the task context
     * @param eventSink the event callback
     */
    protected void runVerifyStage(TaskContext ctx, Consumer<Object> eventSink) {
        VerifyStage stage = new VerifyStage();
        List<com.openjiuwen.auto_harness.schema.StageResult> resultHolder = new ArrayList<>();
        streamStage(stage, ctx, resultHolder, eventSink);
        if (didStageFail(stage, resultHolder)) {
            throw new StopTaskPipelineException();
        }
    }

    /**
     * Run the commit stage.
     *
     * @param ctx       the task context
     * @param eventSink the event callback
     */
    protected void runCommitStage(TaskContext ctx, Consumer<Object> eventSink) {
        CommitStage stage = new CommitStage();
        List<com.openjiuwen.auto_harness.schema.StageResult> resultHolder = new ArrayList<>();
        streamStage(stage, ctx, resultHolder, eventSink);
        if (didStageFail(stage, resultHolder)) {
            throw new StopTaskPipelineException();
        }
    }

    /**
     * Run the publish PR stage.
     *
     * @param ctx       the task context
     * @param eventSink the event callback
     */
    protected void runPublishPrStage(TaskContext ctx, Consumer<Object> eventSink) {
        PublishPrStage stage = new PublishPrStage();
        List<com.openjiuwen.auto_harness.schema.StageResult> resultHolder = new ArrayList<>();
        streamStage(stage, ctx, resultHolder, eventSink);
        if (didStageFail(stage, resultHolder)) {
            throw new StopTaskPipelineException();
        }
    }

    /**
     * Prepare task runtime with worktree, agents, and rails.
     *
     * @param orchestrator the orchestrator
     * @param task         the optimization task
     * @return the task runtime
     */
    public static TaskRuntime prepareTaskRuntime(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        RuntimeDependencies dependencies = runtimeDependencies;
        List<Experience> related = dependencies.searchRelated(orchestrator, task);
        String wtPath = dependencies.prepareWorktree(orchestrator, task);
        dependencies.setWorkspace(orchestrator, wtPath);
        EditSafetyRail editSafetyRail = new EditSafetyRail();
        editSafetyRail.reset();
        List<String> preexistingDirtyFiles = dependencies.listDirtyFiles(orchestrator);
        Object taskAgent = dependencies.createTaskAgent(
                orchestrator.getConfig(), wtPath, editSafetyRail, true, true, true);
        Object fixAgent = dependencies.createTaskAgent(
                orchestrator.getConfig(), wtPath, editSafetyRail, false, false, false);
        Object commitAgent = dependencies.createCommitAgent(orchestrator.getConfig(), wtPath);
        String sessionId = "auto-harness-" + worktreeName(wtPath);
        Object taskSession = dependencies.createTaskSession(sessionId, taskAgent);

        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(related);
        runtime.setWtPath(wtPath);
        runtime.setEditSafetyRail(editSafetyRail);
        runtime.setPreexistingDirtyFiles(preexistingDirtyFiles);
        runtime.setTaskAgent(taskAgent);
        runtime.setFixAgent(fixAgent);
        runtime.setCommitAgent(commitAgent);
        runtime.setTaskSession(taskSession);
        return runtime;
    }

    /**
     * Run a task in isolation with timeout protection.
     *
     * @param orchestrator the orchestrator
     * @param task         the optimization task
     * @param eventSink    the event callback
     * @return the cycle result
     */
    public static CycleResult runIsolated(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task,
            Consumer<Object> eventSink
    ) {
        task.setStatus(TaskStatus.RUNNING);
        logger.info("Task started: " + task.getTopic());

        CycleResult result;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<CycleResult> callable = () -> {
                taskStreamRunner.run(orchestrator, task, eventSink);
                return resolveTaskResult(orchestrator, task);
            };
            Future<CycleResult> future = executor.submit(callable);
            long timeoutMillis = Math.max(1L, Math.round(orchestrator.getConfig().getTaskTimeoutSecs() * 1000.0));
            result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.setStatus(TaskStatus.TIMEOUT);
            logger.severe("Task timed out: " + task.getTopic());
            Experience experience = new Experience();
            experience.setType(ExperienceType.FAILURE);
            experience.setTopic(task.getTopic());
            experience.setSummary("task timeout");
            experience.setOutcome("timeout");
            orchestrator.getExperienceStore().record(experience);

            result = new CycleResult();
            result.setTaskId(TaskContext.taskKey(task));
            result.setStatus(TaskStatus.TIMEOUT);
            result.setError("timeout");
            result.setErrorLog("Task exceeded timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            result = failedResult(orchestrator, task, cause);
        } catch (Exception e) {
            result = failedResult(orchestrator, task, e);
        } finally {
            executor.shutdownNow();
        }
        orchestrator.recordCycleResult(result);
        return result;
    }

    private static CycleResult failedResult(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task,
            Throwable throwable) {
            task.setStatus(TaskStatus.FAILED);
            String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
            logger.severe("Task failed: " + task.getTopic() + " - " + message);

            Experience experience = new Experience();
            experience.setType(ExperienceType.FAILURE);
            experience.setTopic(task.getTopic());
            experience.setSummary(message.length() > 200 ? message.substring(0, 200) : message);
            experience.setOutcome("exception");
            orchestrator.getExperienceStore().record(experience);

            CycleResult result = new CycleResult();
            result.setTaskId(TaskContext.taskKey(task));
            result.setStatus(TaskStatus.FAILED);
            result.setError(message.length() > 200 ? message.substring(0, 200) : message);
            result.setErrorLog(message);
            return result;
    }

    private static void runTaskStreamDefault(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task,
            Consumer<Object> eventSink) {
        TaskRuntime runtime = prepareTaskRuntime(orchestrator, task);
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        String taskKey = TaskContext.taskKey(task);
        orchestrator.getTaskContexts().put(taskKey, ctx);
        try {
            PRTaskPipeline pipeline = new PRTaskPipeline();
            pipeline.execute(ctx, eventSink);
        } finally {
            runtimeDependencies.cleanup(orchestrator, runtime.getWtPath());
            orchestrator.getTaskContexts().remove(taskKey);
        }
    }

    /**
     * Resolve the final task result.
     *
     * @param orchestrator the orchestrator
     * @param task         the optimization task
     * @return the cycle result
     */
    public static CycleResult resolveTaskResult(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        Object raw = orchestrator.getArtifacts().get("task_result", TaskContext.taskKey(task), null);
        CycleResult result = raw instanceof CycleResult cycleResult
                ? cycleResult
                : orchestrator.getLastCycleResult();
        if (result == null) {
            result = new CycleResult();
            result.setTaskId(TaskContext.taskKey(task));
            result.setStatus(TaskStatus.FAILED);
            result.setError("missing result");
            result.setErrorLog("No cycle result recorded for completed task");
            return result;
        }
        if (result.isSuccess()) {
            task.setStatus(TaskStatus.SUCCESS);
        } else if (task.getStatus() == TaskStatus.RUNNING) {
            task.setStatus(TaskStatus.FAILED);
        }
        return result;
    }

    private static String worktreeName(String wtPath) {
        if (wtPath == null || wtPath.isBlank()) {
            return "task";
        }
        Path name = Path.of(wtPath).getFileName();
        return name != null ? name.toString() : "task";
    }

    private static Object cardOf(Object agent) {
        if (agent == null) {
            return null;
        }
        try {
            Method method = agent.getClass().getMethod("getCard");
            return method.invoke(agent);
        } catch (ReflectiveOperationException ignored) {
            try {
                return agent.getClass().getField("card").get(agent);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static final class DefaultRuntimeDependencies implements RuntimeDependencies {
        @Override
        public List<Experience> searchRelated(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
            return orchestrator.getExperienceStore().search(task.getTopic());
        }

        @Override
        public String prepareWorktree(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
            return orchestrator.getWorktreeMgr().prepare(task.getTopic());
        }

        @Override
        public void setWorkspace(AutoHarnessOrchestrator orchestrator, String worktreePath) {
            orchestrator.getGit().setWorkspace(worktreePath);
            orchestrator.getCiGate().setWorkspace(worktreePath);
        }

        @Override
        public List<String> listDirtyFiles(AutoHarnessOrchestrator orchestrator) {
            try {
                return orchestrator.getGit().listDirtyFiles();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Object createTaskAgent(AutoHarnessConfig config, String workspaceOverride, Object editSafetyRail,
                boolean enableTaskLoop, boolean enableTaskPlanning, boolean enableProgressRepeat) {
            AgentRail rail = editSafetyRail instanceof AgentRail agentRail ? agentRail : null;
            return AutoHarnessAgentFactory.createAutoHarnessAgent(
                    config,
                    workspaceOverride,
                    rail,
                    null,
                    enableTaskLoop,
                    enableTaskPlanning,
                    enableProgressRepeat,
                    null,
                    null
            );
        }

        @Override
        public Object createCommitAgent(AutoHarnessConfig config, String workspaceOverride) {
            return AutoHarnessAgentFactory.createCommitAgent(config, workspaceOverride);
        }

        @Override
        public Object createTaskSession(String sessionId, Object taskAgent) {
            return AgentSessionApi.create(sessionId, null, cardOf(taskAgent));
        }

        @Override
        public void cleanup(AutoHarnessOrchestrator orchestrator, String worktreePath) {
            orchestrator.getWorktreeMgr().cleanup(worktreePath);
        }
    }

    /**
     * Exception to stop the task pipeline after a failed stage.
     */
    public static class StopTaskPipelineException extends RuntimeException {
        public StopTaskPipelineException() {
            super("Task pipeline stopped due to stage failure");
        }
    }
}
