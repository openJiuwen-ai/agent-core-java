/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.PipelineStageMap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.single_agent.rail.AgentRail;
import com.openjiuwen.harness.DeepAgent;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build, verify, commit, and publish PR for one runtime extension.
 *
 * <p>Mirrors Python's {@code ExtensionTaskPipeline} and module helpers in
 * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extension_task_pipeline.py}.</p>
 */
public class ExtensionTaskPipeline extends BasePipeline {

    private static final Logger LOGGER = Logger.getLogger(ExtensionTaskPipeline.class.getName());

    private static final String EXTENSION_TARGET = "extension_target";
    private static final String TASK_RESULT = "task_result";
    private static final String RUNTIME_EXTENSION = "runtime_extension";
    private static final String IMPLEMENT_STAGE_NAME = "implement_ext";
    private static final String VERIFY_STAGE_NAME = "verify_ext";
    private static final String ACTIVATE_STAGE_NAME = "activate_ext";
    private static final String IMPLEMENT_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.ExtendImplementStage";
    private static final String VERIFY_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.ExtendVerifyStage";
    private static final String ACTIVATE_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.ExtendActivateStage";

    private final StageFactory stageFactory;

    public ExtensionTaskPipeline() {
        this(ExtensionTaskPipeline::createDefaultStage);
    }

    ExtensionTaskPipeline(StageFactory stageFactory) {
        this.stageFactory = stageFactory == null ? ExtensionTaskPipeline::createDefaultStage : stageFactory;
    }

    @Override
    public PipelineStageMap stageMap() {
        Map<String, Class<? extends BaseStage>> mapping = new LinkedHashMap<>();
        mapping.put(StageSlot.IMPLEMENT.value(), loadStageClass(IMPLEMENT_STAGE_CLASS));
        mapping.put(StageSlot.VERIFY.value(), loadStageClass(VERIFY_STAGE_CLASS));
        mapping.put(StageSlot.ACTIVATE.value(), loadStageClass(ACTIVATE_STAGE_CLASS));
        return new PipelineStageMap(mapping);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("ExtensionTaskPipeline requires a TaskContext");
        }
        return stream(taskContext);
    }

    public Iterator<Object> stream(TaskContext ctx) {
        List<Object> events = new ArrayList<>();
        ExtensionDesign design = requireExtensionDesign(ctx);
        LOGGER.info(() -> "[AutoHarnessExtensionTask] pipeline start: extension="
                + design.getExtensionName() + " task=" + topic(ctx));
        if (appendStageEvents(stageFactory.create(IMPLEMENT_STAGE_NAME), ctx, events)
                || appendStageEvents(stageFactory.create(VERIFY_STAGE_NAME), ctx, events)
                || appendStageEvents(stageFactory.create(ACTIVATE_STAGE_NAME), ctx, events)) {
            logPipelineStopped("pipeline", design, ctx);
            return events.iterator();
        }

        if (ctx.getArtifact(TASK_RESULT) == null) {
            ctx.putArtifact(TASK_RESULT, CycleResult.builder()
                    .success(true)
                    .summary("Extension activated: " + design.getExtensionName())
                    .build());
        }
        events.add(BaseExecutionContext.message("Extension activated: " + design.getExtensionName()));
        LOGGER.info("[AutoHarnessExtensionTask] pipeline success: extension="
                + design.getExtensionName() + " task=" + topic(ctx));
        return events.iterator();
    }

    public Iterator<Object> runBuildVerifyStream(TaskContext ctx) {
        List<Object> events = new ArrayList<>();
        ExtensionDesign design = requireExtensionDesign(ctx);
        LOGGER.info(() -> "[AutoHarnessExtensionTask] build/verify start: extension="
                + design.getExtensionName() + " task=" + topic(ctx));
        if (appendStageEvents(stageFactory.create(IMPLEMENT_STAGE_NAME), ctx, events)
                || appendStageEvents(stageFactory.create(VERIFY_STAGE_NAME), ctx, events)) {
            logPipelineStopped("build/verify", design, ctx);
            return events.iterator();
        }
        LOGGER.info("[AutoHarnessExtensionTask] build/verify success: extension="
                + design.getExtensionName() + " task=" + topic(ctx));
        return events.iterator();
    }

    public Iterator<Object> runActivateStageStream(TaskContext ctx) {
        List<Object> events = new ArrayList<>();
        ExtensionDesign design = requireExtensionDesign(ctx);
        if (appendStageEvents(stageFactory.create(ACTIVATE_STAGE_NAME), ctx, events)) {
            logPipelineStopped("activate", design, ctx);
            return events.iterator();
        }
        if (ctx.getArtifact(TASK_RESULT) == null) {
            ctx.putArtifact(TASK_RESULT, CycleResult.builder()
                    .success(true)
                    .summary("Extension activated: " + design.getExtensionName())
                    .build());
        }
        events.add(BaseExecutionContext.message("Extension activated: " + design.getExtensionName()));
        return events.iterator();
    }

    Iterator<Object> runStageStream(BaseStage stage, TaskContext ctx) {
        List<Object> events = new ArrayList<>();
        ExtensionDesign design = requireExtensionDesign(ctx);
        double startedAt = monotonicSeconds();
        LOGGER.info(() -> "[AutoHarnessExtensionTask] stage begin: extension="
                + design.getExtensionName() + " stage=" + stage.name() + " task=" + topic(ctx));
        events.add(extensionStageOutput(ctx, stage, "running", "", null));
        List<StageResult> resultHolder = new ArrayList<>();
        try {
            Iterator<Object> stream = stage.stream(ctx);
            while (stream.hasNext()) {
                Object event = stream.next();
                if (event instanceof StageResult result) {
                    resultHolder.add(result);
                    if (result.getArtifacts() != null && !result.getArtifacts().isEmpty()) {
                        ctx.putArtifacts(result.getArtifacts());
                    }
                    for (String message : result.getMessages() == null ? List.<String>of() : result.getMessages()) {
                        events.add(BaseExecutionContext.message(message, parentStage(stage)));
                    }
                    continue;
                }
                events.add(event);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "[AutoHarnessExtensionTask] stage exception: extension="
                    + design.getExtensionName() + " stage=" + stage.name() + " task=" + topic(ctx), e);
            throw e;
        }

        if (!didStageFail(stage, resultHolder)) {
            LOGGER.info("[AutoHarnessExtensionTask] stage success: extension="
                    + design.getExtensionName() + " stage=" + stage.name()
                    + " elapsed=" + String.format("%.1fs", monotonicSeconds() - startedAt));
            events.add(extensionStageOutput(ctx, stage, "success", "", null));
            return events.iterator();
        }

        StageResult result = requireStageResult(stage, resultHolder);
        String error = isBlank(result.getError()) ? "Stage failed: " + stage.name() : result.getError();
        LOGGER.warning("[AutoHarnessExtensionTask] stage failed: extension="
                + design.getExtensionName() + " stage=" + stage.name()
                + " elapsed=" + String.format("%.1fs", monotonicSeconds() - startedAt)
                + " error=" + error + " messages=" + result.getMessages());
        if (ctx.getArtifact(TASK_RESULT) == null) {
            ctx.putArtifact(TASK_RESULT, CycleResult.builder()
                    .success(false)
                    .error(error)
                    .build());
        }
        events.add(extensionStageOutput(
                ctx,
                stage,
                "failed",
                error,
                result.getMessages() == null ? List.of() : result.getMessages()
        ));
        return events.iterator();
    }

    public static Iterator<Object> runIsolatedStream(
            AutoHarnessOrchestrator orchestrator,
            ExtensionDesign design
    ) {
        OptimizationTask task = buildExtensionTask(design);
        task.setStatus(TaskStatus.RUNNING);
        double timeoutSecs = remainingTaskTimeout(orchestrator);
        double startedAt = monotonicSeconds();
        LOGGER.info("[AutoHarnessExtensionTask] task started: extension=" + design.getExtensionName()
                + " kind=" + design.getKind()
                + " depends_on=" + design.getDependsOn()
                + " timeout=" + timeoutSecs
                + " session_remaining=" + orchestrator.getBudget().getRemainingSecs()
                + " task_timeout=" + orchestrator.getConfig().getTaskTimeoutSecs());
        List<Object> events = new ArrayList<>();
        CycleResult result;
        try {
            PipelineRun run = runWithTimeout(() -> {
                List<Object> chunks = toList(runTaskStream(
                        orchestrator,
                        task,
                        design,
                        true,
                        true,
                        null
                ));
                return new PipelineRun(chunks, resolveTaskResult(orchestrator, task));
            }, timeoutSecs);
            events.addAll(run.events());
            result = run.result();
        } catch (TimeoutException e) {
            task.setStatus(TaskStatus.TIMEOUT);
            LOGGER.severe("[AutoHarnessExtensionTask] task timed out: extension=" + design.getExtensionName()
                    + " elapsed=" + String.format("%.1fs", monotonicSeconds() - startedAt)
                    + " timeout=" + timeoutSecs
                    + " session_remaining=" + orchestrator.getBudget().getRemainingSecs()
                    + " task_timeout=" + orchestrator.getConfig().getTaskTimeoutSecs());
            recordFailureExperience(orchestrator, task.getTopic(), "task timeout", "timeout");
            result = CycleResult.builder()
                    .success(false)
                    .error("timeout")
                    .errorLog("Extension task exceeded timeout")
                    .build();
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            LOGGER.log(Level.SEVERE, "[AutoHarnessExtensionTask] task exception: extension="
                    + design.getExtensionName() + " elapsed="
                    + String.format("%.1fs", monotonicSeconds() - startedAt), e);
            recordFailureExperience(orchestrator, task.getTopic(), truncate(e.toString(), 200), "exception");
            result = CycleResult.builder()
                    .success(false)
                    .error(truncate(e.toString(), 200))
                    .errorLog(e.toString())
                    .build();
        }

        orchestrator.recordCycleResult(result);
        LOGGER.info("[AutoHarnessExtensionTask] task finished: extension=" + design.getExtensionName()
                + " success=" + result.isSuccess()
                + " status=" + task.getStatus()
                + " elapsed=" + String.format("%.1fs", monotonicSeconds() - startedAt)
                + " error=" + nullToEmpty(result.getError())
                + " results_total=" + orchestrator.getResults().size());
        return events.iterator();
    }

    public static Iterator<Object> runBuildVerifyIsolatedStream(
            AutoHarnessOrchestrator orchestrator,
            ExtensionDesign design,
            List<VerifiedExtensionTask> verifiedTasks
    ) {
        OptimizationTask task = buildExtensionTask(design);
        task.setStatus(TaskStatus.RUNNING);
        double timeoutSecs = remainingTaskTimeout(orchestrator);
        double startedAt = monotonicSeconds();
        LOGGER.info("[AutoHarnessExtensionTask] build/verify task started: extension="
                + design.getExtensionName() + " kind=" + design.getKind()
                + " depends_on=" + design.getDependsOn()
                + " timeout=" + timeoutSecs);
        List<TaskContext> ctxHolder = new ArrayList<>();
        List<Object> events = new ArrayList<>();
        CycleResult result;
        try {
            PipelineRun run = runWithTimeout(() -> {
                List<Object> chunks = toList(runTaskStream(
                        orchestrator,
                        task,
                        design,
                        false,
                        false,
                        ctxHolder
                ));
                return new PipelineRun(chunks, resolveBuildVerifyResult(orchestrator, task));
            }, timeoutSecs);
            events.addAll(run.events());
            result = run.result();
        } catch (TimeoutException e) {
            task.setStatus(TaskStatus.TIMEOUT);
            LOGGER.severe("[AutoHarnessExtensionTask] build/verify timed out: extension="
                    + design.getExtensionName() + " elapsed="
                    + String.format("%.1fs", monotonicSeconds() - startedAt)
                    + " timeout=" + timeoutSecs);
            recordFailureExperience(orchestrator, task.getTopic(), "task timeout", "timeout");
            result = CycleResult.builder()
                    .success(false)
                    .error("timeout")
                    .errorLog("Extension build/verify exceeded timeout")
                    .build();
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            LOGGER.log(Level.SEVERE, "[AutoHarnessExtensionTask] build/verify exception: extension="
                    + design.getExtensionName() + " elapsed="
                    + String.format("%.1fs", monotonicSeconds() - startedAt), e);
            recordFailureExperience(orchestrator, task.getTopic(), truncate(e.toString(), 200), "exception");
            result = CycleResult.builder()
                    .success(false)
                    .error(truncate(e.toString(), 200))
                    .errorLog(e.toString())
                    .build();
        }

        if (result.isSuccess()) {
            task.setStatus(TaskStatus.SUCCESS);
            if (verifiedTasks != null && !ctxHolder.isEmpty()) {
                verifiedTasks.add(new VerifiedExtensionTask(design, task, ctxHolder.get(ctxHolder.size() - 1)));
            }
        } else {
            if (task.getStatus() == TaskStatus.RUNNING) {
                task.setStatus(TaskStatus.FAILED);
            }
            orchestrator.recordCycleResult(result);
        }
        LOGGER.info("[AutoHarnessExtensionTask] build/verify finished: extension="
                + design.getExtensionName() + " success=" + result.isSuccess()
                + " status=" + task.getStatus()
                + " elapsed=" + String.format("%.1fs", monotonicSeconds() - startedAt)
                + " error=" + nullToEmpty(result.getError())
                + " results_total=" + orchestrator.getResults().size());
        return events.iterator();
    }

    public static Iterator<Object> runBuildVerifyIsolatedStream(
            AutoHarnessOrchestrator orchestrator,
            ExtensionDesign design
    ) {
        return runBuildVerifyIsolatedStream(orchestrator, design, null);
    }

    public static Iterator<Object> runActivateStream(
            AutoHarnessOrchestrator orchestrator,
            VerifiedExtensionTask verified
    ) {
        List<Object> events = toList(new ExtensionTaskPipeline().runActivateStageStream(verified.ctx()));
        CycleResult result = resolveTaskResult(orchestrator, verified.task());
        orchestrator.recordCycleResult(result);
        return events.iterator();
    }

    static Iterator<Object> runTaskStream(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task,
            ExtensionDesign design,
            boolean includeActivate,
            boolean configureSharedWorkspace,
            List<TaskContext> ctxHolder
    ) {
        LOGGER.info("[AutoHarnessExtensionTask] runtime setup begin: extension="
                + design.getExtensionName() + " task=" + task.getTopic());
        TaskRuntime runtime = prepareExtensionTaskRuntime(
                orchestrator,
                design,
                configureSharedWorkspace
        );
        LOGGER.info("[AutoHarnessExtensionTask] runtime setup done: extension="
                + design.getExtensionName() + " wt_path=" + runtime.getWtPath());
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        ctx.putArtifact(EXTENSION_TARGET, design);
        orchestrator.getTaskContexts().put(TaskContext.taskKey(task), ctx);
        if (ctxHolder != null) {
            ctxHolder.add(ctx);
        }
        List<Object> events = new ArrayList<>();
        try {
            ExtensionTaskPipeline pipeline = new ExtensionTaskPipeline();
            Iterator<Object> stream = includeActivate ? pipeline.stream(ctx) : pipeline.runBuildVerifyStream(ctx);
            stream.forEachRemaining(events::add);
            return events.iterator();
        } finally {
            LOGGER.info("[AutoHarnessExtensionTask] cleanup begin: extension="
                    + design.getExtensionName() + " wt_path=" + runtime.getWtPath());
            try {
                orchestrator.getWorktreeMgr().cleanup(runtime.getWtPath());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("worktree cleanup interrupted", e);
            } catch (Exception e) {
                throw new IllegalStateException("worktree cleanup failed", e);
            } finally {
                orchestrator.getTaskContexts().remove(TaskContext.taskKey(task));
                LOGGER.info("[AutoHarnessExtensionTask] cleanup done: extension="
                        + design.getExtensionName() + " wt_path=" + runtime.getWtPath());
            }
        }
    }

    public static OptimizationTask buildExtensionTask(ExtensionDesign design) {
        Map<String, String> filePlan = design.getFilePlan() == null ? Map.of() : design.getFilePlan();
        return OptimizationTask.builder()
                .topic("runtime-extension:" + design.getExtensionName())
                .description("Implement and verify runtime extension " + design.getExtensionName())
                .files(List.of(
                        filePlan.getOrDefault("root", ""),
                        filePlan.getOrDefault("manifest", "")
                ))
                .build();
    }

    public static TaskRuntime prepareExtensionTaskRuntime(
            AutoHarnessOrchestrator orchestrator,
            ExtensionDesign design
    ) {
        return prepareExtensionTaskRuntime(orchestrator, design, true);
    }

    public static TaskRuntime prepareExtensionTaskRuntime(
            AutoHarnessOrchestrator orchestrator,
            ExtensionDesign design,
            boolean configureSharedWorkspace
    ) {
        LOGGER.info("[AutoHarnessExtensionTask] preparing runtime: extension="
                + design.getExtensionName()
                + " base_branch=" + orchestrator.getConfig().getGitBaseBranch()
                + " remote=" + orchestrator.getConfig().getGitRemote());
        String wtPath;
        try {
            wtPath = orchestrator.getWorktreeMgr().prepare("extension-" + design.getExtensionName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("worktree prepare interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("worktree prepare failed", e);
        }
        LOGGER.info("[AutoHarnessExtensionTask] worktree ready: extension="
                + design.getExtensionName() + " wt_path=" + wtPath);
        if (configureSharedWorkspace) {
            orchestrator.getGit().setWorkspace(wtPath);
            orchestrator.getCiGate().setWorkspace(wtPath);
        }

        List<DeepAgentRail> extraRails = deepRails(orchestrator);
        DeepAgent taskAgent = AutoHarnessAgentFactory.createAutoHarnessAgent(
                orchestrator.getConfig(),
                wtPath,
                null,
                false,
                List.of("implement_ext", "verify", "verify_ext", "communicate"),
                true,
                true,
                true,
                extraRails,
                null
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
        DeepAgent commitAgent = AutoHarnessAgentFactory.createCommitAgent(
                orchestrator.getConfig(),
                wtPath,
                extraRails
        );

        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(List.of());
        runtime.setWtPath(wtPath);
        runtime.setEditSafetyRail(null);
        runtime.setPreexistingDirtyFiles(List.of());
        runtime.setTaskAgent(taskAgent);
        runtime.setTaskSession(taskSession);
        runtime.setCommitAgent(commitAgent);
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
            cycleResult = CycleResult.builder()
                    .success(false)
                    .error("missing result")
                    .errorLog("No cycle result recorded for extension task")
                    .build();
        }
        if (cycleResult.isSuccess()) {
            task.setStatus(TaskStatus.SUCCESS);
        } else if (task.getStatus() == TaskStatus.RUNNING) {
            task.setStatus(TaskStatus.FAILED);
        }
        return cycleResult;
    }

    public static CycleResult resolveBuildVerifyResult(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task
    ) {
        Object result = orchestrator.getArtifacts().get(TASK_RESULT, TaskContext.taskKey(task));
        if (result instanceof CycleResult typed) {
            return typed;
        }
        Object runtimeExt = orchestrator.getArtifacts().get(RUNTIME_EXTENSION, TaskContext.taskKey(task));
        if (!(runtimeExt instanceof RuntimeExtensionArtifact)) {
            return CycleResult.builder()
                    .success(false)
                    .error("missing runtime extension")
                    .errorLog("No runtime_extension artifact recorded after build/verify")
                    .build();
        }
        return CycleResult.builder()
                .success(true)
                .summary("Extension verified: " + task.getTopic())
                .build();
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

    public static String parentStage(BaseStage stage) {
        if (IMPLEMENT_STAGE_NAME.equals(stage.name()) || VERIFY_STAGE_NAME.equals(stage.name())) {
            return "build_verify";
        }
        if (ACTIVATE_STAGE_NAME.equals(stage.name())) {
            return "activate";
        }
        String slot = stage.slot();
        return slot == null || slot.isBlank() ? stage.name() : slot;
    }

    public static OutputSchema extensionStageOutput(
            TaskContext ctx,
            BaseStage stage,
            String status
    ) {
        return extensionStageOutput(ctx, stage, status, "", null);
    }

    public static OutputSchema extensionStageOutput(
            TaskContext ctx,
            BaseStage stage,
            String status,
            String error,
            List<String> messages
    ) {
        ExtensionDesign design = requireExtensionDesign(ctx);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", parentStage(stage));
        payload.put("scope", "extension");
        payload.put("parent_stage", parentStage(stage));
        payload.put("extension_stage", stage.name());
        payload.put("extension_name", design.getExtensionName());
        payload.put("task_id", TaskContext.taskKey(ctx.getTask()));
        payload.put("status", status);
        payload.put("error", error == null ? "" : error);
        payload.put("messages", messages == null ? new ArrayList<>() : new ArrayList<>(messages));
        payload.put("metrics", Map.of());
        return new OutputSchema("stage_result", 0, payload);
    }

    @FunctionalInterface
    interface StageFactory {
        BaseStage create(String stageName);
    }

    private static BaseStage createDefaultStage(String stageName) {
        return switch (stageName) {
            case IMPLEMENT_STAGE_NAME -> instantiateStage(
                    IMPLEMENT_STAGE_CLASS,
                    new DeferredExtensionStage(IMPLEMENT_STAGE_NAME, StageSlot.IMPLEMENT.value())
            );
            case VERIFY_STAGE_NAME -> instantiateStage(
                    VERIFY_STAGE_CLASS,
                    new DeferredExtensionStage(VERIFY_STAGE_NAME, StageSlot.VERIFY.value())
            );
            case ACTIVATE_STAGE_NAME -> instantiateStage(
                    ACTIVATE_STAGE_CLASS,
                    new DeferredExtensionStage(ACTIVATE_STAGE_NAME, StageSlot.ACTIVATE.value())
            );
            default -> new DeferredExtensionStage(stageName, stageName);
        };
    }

    private static Class<? extends BaseStage> loadStageClass(String className) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class);
        } catch (ClassNotFoundException e) {
            return DeferredExtensionStage.class;
        }
    }

    private static BaseStage instantiateStage(String className, BaseStage fallback) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static ExtensionDesign requireExtensionDesign(TaskContext ctx) {
        Object design = ctx.requireArtifact(EXTENSION_TARGET);
        if (!(design instanceof ExtensionDesign typed)) {
            throw new IllegalStateException("extension_target artifact must be ExtensionDesign");
        }
        return typed;
    }

    private boolean appendStageEvents(BaseStage stage, TaskContext ctx, List<Object> events) {
        runStageStream(stage, ctx).forEachRemaining(events::add);
        Object result = ctx.getArtifact(TASK_RESULT);
        return result instanceof CycleResult cycleResult && !cycleResult.isSuccess();
    }

    private static void logPipelineStopped(String label, ExtensionDesign design, TaskContext ctx) {
        LOGGER.warning("[AutoHarnessExtensionTask] " + label
                + " stopped after failed stage: extension="
                + design.getExtensionName() + " task=" + topic(ctx));
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
        ExecutorService executor = Executors.newSingleThreadExecutor();
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

    private static String topic(TaskContext ctx) {
        return ctx.getTask() == null ? "" : nullToEmpty(ctx.getTask().getTopic());
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

    private static double monotonicSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    private record PipelineRun(List<Object> events, CycleResult result) {
    }

    /**
     * Deferred stage binding used until the same dependency component supplies
     * concrete extension stage classes.
     *
     * <p>Mirrors Python's stage import dependency in
     * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extension_task_pipeline.py}.</p>
     */
    public static class DeferredExtensionStage extends BaseStage {

        private final String name;
        private final String slot;

        public DeferredExtensionStage() {
            this("", "");
        }

        DeferredExtensionStage(String name, String slot) {
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
