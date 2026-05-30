package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.PRTaskPipeline;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.TaskPlanArtifact;
import com.openjiuwen.auto_harness.stages.AssessStage;
import com.openjiuwen.auto_harness.stages.LearningsStage;
import com.openjiuwen.auto_harness.stages.PlanStage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code MetaEvolvePipeline} in
 * {@code openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.meta_evolve_pipeline}.
 */
public class MetaEvolvePipeline extends BasePipeline {
    private static volatile PipelineHooks hooks = new DefaultPipelineHooks();

    @Override public String name() { return AutoHarnessPipelineNames.META_EVOLVE_PIPELINE; }
    @Override public String description() { return "Default meta evolve pipeline."; }
    @Override public List<String> expectedOutputs() { return List.of("session_results"); }

    public interface PipelineHooks {
        void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> resultHolder,
                Consumer<Object> eventSink);

        void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> resultHolder,
                Consumer<Object> eventSink);

        CycleResult runTask(AutoHarnessOrchestrator orchestrator, OptimizationTask task, Consumer<Object> eventSink);

        void runLearnings(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> resultHolder,
                Consumer<Object> eventSink);
    }

    public static void setHooks(PipelineHooks pipelineHooks) {
        hooks = pipelineHooks != null ? pipelineHooks : new DefaultPipelineHooks();
    }

    public static void resetHooks() {
        hooks = new DefaultPipelineHooks();
    }

    @Override
    public void execute(SessionContext ctx, Consumer<Object> eventSink) {
        Object inputTasks = ctx.getArtifact("input_tasks");
        if (inputTasks instanceof List<?> list) {
            populateTaskPlanFromInputTasks(ctx, list);
        } else {
            try {
                runAssessAndPlan(ctx, eventSink);
            } catch (StopMetaEvolvePipelineException ignored) {
                return;
            }
        }

        runTaskPipeline(ctx, eventSink);
        storeSessionResults(ctx);
        runLearningsStage(ctx, eventSink);
    }

    public void populateTaskPlanFromInputTasks(SessionContext ctx, List<?> tasks) {
        List<OptimizationTask> planTasks = new ArrayList<>();
        for (Object task : tasks) {
            if (task instanceof OptimizationTask optimizationTask) {
                planTasks.add(optimizationTask);
            }
        }
        ctx.putArtifact("task_plan", new TaskPlanArtifact(planTasks, ""));
    }

    public void storeSessionResults(SessionContext ctx) {
        SessionResultsArtifact artifact = new SessionResultsArtifact();
        artifact.setResults(ctx.getOrchestrator().getResults());
        ctx.putArtifact("session_results", artifact);
    }

    public void runAssessAndPlan(SessionContext ctx, Consumer<Object> eventSink) {
        AutoHarnessOrchestrator orchestrator = ctx.getOrchestrator();
        String originalWorkspace = orchestrator.getConfig().getWorkspace();
        String assessWorkspace = orchestrator.getWorktreeMgr().prepareReadonlySnapshot("assess");
        orchestrator.getConfig().setWorkspace(assessWorkspace);
        try {
            List<StageResult> assessResults = new ArrayList<>();
            hooks.runAssess(this, ctx, assessResults, eventSink);
            if (didStageFail(new AssessStage(), assessResults)) {
                throw new StopMetaEvolvePipelineException();
            }

            List<StageResult> planResults = new ArrayList<>();
            hooks.runPlan(this, ctx, planResults, eventSink);
            if (didStageFail(new PlanStage(), planResults)) {
                throw new StopMetaEvolvePipelineException();
            }
        } finally {
            orchestrator.getConfig().setWorkspace(originalWorkspace);
            orchestrator.getWorktreeMgr().cleanup(assessWorkspace);
        }
    }

    public void runTaskPipeline(SessionContext ctx, Consumer<Object> eventSink) {
        Object rawTaskPlan = ctx.requireArtifact("task_plan");
        if (!(rawTaskPlan instanceof TaskPlanArtifact taskPlan)) {
            throw new IllegalStateException("task_plan artifact is not a TaskPlanArtifact");
        }
        List<OptimizationTask> tasks = taskPlan.getTasks();
        int limit = Math.min(tasks.size(), ctx.getOrchestrator().getConfig().getMaxTasksPerSession());
        for (int index = 0; index < limit; index++) {
            if (ctx.getOrchestrator().getBudget().isShouldStop()) {
                break;
            }
            if (!ctx.getOrchestrator().getBudget().checkTaskBudget()) {
                break;
            }
            hooks.runTask(ctx.getOrchestrator(), tasks.get(index), eventSink);
        }
    }

    public void runLearningsStage(SessionContext ctx, Consumer<Object> eventSink) {
        List<StageResult> resultHolder = new ArrayList<>();
        hooks.runLearnings(this, ctx, resultHolder, eventSink);
        requireStageResult(new LearningsStage(), resultHolder);
    }

    private static final class DefaultPipelineHooks implements PipelineHooks {
        @Override
        public void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> resultHolder,
                Consumer<Object> eventSink) {
            pipeline.streamStage(new AssessStage(), ctx, resultHolder, eventSink);
        }

        @Override
        public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> resultHolder,
                Consumer<Object> eventSink) {
            pipeline.streamStage(new PlanStage(), ctx, resultHolder, eventSink);
        }

        @Override
        public CycleResult runTask(AutoHarnessOrchestrator orchestrator, OptimizationTask task,
                Consumer<Object> eventSink) {
            return PRTaskPipeline.runIsolated(orchestrator, task, eventSink);
        }

        @Override
        public void runLearnings(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> resultHolder,
                Consumer<Object> eventSink) {
            pipeline.streamStage(new LearningsStage(), ctx, resultHolder, eventSink);
        }
    }

    public static class StopMetaEvolvePipelineException extends RuntimeException {
        public StopMetaEvolvePipelineException() {
            super("Meta evolve pipeline stopped due to stage failure");
        }
    }
}
