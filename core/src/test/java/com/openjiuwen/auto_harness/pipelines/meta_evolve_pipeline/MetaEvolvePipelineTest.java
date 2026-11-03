/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskPlanArtifact;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.auto_harness.stages.SessionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code MetaEvolvePipeline} in
 * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/meta_evolve_pipeline.py}.
 */
class MetaEvolvePipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void metadataMatchesPythonPipelineConstants() {
        MetaEvolvePipeline pipeline = new MetaEvolvePipeline();

        assertThat(pipeline.name()).isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(pipeline.description()).isEqualTo("Default meta evolve pipeline.");
        assertThat(pipeline.expectedOutputs()).containsExactly("session_results");
        assertThat(pipeline.stageOrder())
                .extracting(MetaEvolvePipeline.StageOrderEntry::slot)
                .containsExactly("assess", "plan", "implement", "verify", "commit", "publish", "learnings");
        assertThat(pipeline.stageMap().getMapping()).containsKeys("assess", "plan", "learnings");
    }

    @Test
    void taskPlanAndSessionResultsArtifactsMirrorPythonHelpers() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        SessionContext ctx = new SessionContext(orchestrator);
        MetaEvolvePipeline pipeline = new MetaEvolvePipeline();
        OptimizationTask task = OptimizationTask.builder().topic("topic").build();
        CycleResult result = CycleResult.builder().success(true).summary("done").build();

        pipeline.populateTaskPlanFromInputTasks(ctx, List.of(task));
        orchestrator.recordCycleResult(result);
        pipeline.storeSessionResults(ctx);

        TaskPlanArtifact plan = (TaskPlanArtifact) ctx.getArtifact("task_plan");
        SessionResultsArtifact sessionResults = (SessionResultsArtifact) ctx.getArtifact("session_results");
        assertThat(plan.getTasks()).containsExactly(task);
        assertThat(plan.getRawPlan()).isEmpty();
        assertThat(sessionResults.getResults()).containsExactly(result);
    }

    @Test
    void assessPlanUsesReadonlyWorkspaceAndRestoresOriginalWorkspace() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        FakeWorktreeManager worktrees = new FakeWorktreeManager(orchestrator.getConfig(), tempDir.resolve("assess-wt"));
        orchestrator.setWorktreeMgr(worktrees);
        SessionContext ctx = new SessionContext(orchestrator);
        MetaEvolvePipeline pipeline = new MetaEvolvePipeline(
                name -> new ScriptedStage(name, slotFor(name), true, artifactFor(name))
        );

        List<Object> events = toList(pipeline.runAssessAndPlanStream(ctx));

        assertThat(events).hasSize(2);
        assertThat(worktrees.readonlyLabels).containsExactly("assess");
        assertThat(worktrees.cleaned).containsExactly(tempDir.resolve("assess-wt").toString());
        assertThat(orchestrator.getConfig().getWorkspace()).isEqualTo("original-workspace");
        assertThat(ctx.getArtifact("task_plan")).isInstanceOf(TaskPlanArtifact.class);
    }

    @Test
    void assessPlanStopsAfterAssessWhenCancellationRequested() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        orchestrator.setWorktreeMgr(new FakeWorktreeManager(orchestrator.getConfig(), tempDir.resolve("assess-wt")));
        orchestrator.cancel();
        SessionContext ctx = new SessionContext(orchestrator);
        MetaEvolvePipeline pipeline = new MetaEvolvePipeline(
                name -> new ScriptedStage(name, slotFor(name), true, artifactFor(name))
        );

        List<Object> events = toList(pipeline.runAssessAndPlanStream(ctx));

        assertThat(events).hasSize(1);
        assertThat(ctx.getArtifact("task_plan")).isNull();
    }

    @Test
    void streamStoresSessionResultsAndRunsLearningsAfterTaskPlan() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        orchestrator.setWorktreeMgr(new FakeWorktreeManager(orchestrator.getConfig(), tempDir.resolve("assess-wt")));
        SessionContext ctx = new SessionContext(orchestrator);
        MetaEvolvePipeline pipeline = new MetaEvolvePipeline(
                name -> new ScriptedStage(name, slotFor(name), true, artifactFor(name))
        );

        List<Object> events = toList(pipeline.stream(ctx));

        assertThat(events).hasSize(3);
        assertThat(ctx.getArtifact("session_results")).isInstanceOf(SessionResultsArtifact.class);
    }

    private AutoHarnessOrchestrator orchestrator() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setWorkspace("original-workspace");
        config.setMaxTasksPerSession(2);
        return new AutoHarnessOrchestrator(config);
    }

    private static String slotFor(String stageName) {
        return switch (stageName) {
            case "meta_assess" -> "assess";
            case "meta_plan" -> "plan";
            case "learnings" -> "learnings";
            default -> stageName;
        };
    }

    private static Map<String, Object> artifactFor(String stageName) {
        if (!"meta_plan".equals(stageName)) {
            return Map.of();
        }
        return Map.of("task_plan", TaskPlanArtifact.builder()
                .tasks(List.of())
                .rawPlan("")
                .build());
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> events = new ArrayList<>();
        iterator.forEachRemaining(events::add);
        return events;
    }

    private static final class ScriptedStage extends SessionStage {
        private final String name;
        private final String slot;
        private final boolean success;
        private final Map<String, Object> artifacts;

        private ScriptedStage(String name, String slot, boolean success, Map<String, Object> artifacts) {
            this.name = name;
            this.slot = slot;
            this.success = success;
            this.artifacts = artifacts;
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
                    .status(success ? "success" : "failed")
                    .artifacts(artifacts)
                    .error(success ? "" : name + " failed")
                    .build();
            return List.of((Object) result).iterator();
        }
    }

    private static final class FakeWorktreeManager extends WorktreeManager {
        private final Path readonlyPath;
        private final List<String> readonlyLabels = new ArrayList<>();
        private final List<String> cleaned = new ArrayList<>();

        private FakeWorktreeManager(AutoHarnessConfig config, Path readonlyPath) {
            super(config, (args, cwd, env) -> new GitCommandResult(0, ""));
            this.readonlyPath = readonlyPath;
        }

        @Override
        public String prepareReadonlySnapshot(String label) throws IOException {
            readonlyLabels.add(label);
            java.nio.file.Files.createDirectories(readonlyPath);
            return readonlyPath.toString();
        }

        @Override
        public void cleanup(String worktreePath) {
            cleaned.add(worktreePath);
        }
    }
}
