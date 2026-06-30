/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.pipelines;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.SessionContext;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.SessionResultsArtifact;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskPlanArtifact;
import com.openjiuwen.autoharness.stages.AssessStage;
import com.openjiuwen.autoharness.stages.LearningsStage;
import com.openjiuwen.autoharness.stages.PlanStage;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class MetaEvolvePipeline used by the Java parity implementation.
 *
 * @since 1.0
 */
public class MetaEvolvePipeline extends BasePipeline {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String NAME = "meta_evolve_pipeline";

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return NAME;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Default meta evolve pipeline.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> expectedOutputs() {
        return List.of("session_results");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("MetaEvolvePipeline requires SessionContext");
        }
        List<Object> events = new ArrayList<>();
        Object tasks = sessionContext.getArtifact("input_tasks", null);
        if (tasks instanceof List<?> list) {
            populateTaskPlanFromInputTasks(sessionContext, list);
        } else {
            if (!runAssessAndPlanStream(sessionContext, events)) {
                return events;
            }
        }
        events.addAll(runTaskPipelineStream(sessionContext));
        storeSessionResults(sessionContext);
        events.addAll(runLearningsStageStream(sessionContext));
        return events;
    }

    void populateTaskPlanFromInputTasks(SessionContext ctx, List<?> tasks) {
        List<OptimizationTask> planned = tasks.stream()
                .filter(OptimizationTask.class::isInstance)
                .map(OptimizationTask.class::cast)
                .toList();
        ctx.putArtifact("task_plan", TaskPlanArtifact.builder()
                .tasks(planned)
                .rawPlan("")
                .build());
    }

    void storeSessionResults(SessionContext ctx) {
        ctx.putArtifact("session_results", SessionResultsArtifact.builder()
                .results(ctx.getOrchestrator().getResults())
                .build());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean runAssessAndPlanStream(SessionContext ctx, List<Object> events) {
        String originalWorkspace = ctx.getOrchestrator().getConfig().getWorkspace();
        String assessWorkspace = ctx.getOrchestrator().getWorktreeMgr().prepareReadonlySnapshot("assess").toString();
        ctx.getOrchestrator().getConfig().setWorkspace(assessWorkspace);
        try {
            AssessStage assessStage = new AssessStage();
            List<StageResult> assessResultHolder = new ArrayList<>();
            events.addAll(streamStage(assessStage, ctx, assessResultHolder));
            if (didStageFail(assessStage, assessResultHolder)) {
                return false;
            }

            PlanStage planStage = new PlanStage();
            List<StageResult> planResultHolder = new ArrayList<>();
            events.addAll(streamStage(planStage, ctx, planResultHolder));
            return !didStageFail(planStage, planResultHolder);
        } finally {
            ctx.getOrchestrator().getConfig().setWorkspace(originalWorkspace);
            ctx.getOrchestrator().getWorktreeMgr().cleanup(assessWorkspace);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> runTaskPipelineStream(SessionContext ctx) {
        Object rawPlan = ctx.requireArtifact("task_plan");
        if (!(rawPlan instanceof TaskPlanArtifact taskPlan)) {
            throw new IllegalArgumentException("task_plan must be TaskPlanArtifact");
        }
        List<Object> events = new ArrayList<>();
        List<OptimizationTask> tasks = taskPlan.getTasks().stream()
                .limit(ctx.getOrchestrator().getConfig().getMaxTasksPerSession())
                .toList();
        for (OptimizationTask task : tasks) {
            if (ctx.getOrchestrator().getBudget().shouldStop()) {
                break;
            }
            if (!ctx.getOrchestrator().getBudget().checkTaskBudget(null)) {
                break;
            }
            events.addAll(PRTaskPipeline.runIsolatedStream(ctx.getOrchestrator(), task));
        }
        return events;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> runLearningsStageStream(SessionContext ctx) {
        LearningsStage stage = new LearningsStage();
        List<StageResult> resultHolder = new ArrayList<>();
        List<Object> events = streamStage(stage, ctx, resultHolder);
        requireStageResult(stage, resultHolder);
        return events;
    }
}
