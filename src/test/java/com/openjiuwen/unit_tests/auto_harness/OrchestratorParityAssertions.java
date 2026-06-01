package com.openjiuwen.unit_tests.auto_harness;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.PRTaskPipeline;
import com.openjiuwen.auto_harness.schema.AssessmentArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PipelineSelectionArtifact;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.TaskPlanArtifact;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared assertions for Java mirrors of Python's
 * {@code tests.unit_tests.auto_harness.test_orchestrator}.
 */
public final class OrchestratorParityAssertions {

    private OrchestratorParityAssertions() {
    }

    public static void testSessionSelectsMetaPipelineBeforeRunning() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        List<String> observed = new ArrayList<>();
        MetaEvolvePipeline.setHooks(new Hooks() {
            @Override
            public void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                assertEquals(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                        orch.getRuntime().getSelectedPipeline());
                PipelineSelectionArtifact selection = assertInstanceOf(
                        PipelineSelectionArtifact.class,
                        orch.getArtifacts().require("pipeline_selection"));
                assertEquals(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE, selection.getPipelineName());
                observed.add("assess");
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "assessment", new AssessmentArtifact("# Report")), List.of()));
            }

            @Override
            public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "task_plan",
                        new TaskPlanArtifact(List.of(new OptimizationTask("t1")), "")), List.of()));
            }
        });
        try {
            List<Object> chunks = collect(orch.runSessionStream(null));
            assertTrue(messageTexts(chunks).contains(
                    "Session pipeline: " + AutoHarnessPipelineNames.META_EVOLVE_PIPELINE));
            assertEquals(List.of("assess"), observed);
        } finally {
            resetHooks();
        }
    }

    public static void testAssessAndPlanUseReadonlySnapshot() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessConfig config = config(dir);
        config.setWorkspace("/repo/local");
        AutoHarnessOrchestrator orch = new AutoHarnessOrchestrator(config, null);
        FakeWorktreeManager worktree = new FakeWorktreeManager(config, dir.resolve("worktrees/assess").toString());
        orch.setWorktreeMgr(worktree);
        List<String> seenWorkspaces = new ArrayList<>();
        String originalWorkspace = config.getWorkspace();
        MetaEvolvePipeline.setHooks(new Hooks() {
            @Override
            public void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                seenWorkspaces.add(config.getWorkspace());
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "assessment", new AssessmentArtifact("# Report")), List.of()));
            }

            @Override
            public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                seenWorkspaces.add(config.getWorkspace());
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "task_plan",
                        new TaskPlanArtifact(List.of(new OptimizationTask("t1")), "")), List.of()));
            }
        });
        try {
            collect(orch.runSessionStream(null));
            assertEquals(List.of(worktree.snapshotPath, worktree.snapshotPath), seenWorkspaces);
            assertEquals(originalWorkspace, config.getWorkspace());
            assertEquals(List.of(worktree.snapshotPath), worktree.cleaned);
        } finally {
            resetHooks();
        }
    }

    public static void testDirectTasksSkipAssessAndPlan() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        MetaEvolvePipeline.setHooks(new Hooks() {
            @Override
            public void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                throw new AssertionError("assess should be skipped for direct tasks");
            }

            @Override
            public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                throw new AssertionError("plan should be skipped for direct tasks");
            }

            @Override
            public CycleResult runTask(AutoHarnessOrchestrator orchestrator, OptimizationTask task,
                    Consumer<Object> sink) {
                CycleResult result = success(task.getTopic());
                orchestrator.recordCycleResult(result);
                sink.accept(message(task.getTopic()));
                return result;
            }
        });
        try {
            List<Object> chunks = collect(orch.runSessionStream(List.of(new OptimizationTask("t1"))));
            assertTrue(messageTexts(chunks).contains("t1"));
        } finally {
            resetHooks();
        }
    }

    public static void testSessionStreamPassthroughsAssessAndPlanChunks() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        String planText = "```json\n[{\"topic\":\"t1\"}]\n```";
        MetaEvolvePipeline.setHooks(new Hooks() {
            @Override
            public void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                sink.accept(llm("# streamed assess"));
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "assessment", new AssessmentArtifact("# streamed assess")), List.of()));
            }

            @Override
            public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                sink.accept(llm(planText));
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "task_plan",
                        new TaskPlanArtifact(List.of(new OptimizationTask("t1")), planText)), List.of()));
            }
        });
        try {
            List<Object> chunks = collect(orch.runSessionStream(null));
            assertTrue(llmTexts(chunks).contains("# streamed assess"));
            assertTrue(llmTexts(chunks).contains(planText));
            assertFalse(messageTexts(chunks).contains("# streamed assess"));
            assertFalse(messageTexts(chunks).contains(planText));
        } finally {
            resetHooks();
        }
    }

    public static void testPlanStageKeepsOnlyFirstPlannedTask() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        List<String> seenTopics = new ArrayList<>();
        MetaEvolvePipeline.setHooks(new Hooks() {
            @Override
            public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                    Consumer<Object> sink) {
                List<OptimizationTask> planned = List.of(new OptimizationTask("t1"), new OptimizationTask("t2"));
                emitStageResult(ctx, holder, sink, stageResult(Map.of(
                        "task_plan", new TaskPlanArtifact(planned.subList(0, 1), "raw")), List.of(
                        "规划阶段只保留最高优先级的 1 个任务")));
            }

            @Override
            public CycleResult runTask(AutoHarnessOrchestrator orchestrator, OptimizationTask task,
                    Consumer<Object> sink) {
                seenTopics.add(task.getTopic());
                CycleResult result = success(task.getTopic());
                orchestrator.recordCycleResult(result);
                return result;
            }
        });
        try {
            List<Object> chunks = collect(orch.runSessionStream(null));
            assertEquals(List.of("t1"), seenTopics);
            assertTrue(messageTexts(chunks).contains("规划阶段只保留最高优先级的 1 个任务"));
        } finally {
            resetHooks();
        }
    }

    public static void testCapsTasks() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        orch.getConfig().setMaxTasksPerSession(2);
        MetaEvolvePipeline.setHooks(new Hooks() {
            @Override
            public CycleResult runTask(AutoHarnessOrchestrator orchestrator, OptimizationTask task,
                    Consumer<Object> sink) {
                CycleResult result = success(task.getTopic());
                orchestrator.recordCycleResult(result);
                return result;
            }
        });
        try {
            List<OptimizationTask> tasks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                tasks.add(new OptimizationTask("t" + i));
            }
            collect(orch.runSessionStream(tasks));
            assertEquals(2, orch.getResults().size());
        } finally {
            resetHooks();
        }
    }

    public static void testPrepareTaskRuntimeCreatesTaskSessionAndFixAgent() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        FakeRuntimeDependencies deps = new FakeRuntimeDependencies(dir.resolve("worktrees/task-1").toString());
        PRTaskPipeline.setRuntimeDependencies(deps);
        try {
            TaskRuntime runtime = PRTaskPipeline.prepareTaskRuntime(orch, new OptimizationTask("task-1"));

            assertSame(deps.taskAgent, runtime.getTaskAgent());
            assertSame(deps.fixAgent, runtime.getFixAgent());
            assertSame(deps.commitAgent, runtime.getCommitAgent());
            assertSame(deps.taskSession, runtime.getTaskSession());
            assertEquals(List.of("openjiuwen/harness/tools/filesystem.py"), runtime.getPreexistingDirtyFiles());
            assertEquals(deps.worktreePath, runtime.getWtPath());
            assertSame(runtime.getEditSafetyRail(), deps.firstRail);
            assertSame(runtime.getEditSafetyRail(), deps.secondRail);
            assertEquals(List.of(true, false), deps.enableTaskLoopCalls);
            assertEquals(List.of(true, false), deps.enableTaskPlanningCalls);
            assertEquals(List.of(true, false), deps.enableProgressRepeatCalls);
            assertEquals("auto-harness-task-1", deps.sessionId);
            assertEquals(List.of(deps.worktreePath), deps.gitWorkspaces);
            assertEquals(List.of(deps.worktreePath), deps.ciWorkspaces);
        } finally {
            PRTaskPipeline.resetRuntimeDependencies();
        }
    }

    public static void testTimeoutHandling() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessConfig config = config(dir);
        config.setTaskTimeoutSecs(0.01);
        AutoHarnessOrchestrator orch = new AutoHarnessOrchestrator(config, null);
        PRTaskPipeline.setTaskStreamRunner((orchestrator, task, sink) -> Thread.sleep(10_000));
        try {
            OptimizationTask task = new OptimizationTask("slow");
            PRTaskPipeline.runIsolated(orch, task, ignored -> {
            });
            assertEquals("timeout", orch.getResults().get(0).getError());
            assertEquals(TaskStatus.TIMEOUT, task.getStatus());
        } finally {
            PRTaskPipeline.resetTaskStreamRunner();
        }
    }

    public static void testExceptionHandling() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        PRTaskPipeline.setTaskStreamRunner((orchestrator, task, sink) -> {
            throw new RuntimeException("kaboom");
        });
        try {
            OptimizationTask task = new OptimizationTask("boom");
            PRTaskPipeline.runIsolated(orch, task, ignored -> {
            });
            assertTrue(orch.getResults().get(0).getError().contains("kaboom"));
            assertEquals(TaskStatus.FAILED, task.getStatus());
        } finally {
            PRTaskPipeline.resetTaskStreamRunner();
        }
    }

    public static void testResolveTaskResultFromArtifact() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);
        OptimizationTask task = new OptimizationTask("done");
        CycleResult expected = success("done");
        orch.getArtifacts().put("task_result", expected, TaskContext.taskKey(task));

        CycleResult result = PRTaskPipeline.resolveTaskResult(orch, task);

        assertSame(expected, result);
        assertTrue(result.isSuccess());
        assertEquals(TaskStatus.SUCCESS, task.getStatus());
    }

    public static void testOrchestratorInitializesTaskContexts() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator");
        AutoHarnessOrchestrator orch = orchestrator(dir);

        assertTrue(orch.getTaskContexts().isEmpty());
    }

    public static void testWriteDebugArtifactPersistsContentAndRaisesOnFailure() throws Exception {
        Path dir = Files.createTempDirectory("ah-orchestrator-debug");

        String written = AutoHarnessOrchestrator.writeDebugArtifact(
                dir.toString(), "nested/output.txt", "debug payload");

        assertEquals("debug payload", Files.readString(Path.of(written)));

        Path fileInsteadOfDirectory = Files.createTempFile("ah-orchestrator-debug", ".txt");
        assertThrows(UncheckedIOException.class, () -> AutoHarnessOrchestrator.writeDebugArtifact(
                fileInsteadOfDirectory.toString(), "child.txt", "should fail"));
    }

    private static AutoHarnessOrchestrator orchestrator(Path dir) {
        AutoHarnessConfig config = config(dir);
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, null);
        orchestrator.setWorktreeMgr(new FakeWorktreeManager(config, dir.resolve("worktrees/assess").toString()));
        return orchestrator;
    }

    private static AutoHarnessConfig config(Path dir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(dir.toString());
        config.setSessionBudgetSecs(3600.0);
        config.setTaskTimeoutSecs(600.0);
        config.setWorkspace("/repo/local");
        return config;
    }

    private static void resetHooks() {
        MetaEvolvePipeline.resetHooks();
        PRTaskPipeline.resetTaskStreamRunner();
        PRTaskPipeline.resetRuntimeDependencies();
    }

    private static void emitStageResult(SessionContext ctx, List<StageResult> holder, Consumer<Object> sink,
            StageResult result) {
        holder.add(result);
        if (result.getArtifacts() != null && !result.getArtifacts().isEmpty()) {
            ctx.putArtifacts(result.getArtifacts());
        }
        for (String text : result.getMessages()) {
            sink.accept(message(text));
        }
    }

    private static StageResult stageResult(Map<String, Object> artifacts, List<String> messages) {
        StageResult result = new StageResult();
        result.setArtifacts(artifacts);
        result.setMessages(messages);
        return result;
    }

    private static CycleResult success(String summary) {
        CycleResult result = new CycleResult();
        result.setSuccess(true);
        result.setSummary(summary);
        return result;
    }

    private static OutputSchema message(String text) {
        return new OutputSchema("message", 0, Map.of("content", text));
    }

    private static OutputSchema llm(String text) {
        return new OutputSchema("llm_output", 0, Map.of("content", text));
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    private static List<String> messageTexts(List<Object> chunks) {
        return outputTexts(chunks, "message");
    }

    private static List<String> llmTexts(List<Object> chunks) {
        return outputTexts(chunks, "llm_output");
    }

    private static List<String> outputTexts(List<Object> chunks, String type) {
        List<String> texts = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema && type.equals(schema.getType())) {
                texts.add(String.valueOf(((Map<?, ?>) schema.getPayload()).get("content")));
            }
        }
        return texts;
    }

    private static class Hooks implements MetaEvolvePipeline.PipelineHooks {
        @Override
        public void runAssess(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                Consumer<Object> sink) {
            emitStageResult(ctx, holder, sink, stageResult(Map.of(
                    "assessment", new AssessmentArtifact("# Report")), List.of()));
        }

        @Override
        public void runPlan(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                Consumer<Object> sink) {
            emitStageResult(ctx, holder, sink, stageResult(Map.of(
                    "task_plan", new TaskPlanArtifact(List.of(new OptimizationTask("t1")), "")), List.of()));
        }

        @Override
        public CycleResult runTask(AutoHarnessOrchestrator orchestrator, OptimizationTask task,
                Consumer<Object> sink) {
            CycleResult result = success(task.getTopic());
            orchestrator.recordCycleResult(result);
            return result;
        }

        @Override
        public void runLearnings(MetaEvolvePipeline pipeline, SessionContext ctx, List<StageResult> holder,
                Consumer<Object> sink) {
            emitStageResult(ctx, holder, sink, new StageResult());
        }
    }

    private static class FakeWorktreeManager extends WorktreeManager {
        final String snapshotPath;
        final List<String> cleaned = new ArrayList<>();

        FakeWorktreeManager(AutoHarnessConfig config, String snapshotPath) {
            super(config);
            this.snapshotPath = snapshotPath;
        }

        @Override
        public String prepareReadonlySnapshot(String label) {
            return snapshotPath;
        }

        @Override
        public void cleanup(String worktreePath) {
            cleaned.add(worktreePath);
        }
    }

    private static class FakeRuntimeDependencies implements PRTaskPipeline.RuntimeDependencies {
        final String worktreePath;
        final Object taskAgent = new AgentWithCard();
        final Object fixAgent = new Object();
        final Object commitAgent = new Object();
        final Object taskSession = new Object();
        final List<Boolean> enableTaskLoopCalls = new ArrayList<>();
        final List<Boolean> enableTaskPlanningCalls = new ArrayList<>();
        final List<Boolean> enableProgressRepeatCalls = new ArrayList<>();
        final List<String> gitWorkspaces = new ArrayList<>();
        final List<String> ciWorkspaces = new ArrayList<>();
        Object firstRail;
        Object secondRail;
        String sessionId = "";

        FakeRuntimeDependencies(String worktreePath) {
            this.worktreePath = worktreePath;
        }

        @Override
        public List<Experience> searchRelated(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
            return List.of();
        }

        @Override
        public String prepareWorktree(AutoHarnessOrchestrator orchestrator, OptimizationTask task) {
            return worktreePath;
        }

        @Override
        public void setWorkspace(AutoHarnessOrchestrator orchestrator, String worktreePath) {
            gitWorkspaces.add(worktreePath);
            ciWorkspaces.add(worktreePath);
        }

        @Override
        public List<String> listDirtyFiles(AutoHarnessOrchestrator orchestrator) {
            return List.of("openjiuwen/harness/tools/filesystem.py");
        }

        @Override
        public Object createTaskAgent(AutoHarnessConfig config, String workspaceOverride, Object editSafetyRail,
                boolean enableTaskLoop, boolean enableTaskPlanning, boolean enableProgressRepeat) {
            if (enableTaskLoopCalls.isEmpty()) {
                firstRail = editSafetyRail;
            } else {
                secondRail = editSafetyRail;
            }
            enableTaskLoopCalls.add(enableTaskLoop);
            enableTaskPlanningCalls.add(enableTaskPlanning);
            enableProgressRepeatCalls.add(enableProgressRepeat);
            return enableTaskLoop ? taskAgent : fixAgent;
        }

        @Override
        public Object createCommitAgent(AutoHarnessConfig config, String workspaceOverride) {
            return commitAgent;
        }

        @Override
        public Object createTaskSession(String sessionId, Object taskAgent) {
            this.sessionId = sessionId;
            return taskSession;
        }

        @Override
        public void cleanup(AutoHarnessOrchestrator orchestrator, String worktreePath) {
        }
    }

    public static class AgentWithCard {
        public final Object card = new Object();
    }
}
