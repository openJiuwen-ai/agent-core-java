/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code PRTaskPipeline} and module helpers in
 * {@code openjiuwen/auto_harness/pipelines/meta_evolve_pipeline/meta_evolve_task_pipeline.py}.
 */
class PRTaskPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void stageMapContainsTaskStageSlots() {
        PRTaskPipeline pipeline = new PRTaskPipeline();

        assertThat(pipeline.stageMap().getMapping()).containsKeys("implement", "verify", "commit", "publish");
    }

    @Test
    void streamStopsAfterFailedImplementStage() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        OptimizationTask task = OptimizationTask.builder().topic("topic").build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        List<String> visited = new ArrayList<>();
        PRTaskPipeline pipeline = new PRTaskPipeline(name -> {
            visited.add(name);
            return new ScriptedStage(name, slotFor(name), !"meta_implement".equals(name), Map.of());
        });

        List<Object> events = toList(pipeline.stream(ctx));

        assertThat(visited).containsExactly("meta_implement");
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StageResult.class);
        assertThat(((StageResult) events.get(0)).getStatus()).isEqualTo("failed");
    }

    @Test
    void streamRunsAllStagesWhenStagesSucceed() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        OptimizationTask task = OptimizationTask.builder().topic("topic").build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        List<String> visited = new ArrayList<>();
        PRTaskPipeline pipeline = new PRTaskPipeline(name -> {
            visited.add(name);
            return new ScriptedStage(name, slotFor(name), true, Map.of());
        });

        List<Object> events = toList(pipeline.stream(ctx));

        assertThat(visited).containsExactly("meta_implement", "meta_verify", "commit", "publish_pr");
        assertThat(events).hasSize(4);
        assertThat(events).allMatch(StageResult.class::isInstance);
    }

    @Test
    void resolveTaskResultUsesTaskArtifactAndUpdatesStatus() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        OptimizationTask task = OptimizationTask.builder()
                .topic("topic")
                .status(TaskStatus.RUNNING)
                .build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        CycleResult result = CycleResult.builder().success(true).summary("done").build();
        ctx.putArtifact("task_result", result);

        CycleResult resolved = PRTaskPipeline.resolveTaskResult(orchestrator, task);

        assertThat(resolved).isSameAs(result);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    void prepareTaskRuntimeMirrorsPythonSetup() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        Path wtPath = tempDir.resolve("worktree");
        FakeWorktreeManager worktrees = new FakeWorktreeManager(orchestrator.getConfig(), wtPath);
        RecordingGitOperations git = new RecordingGitOperations(List.of("dirty.txt"));
        RecordingCIGateRunner ci = new RecordingCIGateRunner();
        Experience related = Experience.builder()
                .type(ExperienceType.INSIGHT)
                .topic("topic")
                .summary("related")
                .build();
        orchestrator.setWorktreeMgr(worktrees);
        orchestrator.setGit(git);
        orchestrator.setCiGate(ci);
        orchestrator.setExperienceStore(new FakeExperienceStore(tempDir.resolve("experience"), List.of(related)));
        OptimizationTask task = OptimizationTask.builder().topic("topic").build();

        TaskRuntime runtime = PRTaskPipeline.prepareTaskRuntime(orchestrator, task);

        assertThat(worktrees.preparedTopics).containsExactly("topic");
        assertThat(runtime.getRelated()).containsExactly(related);
        assertThat(runtime.getWtPath()).isEqualTo(wtPath.toString());
        assertThat(git.workspace).isEqualTo(wtPath.toString());
        assertThat(ci.workspace).isEqualTo(wtPath.toString());
        assertThat(runtime.getEditSafetyRail()).isInstanceOf(EditSafetyRail.class);
        assertThat(runtime.getPreexistingDirtyFiles()).containsExactly("dirty.txt");
        assertThat(runtime.getTaskAgent()).isInstanceOf(DeepAgent.class);
        assertThat(runtime.getFixAgent()).isInstanceOf(DeepAgent.class);
        assertThat(runtime.getCommitAgent()).isInstanceOf(DeepAgent.class);
        assertThat(runtime.getTaskSession()).isInstanceOf(AgentSession.class);
    }

    private AutoHarnessOrchestrator orchestrator() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setWorkspace(tempDir.toString());
        config.setTaskTimeoutSecs(30.0);
        return new AutoHarnessOrchestrator(config);
    }

    private static String slotFor(String stageName) {
        return switch (stageName) {
            case "meta_implement" -> "implement";
            case "meta_verify" -> "verify";
            case "publish_pr" -> "publish";
            default -> stageName;
        };
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> events = new ArrayList<>();
        iterator.forEachRemaining(events::add);
        return events;
    }

    private static final class ScriptedStage extends BaseStage {
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
            return List.of((Object) StageResult.builder()
                    .status(success ? "success" : "failed")
                    .artifacts(artifacts)
                    .error(success ? "" : name + " failed")
                    .build()).iterator();
        }
    }

    private static final class FakeExperienceStore extends ExperienceStore {
        private final List<Experience> related;

        private FakeExperienceStore(Path dir, List<Experience> related) {
            super(dir);
            this.related = related;
        }

        @Override
        public CompletableFuture<List<Experience>> search(String query) {
            return CompletableFuture.completedFuture(related);
        }
    }

    private static final class FakeWorktreeManager extends WorktreeManager {
        private final Path wtPath;
        private final List<String> preparedTopics = new ArrayList<>();

        private FakeWorktreeManager(AutoHarnessConfig config, Path wtPath) {
            super(config, (args, cwd, env) -> new GitCommandResult(0, ""));
            this.wtPath = wtPath;
        }

        @Override
        public String prepare(String topic) throws IOException {
            preparedTopics.add(topic);
            java.nio.file.Files.createDirectories(wtPath);
            return wtPath.toString();
        }
    }

    private static final class RecordingGitOperations extends GitOperations {
        private final List<String> dirtyFiles;
        private String workspace;

        private RecordingGitOperations(List<String> dirtyFiles) {
            super("");
            this.dirtyFiles = dirtyFiles;
        }

        @Override
        public void setWorkspace(String workspace) {
            this.workspace = workspace;
        }

        @Override
        public List<String> listDirtyFiles() {
            return dirtyFiles;
        }
    }

    private static final class RecordingCIGateRunner extends CIGateRunner {
        private String workspace;

        private RecordingCIGateRunner() {
            super("");
        }

        @Override
        public void setWorkspace(String workspace) {
            this.workspace = workspace;
        }
    }
}
