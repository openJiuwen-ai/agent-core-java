/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.PublishPrStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Explicit task-scoped pipeline for meta evolve work.
 *
 * <p>Mirrors Python's {@code PRTaskPipeline} in {@code openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.meta_evolve_task_pipeline}.</p>
 */
public class PRTaskPipeline extends BasePipeline {

    private static final Logger logger = Logger.getLogger(PRTaskPipeline.class.getName());

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
        // TODO: Implement async worktree preparation, agent creation, etc.
        // This requires async framework integration
        TaskRuntime runtime = new TaskRuntime();
        runtime.setWtPath(orchestrator.getWorktreeMgr().prepareSync(task.getTopic()));
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

        try {
            // Prepare runtime
            TaskRuntime runtime = prepareTaskRuntime(orchestrator, task);
            TaskContext ctx = new TaskContext(orchestrator, task, runtime);

            // Execute pipeline
            PRTaskPipeline pipeline = new PRTaskPipeline();
            pipeline.execute(ctx, eventSink);

            // Resolve result
            return resolveTaskResult(orchestrator, task);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            logger.severe("Task failed: " + task.getTopic() + " - " + e.getMessage());

            // Record failure experience
            Experience experience = new Experience();
            experience.setType(ExperienceType.FAILURE);
            experience.setTopic(task.getTopic());
            experience.setSummary("Task failed: " + e.getMessage());
            orchestrator.getExperienceStore().record(experience);

            CycleResult result = new CycleResult();
            result.setTaskId(TaskContext.taskKey(task));
            result.setStatus(TaskStatus.FAILED);
            result.setError(e.getMessage());
            return result;
        }
    }

    /**
     * Resolve the final task result.
     *
     * @param orchestrator the orchestrator
     * @param task         the optimization task
     * @return the cycle result
     */
    protected static CycleResult resolveTaskResult(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
        CycleResult result = new CycleResult();
        result.setTaskId(TaskContext.taskKey(task));
        result.setStatus(TaskStatus.SUCCESS);
        return result;
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