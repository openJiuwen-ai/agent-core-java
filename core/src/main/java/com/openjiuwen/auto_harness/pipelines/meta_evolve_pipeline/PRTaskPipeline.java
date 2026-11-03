/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.PipelineStageMap;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Explicit task-scoped pipeline for meta evolve work.
 *
 * <p>Mirrors Python's {@code PRTaskPipeline} and module helpers in
 * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/meta_evolve_task_pipeline.py}.</p>
 */
public class PRTaskPipeline extends BasePipeline {

    private static final Logger LOGGER = Logger.getLogger(PRTaskPipeline.class.getName());

    private static final String TASK_RESULT = "task_result";
    private static final String IMPLEMENT_STAGE_NAME = "meta_implement";
    private static final String VERIFY_STAGE_NAME = "meta_verify";
    private static final String COMMIT_STAGE_NAME = "commit";
    private static final String PUBLISH_STAGE_NAME = "publish_pr";
    private static final String IMPLEMENT_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.MetaImplementStage";
    private static final String VERIFY_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.MetaVerifyStage";
    private static final String COMMIT_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.CommitStage";
    private static final String PUBLISH_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.PublishPRStage";

    private final StageFactory stageFactory;

    public PRTaskPipeline() {
        this(PRTaskPipeline::createDefaultStage);
    }

    PRTaskPipeline(StageFactory stageFactory) {
        this.stageFactory = stageFactory == null ? PRTaskPipeline::createDefaultStage : stageFactory;
    }

    @Override
    public PipelineStageMap stageMap() {
        Map<String, Class<? extends BaseStage>> mapping = new LinkedHashMap<>();
        mapping.put(StageSlot.IMPLEMENT.value(), loadStageClass(IMPLEMENT_STAGE_CLASS));
        mapping.put(StageSlot.VERIFY.value(), loadStageClass(VERIFY_STAGE_CLASS));
        mapping.put(StageSlot.COMMIT.value(), loadStageClass(COMMIT_STAGE_CLASS));
        mapping.put(StageSlot.PUBLISH.value(), loadStageClass(PUBLISH_STAGE_CLASS));
        return new PipelineStageMap(mapping);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("PRTaskPipeline requires a TaskContext");
        }
        return stream(taskContext);
    }

    public Iterator<Object> stream(TaskContext ctx) {
        List<Object> events = new ArrayList<>();
        for (String stageName : List.of(
                IMPLEMENT_STAGE_NAME,
                VERIFY_STAGE_NAME,
                COMMIT_STAGE_NAME,
                PUBLISH_STAGE_NAME
        )) {
            PipelineSegment segment = runStageSegment(stageFactory.create(stageName), ctx);
            events.addAll(segment.events());
            if (segment.stopped()) {
                return events.iterator();
            }
        }
        return events.iterator();
    }

    public Iterator<Object> runImplementStageStream(TaskContext ctx) {
        return runStageSegment(stageFactory.create(IMPLEMENT_STAGE_NAME), ctx).events().iterator();
    }

    public Iterator<Object> runVerifyStageStream(TaskContext ctx) {
        return runStageSegment(stageFactory.create(VERIFY_STAGE_NAME), ctx).events().iterator();
    }

    public Iterator<Object> runCommitStageStream(TaskContext ctx) {
        return runStageSegment(stageFactory.create(COMMIT_STAGE_NAME), ctx).events().iterator();
    }

    public Iterator<Object> runPublishPrStageStream(TaskContext ctx) {
        return runStageSegment(stageFactory.create(PUBLISH_STAGE_NAME), ctx).events().iterator();
    }

    public static Iterator<Object> runIsolatedStream(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task
    ) {
        OptimizationTask currentTask = task == null ? new OptimizationTask() : task;
        currentTask.setStatus(TaskStatus.RUNNING);
        LOGGER.info("Task started: " + nullToEmpty(currentTask.getTopic()));
        List<Object> events = new ArrayList<>();
        CycleResult result;
        try {
            PipelineRun run = runWithTimeout(
                    () -> new PipelineRun(
                            toList(runTaskStream(orchestrator, currentTask)),
                            resolveTaskResult(orchestrator, currentTask)
                    ),
                    remainingTaskTimeout(orchestrator)
            );
            events.addAll(run.events());
            result = run.result();
        } catch (TimeoutException e) {
            currentTask.setStatus(TaskStatus.TIMEOUT);
            LOGGER.severe("Task timed out: " + nullToEmpty(currentTask.getTopic()));
            recordFailureExperience(orchestrator, currentTask.getTopic(), "task timeout", "timeout");
            result = CycleResult.builder()
                    .success(false)
                    .error("timeout")
                    .errorLog("Task exceeded timeout")
                    .build();
        } catch (Exception e) {
            currentTask.setStatus(TaskStatus.FAILED);
            LOGGER.log(Level.SEVERE, "Task failed: " + nullToEmpty(currentTask.getTopic()), e);
            recordFailureExperience(orchestrator, currentTask.getTopic(), truncate(e.toString(), 200), "exception");
            result = CycleResult.builder()
                    .success(false)
                    .error(truncate(e.toString(), 200))
                    .errorLog(e.toString())
                    .build();
        }
        orchestrator.recordCycleResult(result);
        return events.iterator();
    }

    public static Iterator<Object> runTaskStream(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task
    ) {
        TaskRuntime runtime = prepareTaskRuntime(orchestrator, task);
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        String taskId = TaskContext.taskKey(task);
        orchestrator.getTaskContexts().put(taskId, ctx);
        List<Object> events = new ArrayList<>();
        try {
            new PRTaskPipeline().stream(ctx).forEachRemaining(events::add);
            return events.iterator();
        } finally {
            try {
                orchestrator.getWorktreeMgr().cleanup(runtime.getWtPath());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("worktree cleanup interrupted", e);
            } catch (Exception e) {
                throw new IllegalStateException("worktree cleanup failed", e);
            } finally {
                orchestrator.getTaskContexts().remove(taskId);
            }
        }
    }

    public static TaskRuntime prepareTaskRuntime(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task
    ) {
        String topic = task == null ? "" : nullToEmpty(task.getTopic());
        List<Experience> related = orchestrator.getExperienceStore().search(topic).join();
        String wtPath;
        try {
            wtPath = orchestrator.getWorktreeMgr().prepare(topic);
            orchestrator.getGit().setWorkspace(wtPath);
            orchestrator.getCiGate().setWorkspace(wtPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("task runtime prepare interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("task runtime prepare failed", e);
        }

        EditSafetyRail editSafetyRail = new EditSafetyRail();
        editSafetyRail.reset();
        List<String> preexistingDirtyFiles;
        try {
            preexistingDirtyFiles = orchestrator.getGit().listDirtyFiles();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("dirty file listing interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("dirty file listing failed", e);
        }

        List<DeepAgentRail> extraRails = deepRails(orchestrator);
        DeepAgent taskAgent = AutoHarnessAgentFactory.createAutoHarnessAgent(
                orchestrator.getConfig(),
                wtPath,
                editSafetyRail,
                true,
                null,
                true,
                true,
                true,
                extraRails,
                null
        );
        DeepAgent fixAgent = AutoHarnessAgentFactory.createAutoHarnessAgent(
                orchestrator.getConfig(),
                wtPath,
                editSafetyRail,
                true,
                null,
                false,
                false,
                false,
                extraRails,
                null
        );
        DeepAgent commitAgent = AutoHarnessAgentFactory.createCommitAgent(
                orchestrator.getConfig(),
                wtPath,
                extraRails
        );
        String leaf = Path.of(wtPath).getFileName() == null ? wtPath : Path.of(wtPath).getFileName().toString();
        AgentSession taskSession = new AgentSession(
                "auto-harness-" + leaf,
                null,
                taskAgent.getCard(),
                null,
                false,
                null
        );

        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(related);
        runtime.setWtPath(wtPath);
        runtime.setEditSafetyRail(editSafetyRail);
        runtime.setPreexistingDirtyFiles(preexistingDirtyFiles);
        runtime.setTaskAgent(taskAgent);
        runtime.setCommitAgent(commitAgent);
        runtime.setTaskSession(taskSession);
        runtime.setFixAgent(fixAgent);
        return runtime;
    }

    public static CycleResult resolveTaskResult(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task
    ) {
        Object result = orchestrator.getArtifacts().get(TASK_RESULT, TaskContext.taskKey(task));
        CycleResult cycleResult;
        if (result instanceof CycleResult typed) {
            cycleResult = typed;
        } else {
            CycleResult lastResult = orchestrator.getLastCycleResult();
            cycleResult = isMeaningful(lastResult) ? lastResult : CycleResult.builder()
                    .success(false)
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

    public static double remainingTaskTimeout(AutoHarnessOrchestrator orchestrator) {
        return Math.max(
                1.0,
                Math.min(
                        orchestrator.getConfig().getTaskTimeoutSecs(),
                        orchestrator.getBudget().getRemainingSecs()
                )
        );
    }

    @FunctionalInterface
    interface StageFactory {
        BaseStage create(String stageName);
    }

    private PipelineSegment runStageSegment(BaseStage stage, TaskContext ctx) {
        List<StageResult> resultHolder = new ArrayList<>();
        List<Object> events = toList(streamStage(stage, ctx, resultHolder));
        return new PipelineSegment(events, didStageFail(stage, resultHolder));
    }

    private static BaseStage createDefaultStage(String stageName) {
        return switch (stageName) {
            case IMPLEMENT_STAGE_NAME -> instantiateStage(
                    IMPLEMENT_STAGE_CLASS,
                    new DeferredTaskStage(IMPLEMENT_STAGE_NAME, StageSlot.IMPLEMENT.value())
            );
            case VERIFY_STAGE_NAME -> instantiateStage(
                    VERIFY_STAGE_CLASS,
                    new DeferredTaskStage(VERIFY_STAGE_NAME, StageSlot.VERIFY.value())
            );
            case COMMIT_STAGE_NAME -> instantiateStage(
                    COMMIT_STAGE_CLASS,
                    new DeferredTaskStage(COMMIT_STAGE_NAME, StageSlot.COMMIT.value())
            );
            case PUBLISH_STAGE_NAME -> instantiateStage(
                    PUBLISH_STAGE_CLASS,
                    new DeferredTaskStage(PUBLISH_STAGE_NAME, StageSlot.PUBLISH.value())
            );
            default -> new DeferredTaskStage(stageName, stageName);
        };
    }

    private static Class<? extends BaseStage> loadStageClass(String className) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class);
        } catch (ClassNotFoundException e) {
            return DeferredTaskStage.class;
        }
    }

    private static BaseStage instantiateStage(String className, BaseStage fallback) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static List<DeepAgentRail> deepRails(AutoHarnessOrchestrator orchestrator) {
        List<DeepAgentRail> rails = new ArrayList<>();
        for (AgentRail rail : orchestrator.getStreamRails()) {
            rails.add(AutoHarnessAgentFactory.bridge(rail));
        }
        return rails;
    }

    private static PipelineRun runWithTimeout(Callable<PipelineRun> callable, double timeoutSecs)
            throws Exception {
        ExecutorService executor = OpenJiuwenExecutors.newSingleThreadExecutor("autoharness-task", false);
        Future<PipelineRun> future = executor.submit(callable);
        try {
            long timeoutMillis = Math.max(1L, (long) Math.ceil(timeoutSecs * 1000.0));
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> events = new ArrayList<>();
        if (iterator != null) {
            iterator.forEachRemaining(events::add);
        }
        return events;
    }

    private static void recordFailureExperience(
            AutoHarnessOrchestrator orchestrator,
            String topic,
            String summary,
            String outcome
    ) {
        orchestrator.getExperienceStore().record(Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic(topic)
                .summary(summary)
                .outcome(outcome)
                .build()).join();
    }

    private static boolean isMeaningful(CycleResult result) {
        return result != null
                && (result.isSuccess()
                || result.isReverted()
                || !isBlank(result.getSummary())
                || !isBlank(result.getPrUrl())
                || !isBlank(result.getError())
                || !isBlank(result.getErrorLog()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxChars) {
        String raw = value == null ? "" : value;
        return raw.length() <= maxChars ? raw : raw.substring(0, maxChars);
    }

    private record PipelineSegment(List<Object> events, boolean stopped) {
    }

    private record PipelineRun(List<Object> events, CycleResult result) {
    }

    /**
     * Deferred stage binding used until same-batch stage tasks provide concrete
     * task-stage implementations.
     *
     * <p>Mirrors Python's stage import dependency in
     * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/meta_evolve_task_pipeline.py}.</p>
     */
    public static class DeferredTaskStage extends BaseStage {

        private final String name;
        private final String slot;

        public DeferredTaskStage() {
            this("", "");
        }

        DeferredTaskStage(String name, String slot) {
            this.name = name == null ? "" : name;
            this.slot = slot == null ? "" : slot;
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
                    .status("failed")
                    .error("Stage unavailable: " + name)
                    .messages(List.of("Stage unavailable: " + name))
                    .build();
            return List.of((Object) result).iterator();
        }
    }
}
