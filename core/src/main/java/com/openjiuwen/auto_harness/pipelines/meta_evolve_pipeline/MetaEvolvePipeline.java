/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.PipelineStageMap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskPlanArtifact;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.auto_harness.stages.SessionStage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Built-in meta evolve pipeline.
 *
 * <p>Mirrors Python's {@code MetaEvolvePipeline} in
 * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/meta_evolve_pipeline.py}.</p>
 */
public class MetaEvolvePipeline extends BasePipeline {

    private static final Logger LOGGER = Logger.getLogger(MetaEvolvePipeline.class.getName());

    private static final String TASK_PLAN = "task_plan";
    private static final String SESSION_RESULTS = "session_results";
    private static final String META_ASSESS_STAGE_NAME = "meta_assess";
    private static final String META_PLAN_STAGE_NAME = "meta_plan";
    private static final String LEARNINGS_STAGE_NAME = "learnings";
    private static final String META_ASSESS_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.MetaAssessStage";
    private static final String META_PLAN_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.MetaPlanStage";
    private static final String LEARNINGS_STAGE_CLASS =
            "com.openjiuwen.auto_harness.stages.LearningsStage";
    private static final String PR_TASK_PIPELINE_CLASS =
            "com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.PRTaskPipeline";

    private final StageFactory stageFactory;

    public MetaEvolvePipeline() {
        this(MetaEvolvePipeline::createDefaultStage);
    }

    MetaEvolvePipeline(StageFactory stageFactory) {
        this.stageFactory = stageFactory == null ? MetaEvolvePipeline::createDefaultStage : stageFactory;
    }

    @Override
    public String name() {
        return AutoHarnessPipelineNames.META_EVOLVE_PIPELINE;
    }

    @Override
    public String description() {
        return "Default meta evolve pipeline.";
    }

    @Override
    public List<String> expectedOutputs() {
        return List.of("session_results");
    }

    @Override
    public List<StageOrderEntry> stageOrder() {
        return List.of(
                new StageOrderEntry("assess", "评估当前状态"),
                new StageOrderEntry("plan", "制定优化计划"),
                new StageOrderEntry("implement", "执行代码修改"),
                new StageOrderEntry("verify", "CI 门禁检查"),
                new StageOrderEntry("commit", "提交变更"),
                new StageOrderEntry("publish", "发布 PR"),
                new StageOrderEntry("learnings", "总结经验")
        );
    }

    @Override
    public PipelineStageMap stageMap() {
        Map<String, Class<? extends BaseStage>> mapping = new LinkedHashMap<>();
        mapping.put(StageSlot.ASSESS.value(), loadStageClass(META_ASSESS_STAGE_CLASS));
        mapping.put(StageSlot.PLAN.value(), loadStageClass(META_PLAN_STAGE_CLASS));
        mapping.put(StageSlot.LEARNINGS.value(), loadStageClass(LEARNINGS_STAGE_CLASS));
        return new PipelineStageMap(mapping);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("MetaEvolvePipeline requires a SessionContext");
        }
        return stream(sessionContext);
    }

    public Iterator<Object> stream(SessionContext ctx) {
        List<Object> events = new ArrayList<>();
        PipelineSegment assessPlan = runAssessAndPlanSegment(ctx);
        events.addAll(assessPlan.events());
        if (assessPlan.stopped()) {
            return events.iterator();
        }

        runTaskPipelineStream(ctx).forEachRemaining(events::add);
        storeSessionResults(ctx);
        runLearningsStageStream(ctx).forEachRemaining(events::add);
        return events.iterator();
    }

    public void populateTaskPlanFromInputTasks(SessionContext ctx, List<OptimizationTask> tasks) {
        ctx.putArtifact(TASK_PLAN, TaskPlanArtifact.builder()
                .tasks(tasks == null ? new ArrayList<>() : new ArrayList<>(tasks))
                .rawPlan("")
                .build());
    }

    public void storeSessionResults(SessionContext ctx) {
        ctx.putArtifact(SESSION_RESULTS, SessionResultsArtifact.builder()
                .results(ctx.getOrchestrator().getResults())
                .build());
    }

    public Iterator<Object> runAssessAndPlanStream(SessionContext ctx) {
        return runAssessAndPlanSegment(ctx).events().iterator();
    }

    private PipelineSegment runAssessAndPlanSegment(SessionContext ctx) {
        List<Object> events = new ArrayList<>();
        String originalWorkspace = ctx.getOrchestrator().getConfig().getWorkspace();
        String assessWorkspace = prepareReadonlyAssessWorkspace(ctx);
        ctx.getOrchestrator().getConfig().setWorkspace(assessWorkspace);
        try {
            BaseStage assessStage = stageFactory.create(META_ASSESS_STAGE_NAME);
            List<StageResult> assessResultHolder = new ArrayList<>();
            runAssessStageStream(ctx, assessResultHolder).forEachRemaining(events::add);
            if (ctx.getOrchestrator().shouldCancel()) {
                LOGGER.info("[MetaEvolvePipeline] cancellation requested after assess stage");
                return new PipelineSegment(events, true);
            }
            if (didStageFail(assessStage, assessResultHolder)) {
                return new PipelineSegment(events, true);
            }

            BaseStage planStage = stageFactory.create(META_PLAN_STAGE_NAME);
            List<StageResult> planResultHolder = new ArrayList<>();
            runPlanStageStream(ctx, planResultHolder).forEachRemaining(events::add);
            if (ctx.getOrchestrator().shouldCancel()) {
                LOGGER.info("[MetaEvolvePipeline] cancellation requested after plan stage");
                return new PipelineSegment(events, true);
            }
            if (didStageFail(planStage, planResultHolder)) {
                return new PipelineSegment(events, true);
            }
            return new PipelineSegment(events, false);
        } finally {
            ctx.getOrchestrator().getConfig().setWorkspace(originalWorkspace);
            cleanupReadonlyAssessWorkspace(ctx, assessWorkspace);
        }
    }

    public Iterator<Object> runTaskPipelineStream(SessionContext ctx) {
        Object artifact = ctx.requireArtifact(TASK_PLAN);
        if (!(artifact instanceof TaskPlanArtifact taskPlan)) {
            throw new IllegalStateException("task_plan artifact must be TaskPlanArtifact");
        }
        List<Object> events = new ArrayList<>();
        List<OptimizationTask> tasks = taskPlan.getTasks() == null ? List.of() : taskPlan.getTasks();
        int limit = Math.min(tasks.size(), ctx.getOrchestrator().getConfig().getMaxTasksPerSession());
        for (OptimizationTask task : tasks.subList(0, limit)) {
            if (ctx.getOrchestrator().shouldCancel()) {
                LOGGER.info("[MetaEvolvePipeline] cancellation requested, stopping task pipeline");
                break;
            }
            if (ctx.getOrchestrator().getBudget().isShouldStop()) {
                break;
            }
            if (!ctx.getOrchestrator().getBudget().checkTaskBudget()) {
                break;
            }
            runPrTaskPipeline(ctx.getOrchestrator(), task).forEachRemaining(events::add);
        }
        return events.iterator();
    }

    public Iterator<Object> runLearningsStageStream(SessionContext ctx) {
        BaseStage stage = stageFactory.create(LEARNINGS_STAGE_NAME);
        List<StageResult> resultHolder = new ArrayList<>();
        Iterator<Object> events = streamStage(stage, ctx, resultHolder);
        requireStageResult(stage, resultHolder);
        return events;
    }

    public Iterator<Object> runAssessStageStream(
            SessionContext ctx,
            List<StageResult> resultHolder
    ) {
        return streamStage(stageFactory.create(META_ASSESS_STAGE_NAME), ctx, resultHolder);
    }

    public Iterator<Object> runPlanStageStream(
            SessionContext ctx,
            List<StageResult> resultHolder
    ) {
        return streamStage(stageFactory.create(META_PLAN_STAGE_NAME), ctx, resultHolder);
    }

    @FunctionalInterface
    interface StageFactory {
        BaseStage create(String stageName);
    }

    private static Iterator<Object> runPrTaskPipeline(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task
    ) {
        try {
            Class<?> cls = Class.forName(PR_TASK_PIPELINE_CLASS);
            Method method = cls.getMethod(
                    "runIsolatedStream",
                    AutoHarnessOrchestrator.class,
                    OptimizationTask.class
            );
            Object result = method.invoke(null, orchestrator, task);
            return toIterator(result);
        } catch (ClassNotFoundException e) {
            LOGGER.info("[MetaEvolvePipeline] PRTaskPipeline not translated yet; skipping task stream dispatch");
            return List.of().iterator();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to run PRTaskPipeline", e);
        }
    }

    private String prepareReadonlyAssessWorkspace(SessionContext ctx) {
        try {
            return ctx.getOrchestrator().getWorktreeMgr().prepareReadonlySnapshot("assess");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("readonly assess workspace prepare interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("readonly assess workspace prepare failed", e);
        }
    }

    private void cleanupReadonlyAssessWorkspace(SessionContext ctx, String assessWorkspace) {
        try {
            ctx.getOrchestrator().getWorktreeMgr().cleanup(assessWorkspace);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("readonly assess workspace cleanup interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("readonly assess workspace cleanup failed", e);
        }
    }

    private static BaseStage createDefaultStage(String stageName) {
        return switch (stageName) {
            case META_ASSESS_STAGE_NAME -> instantiateStage(
                    META_ASSESS_STAGE_CLASS,
                    new DeferredMetaStage(META_ASSESS_STAGE_NAME, StageSlot.ASSESS.value())
            );
            case META_PLAN_STAGE_NAME -> instantiateStage(
                    META_PLAN_STAGE_CLASS,
                    new DeferredMetaStage(META_PLAN_STAGE_NAME, StageSlot.PLAN.value())
            );
            case LEARNINGS_STAGE_NAME -> instantiateStage(
                    LEARNINGS_STAGE_CLASS,
                    new DeferredMetaStage(LEARNINGS_STAGE_NAME, StageSlot.LEARNINGS.value())
            );
            default -> new DeferredMetaStage(stageName, stageName);
        };
    }

    private static Class<? extends BaseStage> loadStageClass(String className) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class);
        } catch (ClassNotFoundException e) {
            return DeferredMetaStage.class;
        }
    }

    private static BaseStage instantiateStage(String className, BaseStage fallback) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static Iterator<Object> toIterator(Object value) {
        if (value instanceof Iterator<?> iterator) {
            return castIterator(iterator);
        }
        if (value instanceof Iterable<?> iterable) {
            return castIterator(iterable.iterator());
        }
        return List.of().iterator();
    }

    private static Iterator<Object> castIterator(Iterator<?> iterator) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return iterator.next();
            }
        };
    }

    private record PipelineSegment(List<Object> events, boolean stopped) {
    }

    /**
     * Deferred stage binding used until the same dependency component supplies
     * concrete meta evolve stage classes.
     *
     * <p>Mirrors Python's stage import dependency in
     * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/meta_evolve_pipeline.py}.</p>
     */
    public static class DeferredMetaStage extends SessionStage {

        private final String name;
        private final String slot;

        public DeferredMetaStage() {
            this("", "");
        }

        DeferredMetaStage(String name, String slot) {
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
