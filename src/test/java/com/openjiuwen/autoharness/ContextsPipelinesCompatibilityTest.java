
package com.openjiuwen.autoharness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.SessionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.contexts.TaskRuntime;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.pipelines.BasePipeline;
import com.openjiuwen.autoharness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.autoharness.pipelines.PRTaskPipeline;
import com.openjiuwen.autoharness.rails.EditSafetyRail;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.SessionResultsArtifact;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskPlanArtifact;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.stages.BaseStage;
import com.openjiuwen.autoharness.stages.PlanStage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.testsupport.OsTestSupport;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class ContextsPipelinesCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void contextsShouldResolveArtifactsAcrossSessionAndTaskScopes() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder().build());
        SessionContext sessionCtx = new SessionContext(orchestrator);
        TaskContext taskCtx = new TaskContext(orchestrator, OptimizationTask.builder().topic("task-a").build(),
                TaskRuntime.builder().wtPath("/tmp/wt").build());

        sessionCtx.putArtifact("pipeline", "meta");
        taskCtx.putArtifact("report", "ok");
        taskCtx.putArtifacts(Map.of("plan", "ready"));

        assertThat(sessionCtx.getArtifact("pipeline", null)).isEqualTo("meta");
        assertThat(taskCtx.requireArtifact("report")).isEqualTo("ok");
        assertThat(taskCtx.requireArtifact("plan")).isEqualTo("ready");
        assertThat(TaskContext.taskKey(OptimizationTask.builder().build())).isEqualTo("task");
    }

    @Test
    void orchestratorShouldInitializeEmptyTaskContextsLikePython() {
        AutoHarnessOrchestrator orchestrator =
            new AutoHarnessOrchestrator(AutoHarnessConfig.builder().dataDir(tempDir.toString()).build());

        assertThat(orchestrator.getTaskContexts()).isEmpty();
    }

    @Test
    void basePipelineHelpersShouldCaptureStageResultsArtifactsAndMessages() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder().build());
        SessionContext ctx = new SessionContext(orchestrator);
        BasePipeline pipeline = new BasePipeline() {
            @Override
            public String name() {
                return "demo";
            }

            @Override
            public String description() {
                return "demo pipeline";
            }
        };
        BaseStage stage = new BaseStage() {
            @Override
            public String name() {
                return "demo_stage";
            }

            @Override
            public String description() {
                return "demo stage";
            }

            @Override
            public List<Object> stream(com.openjiuwen.autoharness.contexts.BaseExecutionContext ignored) {
                return List.of(new OutputSchema("message", 2, Map.of("content", "chunk")),
                        StageResult.builder().artifacts(new LinkedHashMap<>(Map.of("task_plan", "ok")))
                                .messages(List.of("saved plan")).build());
            }
        };
        List<StageResult> holder = new java.util.ArrayList<>();
        List<Object> events = pipeline.streamStage(stage, ctx, holder);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo(new OutputSchema("message", 2, Map.of("content", "chunk")));
        assertThat(events.get(1)).isEqualTo(BaseExecutionContext.message("saved plan"));
        assertThat(ctx.requireArtifact("task_plan")).isEqualTo("ok");
        assertThat(pipeline.requireStageResult(stage, holder).getStatus()).isEqualTo("success");
        assertThat(pipeline.didStageFail(stage, holder)).isFalse();
    }

    @Test
    void basePipelineRequireShouldUseLastStageResultLikePython() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder().build());
        SessionContext ctx = new SessionContext(orchestrator);
        BasePipeline pipeline = new BasePipeline() {
            @Override
            public String name() {
                return "demo";
            }

            @Override
            public String description() {
                return "demo pipeline";
            }
        };
        BaseStage stage = new BaseStage() {
            @Override
            public String name() {
                return "demo_stage";
            }

            @Override
            public String description() {
                return "demo stage";
            }

            @Override
            public List<Object> stream(com.openjiuwen.autoharness.contexts.BaseExecutionContext ignored) {
                return List.of(
                        StageResult.builder().artifacts(Map.of("phase", "first")).messages(List.of("first done"))
                                .build(),
                        StageResult.builder().status("failed").artifacts(Map.of("phase", "second"))
                                .messages(List.of("second failed")).build());
            }
        };
        List<StageResult> holder = new java.util.ArrayList<>();
        List<Object> events = pipeline.streamStage(stage, ctx, holder);

        assertThat(holder).hasSize(2);
        assertThat(events).containsExactly(BaseExecutionContext.message("first done"),
                BaseExecutionContext.message("second failed"));
        assertThat(ctx.requireArtifact("phase")).isEqualTo("second");
        assertThat(pipeline.requireStageResult(stage, holder).getStatus()).isEqualTo("failed");
        assertThat(pipeline.didStageFail(stage, holder)).isTrue();
    }

    @Test
    void basePipelineRequireShouldFailOnMissingStageResult() {
        BasePipeline pipeline = new BasePipeline() {
            @Override
            public String name() {
                return "demo";
            }

            @Override
            public String description() {
                return "demo pipeline";
            }
        };

        assertThatThrownBy(() -> pipeline.requireStageResult(new PlanStage(), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prTaskPipelineShouldStopWhenImplementMakesNoRepoEdits() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder().build());
        OptimizationTask task = OptimizationTask.builder().topic("pipeline task").build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(".").build());

        List<Object> events = new PRTaskPipeline().stream(ctx);

        assertThat(ctx.requireArtifact("task_result")).isInstanceOf(CycleResult.class);
        CycleResult result = (CycleResult) ctx.requireArtifact("task_result");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("No allowed repo file was changed");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(events).anySatisfy(event -> assertThat(messageText(event)).isEqualTo("[1/5] 执行代码修改"));
    }

    @Test
    void prepareTaskRuntimeShouldCreateTaskSessionAgentsAndSharedEditRail() throws Exception {
        // Use a fixture with origin/develop; cwd may not have that remote branch on CI/Linux.
        Path repo = initOriginDevelopRepo("prepare-task-runtime-repo");
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .workspace(repo.toString()).dataDir(tempDir.resolve("auto-harness-test").toString())
                .localRepo(repo.toString()).gitBaseBranch("develop").build());
        OptimizationTask task = OptimizationTask.builder().topic("task-1").build();

        TaskRuntime runtime = PRTaskPipeline.prepareTaskRuntime(orchestrator, task);

        assertThat(runtime.getTaskAgent()).isNotNull();
        assertThat(runtime.getFixAgent()).isNotNull();
        assertThat(runtime.getCommitAgent()).isNotNull();
        assertThat(runtime.getEditSafetyRail()).isNotNull();
        assertThat(runtime.getTaskAgent()).isInstanceOf(DeepAgent.class);
        assertThat(runtime.getFixAgent()).isInstanceOf(DeepAgent.class);
        assertThat(runtime.getCommitAgent()).isInstanceOf(DeepAgent.class);
        DeepAgent taskAgent = (DeepAgent) runtime.getTaskAgent();
        DeepAgent fixAgent = (DeepAgent) runtime.getFixAgent();
        DeepAgent commitAgent = (DeepAgent) runtime.getCommitAgent();
        assertThat(taskAgent.getWorkspace().root())
                .isEqualTo(java.nio.file.Path.of(runtime.getWtPath()).toAbsolutePath().normalize());
        assertThat(fixAgent.getWorkspace().root())
                .isEqualTo(java.nio.file.Path.of(runtime.getWtPath()).toAbsolutePath().normalize());
        assertThat(commitAgent.getWorkspace().root())
                .isEqualTo(java.nio.file.Path.of(runtime.getWtPath()).toAbsolutePath().normalize());
        assertThat(taskAgent.getConfig().isEnableTaskLoop()).isTrue();
        assertThat(taskAgent.getConfig().isEnableTaskPlanning()).isTrue();
        assertThat(fixAgent.getConfig().isEnableTaskLoop()).isFalse();
        assertThat(fixAgent.getConfig().isEnableTaskPlanning()).isFalse();
        assertThat(fixAgent.getConfig().getRails()).noneMatch(TaskPlanningRail.class::isInstance);
        assertThat(commitAgent.getConfig().isEnableTaskLoop()).isFalse();
        assertThat(commitAgent.getConfig().isEnableTaskPlanning()).isFalse();
        assertThat(commitAgent.getConfig().getRails()).noneMatch(TaskPlanningRail.class::isInstance);
        assertThat(singleEditSafetyRail(taskAgent)).isSameAs(runtime.getEditSafetyRail());
        assertThat(singleEditSafetyRail(fixAgent)).isSameAs(runtime.getEditSafetyRail());
        assertThat(singleEditSafetyRail(commitAgent)).isNotSameAs(runtime.getEditSafetyRail());
        assertThat(runtime.getTaskSession()).isInstanceOf(AgentSessionApi.class);
        assertThat(((AgentSessionApi) runtime.getTaskSession()).getSessionId()).isEqualTo("auto-harness-task-1");
        assertThat(runtime.getWtPath()).isNotBlank();
        orchestrator.getWorktreeMgr().cleanup(runtime.getWtPath());
    }

    @Test
    void runIsolatedStreamShouldRecordExceptionFailure() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(
                AutoHarnessConfig.builder().experienceDir("target/auto-harness-exception-experience").build());
        OptimizationTask task = OptimizationTask.builder().topic("boom").build();

        PRTaskPipeline.runIsolatedStream(orchestrator, task, () -> {
            throw new RuntimeException("kaboom");
        });

        CycleResult result = orchestrator.getResults().get(orchestrator.getResults().size() - 1);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getError()).contains("kaboom");
        assertThat(result.getErrorLog()).contains("kaboom");
    }

    @Test
    void resolveTaskResultShouldPreferTaskArtifact() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder().build());
        OptimizationTask task = OptimizationTask.builder().topic("done").build();
        orchestrator.getArtifacts().put("task_result", CycleResult.builder().isSuccess(true).summary("done").build(),
                "done");

        CycleResult result = PRTaskPipeline.resolveTaskResult(orchestrator, task);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSummary()).isEqualTo("done");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    void metaEvolvePipelineShouldUseInputTasksAndStoreSessionResults() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder().build());
        SessionContext ctx = new SessionContext(orchestrator);
        OptimizationTask task = OptimizationTask.builder().topic("session task").build();
        ctx.putArtifact("input_tasks", List.of(task));

        List<Object> events = new MetaEvolvePipeline().stream(ctx);

        assertThat(ctx.requireArtifact("task_plan")).isInstanceOf(TaskPlanArtifact.class);
        assertThat(ctx.requireArtifact("session_results")).isInstanceOf(SessionResultsArtifact.class);
        SessionResultsArtifact results = (SessionResultsArtifact) ctx.requireArtifact("session_results");
        assertThat(results.getResults()).hasSize(1);
        assertThat(orchestrator.getTaskContexts()).isEmpty();
        assertThat(events).anySatisfy(event -> assertThat(messageText(event)).isEqualTo("[1/5] 执行代码修改"));
    }

    @Test
    void metaEvolvePipelineShouldRecordLearningsIntoExperienceStoreAfterSessionResults() throws Exception {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .experienceDir(tempDir.resolve("experience-pipeline-learnings").toString()).build());
        SessionContext ctx = new SessionContext(orchestrator);
        orchestrator.recordCycleResult(
                CycleResult.builder().isSuccess(false).error("verify failed").isReverted(true).build());
        ctx.putArtifact("input_tasks", List.of());
        FakeLearningsAgent agent = new FakeLearningsAgent(
                """
                        [
                          {"type":"insight","topic":"pipeline","summary":"persist session learning","details":"from meta pipeline"}
                        ]
                        """);

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createLearningsAgent(any(AutoHarnessConfig.class), anyString(),
                    anyString())).thenReturn(agent);
            events = new MetaEvolvePipeline().stream(ctx);
        }

        assertThat(ctx.requireArtifact("session_results")).isInstanceOf(SessionResultsArtifact.class);
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(OutputSchema.class));
        assertThat(agent.lastQuery).contains("本次 session 结果:");
        assertThat(agent.lastQuery).contains("verify failed");
        String stored = Files.readString(tempDir.resolve("experience-pipeline-learnings").resolve("experiences.jsonl"));
        assertThat(stored).contains("persist session learning");
        assertThat(stored).contains("from meta pipeline");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void orchestratorRunSessionStreamShouldOnlyStoreInputTasksWhenProvided() {
        // Keep taskTimeoutSecs finite so a stuck implement stage cannot hang IDEA (default is 1200s).
        AutoHarnessOrchestrator direct = new AutoHarnessOrchestrator(
                AutoHarnessConfig.builder().taskTimeoutSecs(30.0).sessionBudgetSecs(60.0).build());
        direct.runSessionStream(List.of(OptimizationTask.builder().topic("direct task").build()));

        assertThat(direct.getArtifacts().require("input_tasks", "")).asList().hasSize(1);

        AutoHarnessOrchestrator planned = new AutoHarnessOrchestrator(
                AutoHarnessConfig.builder().sessionBudgetSecs(0.0).taskTimeoutSecs(1.0).build());
        planned.runSessionStream(null);

        assertThat(planned.getArtifacts().get("input_tasks", "", null)).isNull();
        assertThat(planned.getArtifacts().require("pipeline_selection", "")).isNotNull();
        assertThat(planned.getArtifacts().get("task_plan", "", null)).isNotNull();
    }

    @Test
    void sessionShouldStoreMetaPipelineSelectionBeforeRunningPipeline() {
        SelectionProbePipeline.entered.set(false);
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(
                AutoHarnessConfig.builder().dataDir(tempDir.resolve("selection-before-run").toString()).build());
        orchestrator.getPipelineRegistry().require(MetaEvolvePipeline.NAME)
                .setPipelineCls(SelectionProbePipeline.class);

        List<Object> events = orchestrator.runSessionStream(null);

        assertThat(SelectionProbePipeline.entered).isTrue();
        assertThat(messageTexts(events)).contains("Session pipeline: meta_evolve_pipeline", "pipeline running");
    }

    @Test
    void metaEvolveSessionShouldPassthroughAssessAndPlanChunks() throws Exception {
        Path repo = initOriginDevelopRepo("session-stream-repo");
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().dataDir(tempDir.resolve("session-stream").toString())
                        .localRepo(repo.toString()).gitBaseBranch("develop").maxTasksPerSession(0).build());
        String planJson = "```json\n[{\"topic\":\"t1\"}]\n```";

        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAutoHarnessOrchestrator(any(AutoHarnessConfig.class)))
                    .thenCallRealMethod();
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeStreamingAgent("fake-assess", "# streamed assess"));
            factory.when(() -> AutoHarnessFactory.createPlanAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeStreamingAgent("fake-plan", planJson));

            List<Object> events = orchestrator.runSessionStream(null);

            List<String> llmChunks = events.stream().filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast).filter(schema -> "llm_output".equals(schema.getType()))
                    .map(ContextsPipelinesCompatibilityTest::messageText).toList();
            List<String> messageChunks = events.stream().filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast).filter(schema -> "message".equals(schema.getType()))
                    .map(ContextsPipelinesCompatibilityTest::messageText).toList();

            assertThat(llmChunks).contains("# streamed assess", planJson);
            assertThat(messageChunks).doesNotContain("# streamed assess", planJson);
            TaskPlanArtifact taskPlan = (TaskPlanArtifact) orchestrator.getArtifacts().require("task_plan", "");
            assertThat(taskPlan.getTasks()).hasSize(1);
            assertThat(taskPlan.getTasks().get(0).getTopic()).isEqualTo("t1");
        }
    }

    @Test
    void metaEvolveSessionShouldUseReadonlySnapshotForAssessAndPlan() throws Exception {
        Path repo = initOriginDevelopRepo("readonly-session-repo");
        String originalWorkspace = tempDir.resolve("original-workspace").toString();
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig
                .builder().dataDir(tempDir.resolve("readonly-session").toString()).workspace(originalWorkspace)
                .localRepo(repo.toString()).gitBaseBranch("develop").maxTasksPerSession(0).build());
        List<String> seenWorkspaces = new ArrayList<>();

        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAutoHarnessOrchestrator(any(AutoHarnessConfig.class)))
                    .thenCallRealMethod();
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)))
                    .thenAnswer(invocation -> {
                        seenWorkspaces.add(invocation.getArgument(0, AutoHarnessConfig.class).getWorkspace());
                        return new FakeStreamingAgent("fake-assess", "# Report");
                    });
            factory.when(() -> AutoHarnessFactory.createPlanAgent(any(AutoHarnessConfig.class)))
                    .thenAnswer(invocation -> {
                        seenWorkspaces.add(invocation.getArgument(0, AutoHarnessConfig.class).getWorkspace());
                        return new FakeStreamingAgent("fake-plan", "```json\n[{\"topic\":\"t1\"}]\n```");
                    });

            orchestrator.runSessionStream(null);
        }

        assertThat(seenWorkspaces).hasSize(2);
        assertThat(seenWorkspaces).allSatisfy(workspace -> {
            assertThat(workspace).isNotEqualTo(originalWorkspace);
            assertThat(workspace).contains("readonly-session");
            assertThat(workspace).endsWith("-assess");
            assertThat(Path.of(workspace)).doesNotExist();
        });
        assertThat(orchestrator.getConfig().getWorkspace()).isEqualTo(originalWorkspace);
    }

    @Test
    void metaEvolveSessionShouldKeepOnlyFirstPlannedTask() throws Exception {
        Path repo = initOriginDevelopRepo("plan-truncate-repo");
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig
                .builder().dataDir(tempDir.resolve("plan-truncate-session").toString()).localRepo(repo.toString())
                .gitBaseBranch("develop").sessionBudgetSecs(0.0).taskTimeoutSecs(1.0).build());
        String planJson = """
                ```json
                [{"topic":"t1"},{"topic":"t2"}]
                ```
                """;

        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAutoHarnessOrchestrator(any(AutoHarnessConfig.class)))
                    .thenCallRealMethod();
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeStreamingAgent("fake-assess", "# Report"));
            factory.when(() -> AutoHarnessFactory.createPlanAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeStreamingAgent("fake-plan", planJson));

            List<Object> events = orchestrator.runSessionStream(null);

            TaskPlanArtifact taskPlan = (TaskPlanArtifact) orchestrator.getArtifacts().require("task_plan", "");
            assertThat(taskPlan.getTasks()).hasSize(1);
            assertThat(taskPlan.getTasks().get(0).getTopic()).isEqualTo("t1");
            assertThat(taskPlan.getRawPlan()).contains("t2");
            assertThat(orchestrator.getResults()).isEmpty();
            assertThat(messageTexts(events)).contains("规划阶段只保留最高优先级的 1 个任务");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void directTasksShouldSkipAssessAndPlanAgents() {
        // maxTasksPerSession=0 keeps the session from running the heavy PR task pipeline;
        // this case only verifies that provided input_tasks skip assess/plan and populate task_plan.
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig
                .builder().dataDir(tempDir.resolve("direct-skip-assess-plan").toString()).maxTasksPerSession(0)
                .taskTimeoutSecs(5.0).sessionBudgetSecs(10.0).build());

        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAutoHarnessOrchestrator(any(AutoHarnessConfig.class)))
                    .thenCallRealMethod();
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)))
                    .thenThrow(new AssertionError("direct tasks must not run assess"));
            factory.when(() -> AutoHarnessFactory.createPlanAgent(any(AutoHarnessConfig.class)))
                    .thenThrow(new AssertionError("direct tasks must not run plan"));

            orchestrator.runSessionStream(List.of(OptimizationTask.builder().topic("t1").build()));

            factory.verify(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)),
                    org.mockito.Mockito.never());
            factory.verify(() -> AutoHarnessFactory.createPlanAgent(any(AutoHarnessConfig.class)),
                    org.mockito.Mockito.never());
            TaskPlanArtifact taskPlan = (TaskPlanArtifact) orchestrator.getArtifacts().require("task_plan", "");
            assertThat(taskPlan.getTasks()).hasSize(1);
            assertThat(taskPlan.getTasks().get(0).getTopic()).isEqualTo("t1");
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void orchestratorRunSessionStreamShouldCapDirectTasksByMaxTasksPerSession() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .maxTasksPerSession(2).taskTimeoutSecs(30.0).sessionBudgetSecs(90.0).build());

        orchestrator.runSessionStream(List.of(OptimizationTask.builder().topic("t0").build(),
                OptimizationTask.builder().topic("t1").build(), OptimizationTask.builder().topic("t2").build(),
                OptimizationTask.builder().topic("t3").build(), OptimizationTask.builder().topic("t4").build()));

        assertThat(orchestrator.getResults()).hasSize(2);
        assertThat(orchestrator.getArtifacts().require("task_result", "t0")).isInstanceOf(CycleResult.class);
        assertThat(orchestrator.getArtifacts().require("task_result", "t1")).isInstanceOf(CycleResult.class);
        assertThat(orchestrator.getArtifacts().get("task_result", "t2", null)).isNull();
    }

    @Test
    void metaEvolvePipelineShouldSkipTaskPipelineWhenSessionBudgetAlreadyStopped() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(
                AutoHarnessConfig.builder().sessionBudgetSecs(0.0).taskTimeoutSecs(1.0).build());
        orchestrator.getBudget().start();
        SessionContext ctx = new SessionContext(orchestrator);
        OptimizationTask task = OptimizationTask.builder().topic("budget stopped").build();
        ctx.putArtifact("task_plan", TaskPlanArtifact.builder().tasks(List.of(task)).build());

        List<Object> events = new MetaEvolvePipeline().runTaskPipelineStream(ctx);

        assertThat(events).isEmpty();
        assertThat(orchestrator.getResults()).isEmpty();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void metaEvolvePipelineShouldSkipTaskPipelineWhenTaskBudgetIsInsufficient() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(
                AutoHarnessConfig.builder().sessionBudgetSecs(1.0).taskTimeoutSecs(10.0).build());
        orchestrator.getBudget().start();
        SessionContext ctx = new SessionContext(orchestrator);
        OptimizationTask task = OptimizationTask.builder().topic("budget insufficient").build();
        ctx.putArtifact("task_plan", TaskPlanArtifact.builder().tasks(List.of(task)).build());

        List<Object> events = new MetaEvolvePipeline().runTaskPipelineStream(ctx);

        assertThat(events).isEmpty();
        assertThat(orchestrator.getResults()).isEmpty();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    private static String messageText(Object event) {
        if (!(event instanceof OutputSchema schema)) {
            return "";
        }
        Object payload = schema.getPayload();
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            return content == null ? "" : String.valueOf(content);
        }
        return "";
    }

    private static List<String> messageTexts(List<Object> events) {
        return events.stream().filter(OutputSchema.class::isInstance).map(OutputSchema.class::cast)
                .filter(schema -> "message".equals(schema.getType()))
                .map(ContextsPipelinesCompatibilityTest::messageText).toList();
    }

    private Path initOriginDevelopRepo(String name) throws Exception {
        Path origin = tempDir.resolve(name + ".git");
        Path local = tempDir.resolve(name);
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), local.toString());
        run(local, "git", "checkout", "-b", "develop");
        run(local, "git", "config", "user.email", "bot@example.com");
        run(local, "git", "config", "user.name", "Auto Harness");
        Files.writeString(local.resolve("README.md"), "base\n");
        run(local, "git", "add", "README.md");
        run(local, "git", "commit", "-m", "base");
        run(local, "git", "push", "-u", "origin", "develop");
        return local;
    }

    private static void run(Path cwd, String... command) throws Exception {
        if (command.length > 0 && "git".equals(command[0])) {
            OsTestSupport.assumeGitAvailable();
        }
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as(String.join(" ", command) + "\n" + output).isZero();
    }

    private static EditSafetyRail singleEditSafetyRail(DeepAgent agent) {
        List<EditSafetyRail> rails = agent.getConfig().getRails().stream().filter(EditSafetyRail.class::isInstance)
                .map(EditSafetyRail.class::cast).toList();
        assertThat(rails).hasSize(1);
        return rails.get(0);
    }

    public static final class FakeLearningsAgent extends DeepAgent {
        private final String output;
        private String lastQuery = "";

        private FakeLearningsAgent(String output) {
            super(AgentCard.builder().name("fake-learnings").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            lastQuery = String.valueOf(inputs.get("query"));
            return List.<Object>of(new OutputSchema("message", 0, Map.of("content", output))).iterator();
        }
    }

    public static final class FakeStreamingAgent extends DeepAgent {
        private final String output;

        private FakeStreamingAgent(String name, String output) {
            super(AgentCard.builder().name(name).description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            return List.<Object>of(new OutputSchema("llm_output", 0, Map.of("content", output))).iterator();
        }
    }

    public static class SelectionProbePipeline extends MetaEvolvePipeline {
        static final AtomicBoolean entered = new AtomicBoolean(false);

        @Override
        public List<Object> stream(BaseExecutionContext ctx) {
            assertThat(ctx).isInstanceOf(SessionContext.class);
            AutoHarnessOrchestrator orchestrator = ((SessionContext) ctx).getOrchestrator();
            assertThat(orchestrator.getRuntime().getSelectedPipeline()).isEqualTo(MetaEvolvePipeline.NAME);
            PipelineSelectionArtifact selection =
                (PipelineSelectionArtifact) orchestrator.getArtifacts().require("pipeline_selection", "");
            assertThat(selection.getPipelineName()).isEqualTo(MetaEvolvePipeline.NAME);
            entered.set(true);
            return List.of(SessionContext.message("pipeline running"));
        }
    }
}
