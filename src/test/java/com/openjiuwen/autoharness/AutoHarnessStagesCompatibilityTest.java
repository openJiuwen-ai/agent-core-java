
package com.openjiuwen.autoharness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.contexts.TaskRuntime;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.registry.BuiltinRegistries;
import com.openjiuwen.autoharness.registry.PipelineRegistry;
import com.openjiuwen.autoharness.registry.StageRegistry;
import com.openjiuwen.autoharness.schema.AssessmentArtifact;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.CodeChangeArtifact;
import com.openjiuwen.autoharness.schema.CommitArtifact;
import com.openjiuwen.autoharness.schema.CommitFacts;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Gap;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.PullRequestArtifact;
import com.openjiuwen.autoharness.schema.SessionResultsArtifact;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.StageSpec;
import com.openjiuwen.autoharness.schema.TaskPlanArtifact;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.schema.VerifyReportArtifact;
import com.openjiuwen.autoharness.stages.AssessStage;
import com.openjiuwen.autoharness.stages.CommitStage;
import com.openjiuwen.autoharness.stages.ImplementStage;
import com.openjiuwen.autoharness.stages.LearningsStage;
import com.openjiuwen.autoharness.stages.PlanStage;
import com.openjiuwen.autoharness.stages.PublishPrStage;
import com.openjiuwen.autoharness.stages.SelectPipelineStage;
import com.openjiuwen.autoharness.stages.VerifyStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import com.openjiuwen.core.testsupport.OsTestSupport;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class AutoHarnessStagesCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void builtinRegistriesShouldExposeKnownStagesAndPipelines() {
        StageRegistry stages = BuiltinRegistries.buildStageRegistry();
        PipelineRegistry pipelines = BuiltinRegistries.buildPipelineRegistry();

        assertThat(stages.names()).contains("assess", "plan", "implement", "verify");
        assertThat(stages.names()).doesNotContain("select_pipeline");
        assertThat(pipelines.names()).contains("meta_evolve_pipeline", "extended_evolve_pipeline");
        assertThat(stages.require("plan").getStageCls()).isEqualTo(PlanStage.class);
        assertThat(stages.require("plan").getScope()).isEqualTo("session");
        assertThat(stages.require("plan").getConsumes()).containsExactly("assessment");
        assertThat(stages.require("plan").getProduces()).containsExactly("task_plan");
        assertThat(stages.require("publish_pr").getStageCls()).isEqualTo(PublishPrStage.class);
        assertThat(stages.require("commit").getStageCls()).isEqualTo(CommitStage.class);
        assertThat(stages.require("commit").getScope()).isEqualTo("task");
        assertThat(stages.require("commit").getConsumes()).containsExactly("verify_report");
        assertThat(stages.require("commit").getProduces()).containsExactly("commit_result");
        assertThat(stages.require("learnings").getStageCls()).isEqualTo(LearningsStage.class);
        assertThat(stages.require("learnings").getScope()).isEqualTo("session");
        assertThat(stages.require("learnings").getConsumes()).containsExactly("session_results");
        assertThat(stages.require("learnings").getProduces()).containsExactly("session_results");
        assertThat(stages.require("publish_pr").getScope()).isEqualTo("task");
        assertThat(stages.require("publish_pr").getConsumes()).containsExactly("verify_report", "commit_result");
        assertThat(stages.require("publish_pr").getProduces()).containsExactly("pull_request", "task_result");
        assertThat(pipelines.require("meta_evolve_pipeline").getExpectedOutputs()).containsExactly("session_results");
    }

    @Test
    void builtinRegistriesShouldLoadConfiguredRegistrars() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .stageRegistrars(List.of(AutoHarnessStagesCompatibilityTest.class.getName() + ":registerCustomStage"))
                .pipelineRegistrars(
                        List.of(AutoHarnessStagesCompatibilityTest.class.getName() + ":registerCustomPipeline"))
                .build();

        StageRegistry stages = BuiltinRegistries.buildStageRegistry(config);
        PipelineRegistry pipelines = BuiltinRegistries.buildPipelineRegistry(config, stages);

        assertThat(stages.require("custom_stage").getStageCls()).isEqualTo(CustomStage.class);
        assertThat(pipelines.require("custom_pipeline").getPipelineCls()).isEqualTo(CustomPipeline.class);
        assertThat(pipelines.require("custom_pipeline").getExpectedOutputs()).containsExactly("custom_artifact");
        assertThat(config.getStageRegistrars())
                .containsExactly(AutoHarnessStagesCompatibilityTest.class.getName() + ":registerCustomStage");
        assertThat(config.getPipelineRegistrars())
                .containsExactly(AutoHarnessStagesCompatibilityTest.class.getName() + ":registerCustomPipeline");
    }

    @Test
    void registriesShouldRejectDuplicateNames() {
        StageRegistry stages = new StageRegistry();
        stages.register(StageSpec.builder().name("plan").description("a").build());
        assertThatThrownBy(() -> stages.register(StageSpec.builder().name("plan").description("b").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stagesShouldReturnStructuredResults() throws Exception {
        Path ciConfig = tempDir.resolve("ci-pass.yaml");
        Files.writeString(ciConfig, """
                ci_gates:
                  - name: lint
                    command: \"printf ok\"
                    required: true
                """);
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(tempDir.toString()).ciGateConfig(ciConfig.toString()).build());
        TaskContext taskCtx =
            new TaskContext(orchestrator, OptimizationTask.builder().topic("Verify stage contract").build(),
                    TaskRuntime.builder().wtPath(".").build());
        StageResult assess = new AssessStage().run(null);
        StageResult plan = new PlanStage().run(null);
        StageResult implement = new ImplementStage().run(null);
        StageResult verify = new VerifyStage().run(taskCtx);

        assertThat(assess.getStatus()).isEqualTo("success");
        assertThat(assess.getArtifacts().get("assessment"))
                .isInstanceOf(com.openjiuwen.autoharness.schema.AssessmentArtifact.class);
        assertThat(plan.getArtifacts().get("task_plan")).isInstanceOf(TaskPlanArtifact.class);
        assertThat(implement.getStatus()).isEqualTo("failed");
        assertThat(implement.getArtifacts().get("code_change")).isInstanceOf(CodeChangeArtifact.class);
        assertThat(verify.getArtifacts().get("verify_report")).isInstanceOf(VerifyReportArtifact.class);
        assertThat(verify.getArtifacts()).containsEntry("verify_report.summary", "Run CI/fix loop verification.");
    }

    @Test
    void verifyStageShouldFormatCiGateMessages() {
        Map<String, Object> ciResult = Map
                .of("passed", false, "gates",
                        List.of(Map.of("name", "lint", "passed", false, "output", "E501 line too long\nline2"),
                                Map.of("name", "test", "passed", true, "output", "ok")),
                        "errors", "E501 line too long");

        assertThat(VerifyStage.iterCiGateMessages(ciResult, "")).containsExactly("CI 结果: lint=FAIL, test=PASS",
                "[lint] E501 line too long\nline2");
        assertThat(VerifyStage.formatCiStatusForEvaluator(ciResult)).contains("结论: blocking failure")
                .contains("- lint: FAIL | E501 line too long");
    }

    @Test
    void verifyStageShouldRunFixLoopAndRevertOnFailure() throws Exception {
        Path repo = initGitRepo();
        Path ciConfig = tempDir.resolve("ci-fail.yaml");
        Files.writeString(ciConfig, """
                ci_gates:
                  - name: lint
                    command: \"echo E501 line too long; exit 1\"
                    required: true
                """);
        Path target = repo.resolve("openjiuwen/harness/demo.py");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "print('changed')\n");
        var orchestrator =
            AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(repo.toString())
                    .ciGateConfig(ciConfig.toString()).experienceDir(tempDir.resolve("experience-verify").toString())
                    .fixPhase1MaxRetries(1).fixPhase2MaxRetries(1).build());
        orchestrator.getGit().setWorkspace(repo.toString());
        OptimizationTask task = OptimizationTask.builder().topic("验证失败").description("触发 CI 修复失败").build();
        TaskContext ctx = new TaskContext(orchestrator, task,
                TaskRuntime.builder().wtPath(repo.toString()).fixAgent(new FakeVerifyFixAgent()).build());

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createEvalAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeEvalAgent("verdict: reject"));
            events = new VerifyStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);
        VerifyReportArtifact report = (VerifyReportArtifact) result.getArtifacts().get("verify_report");
        CycleResult cycle = (CycleResult) result.getArtifacts().get("task_result");

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(report.isReverted()).isTrue();
        assertThat(report.getError()).contains("E501 line too long");
        assertThat(cycle.isReverted()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.REVERTED);
        assertThat(events).contains(BaseExecutionContext.message("[2/5] CI 门禁检查"));
        assertThat(events).contains(BaseExecutionContext.message("CI 结果: lint=FAIL"));
        assertThat(events).contains(BaseExecutionContext.message("[3/5] CI 未通过，启动修复循环"));
        assertThat(events).contains(BaseExecutionContext.message("[修复循环] 第 1 次重跑 CI"));
        assertThat(events).contains(BaseExecutionContext.message("[修复循环] CI 结果: lint=FAIL"));
        assertThat(events).contains(BaseExecutionContext.message("[修复循环] 第 1 次修复"));
        assertThat(events).contains(BaseExecutionContext.message("[修复循环] 修复耗尽"));
        assertThat(Files.readString(tempDir.resolve("experience-verify").resolve("experiences.jsonl")))
                .contains("fix loop failed");
    }

    @Test
    void verifyFixLoopShouldOmitWarningSummaryFromFixTarget() throws Exception {
        Assumptions.assumeTrue(OsTestSupport.isBashAvailable(), "bash not found, skipping");
        Path repo = initGitRepo();
        Path failScript = tempDir.resolve("pytest-failure.sh");
        Files.writeString(failScript, """
                #!/usr/bin/env bash
                cat <<'EOF'
                ============================= test session starts ==============================
                tests/unit_tests/core/foundation/tool/test_api_param_mapper.py F         [100%]

                =================================== FAILURES ===================================
                E   AssertionError: expected value

                =============================== warnings summary ===============================
                tests/unit_tests/core/foundation/tool/test_api_param_mapper.py:60
                  PydanticDeprecatedSince20: `location` is deprecated

                -- Docs: https://docs.pytest.org/en/stable/how-to/capture-warnings.html
                - Generated html report: file:///tmp/report/index.html -
                =========================== short test summary info ============================
                FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x
                EOF
                exit 1
                """);
        failScript.toFile().setExecutable(true);
        Path ciConfig = tempDir.resolve("ci-pytest-failure.yaml");
        // YAML double-quoted scalars treat '\' as escapes; use forward slashes for Windows paths.
        Files.writeString(ciConfig, """
                ci_gates:
                  - name: test
                    command: "%s"
                    required: true
                """.formatted(failScript.toString().replace('\\', '/')));
        Path target = repo.resolve("openjiuwen/harness/demo.py");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "print('changed')\n");
        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(repo.toString()).ciGateConfig(ciConfig.toString())
                        .experienceDir(tempDir.resolve("experience-verify-warning").toString()).fixPhase1MaxRetries(1)
                        .fixPhase2MaxRetries(0).build());
        orchestrator.getGit().setWorkspace(repo.toString());
        OptimizationTask task =
            OptimizationTask.builder().topic("fix pytest failure").description("filter pytest warnings").build();
        TaskContext ctx = new TaskContext(orchestrator, task,
                TaskRuntime.builder().wtPath(repo.toString()).fixAgent(new FakeVerifyFixAgent()).build());

        List<Object> events = new VerifyStage().stream(ctx);
        String joinedMessages = events.stream().filter(OutputSchema.class::isInstance).map(OutputSchema.class::cast)
                .filter(schema -> "message".equals(schema.getType()))
                .map(AutoHarnessStagesCompatibilityTest::messageText).reduce("", (left, right) -> left + "\n" + right);

        assertThat(joinedMessages).contains("AssertionError: expected value");
        assertThat(joinedMessages)
                .contains("FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x");
        assertThat(joinedMessages).doesNotContain("PydanticDeprecatedSince20");
        assertThat(joinedMessages).doesNotContain("Generated html report");
    }

    @Test
    void assessStageShouldBuildPythonLikeQuery() {
        String query = AssessStage.buildQuery(
                AutoHarnessConfig.builder().workspace("/tmp/repo").optimizationGoal("补齐 stages")
                        .competitor("Claude Code").build(),
                List.of(com.openjiuwen.autoharness.schema.Experience.builder()
                        .type(com.openjiuwen.autoharness.schema.ExperienceType.FAILURE).topic("lint-fix")
                        .summary("ruff failed").build()),
                "使用 staged files 运行 make check");

        assertThat(query).contains("当前日期:");
        assertThat(query).contains("工作目录: /tmp/repo");
        assertThat(query).contains("本轮目标: 补齐 stages");
        assertThat(query).contains("重点竞品: Claude Code");
        assertThat(query).contains("本轮评估需要遵守的可落地变更范围");
        assertThat(query).contains("Python 检查策略建议:");
        assertThat(query).contains("使用 staged files 运行 make check");
        assertThat(query).contains("- [failure] **lint-fix**: ruff failed");
        assertThat(query).contains("不要把 `openjiuwen/auto_harness/**`");
    }

    @Test
    void assessPythonCheckStrategyShouldMatchPythonBranches() {
        String staged =
            AssessStage.formatPythonCheckStrategy(List.of("openjiuwen/auto_harness/agent.py"), List.of(), List.of());
        assertThat(staged).contains("`make check`");
        assertThat(staged).contains("`make type-check`");
        assertThat(staged).contains("staged");

        String delta = AssessStage.formatPythonCheckStrategy(List.of(), List.of("openjiuwen/auto_harness/agent.py"),
                List.of("tests/unit_tests/auto_harness/test_agent.py"));
        assertThat(delta).contains("不要运行 `make check COMMITS=1`");
        assertThat(delta).contains("`uv run ruff check <files>`");
        assertThat(delta).contains("`uv run mypy <files>`");

        String empty = AssessStage.formatPythonCheckStrategy(List.of(), List.of(), List.of());
        assertThat(empty).contains("No Python files selected");
        assertThat(empty).contains("未执行");
    }

    @Test
    void assessFallbackShouldIncludeExperiencesAndDirections() {
        String report = AssessStage.fallbackAssess(AutoHarnessConfig.builder().workspace(tempDir.toString()).build(),
                List.of(com.openjiuwen.autoharness.schema.Experience.builder()
                        .type(com.openjiuwen.autoharness.schema.ExperienceType.FAILURE).topic("lint-fix")
                        .summary("ruff failed").build()));

        assertThat(report).contains("# 自动评估报告");
        assertThat(report).contains("lint-fix");
        assertThat(report).contains("修复近期失败: lint-fix");
    }

    @Test
    void assessStageShouldStreamAgentWriteArtifactAndRecordReport() throws Exception {
        Path runsDir = tempDir.resolve("runs");
        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(tempDir.toString()).dataDir(tempDir.toString()).build());
        FakeAssessAgent agent = new FakeAssessAgent("# 评估报告\n## 构建状态\nOK\n".repeat(5));
        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class))).thenReturn(agent);
            events = new AssessStage().stream(new com.openjiuwen.autoharness.contexts.SessionContext(orchestrator));
        }

        StageResult result = (StageResult) events.get(events.size() - 1);
        AssessmentArtifact artifact = (AssessmentArtifact) result.getArtifacts().get("assessment");
        assertThat(events).contains(BaseExecutionContext.message("[Phase A1] 评估当前状态..."));
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(OutputSchema.class));
        assertThat(agent.lastQuery).contains("Python 检查策略建议:");
        assertThat(artifact.getReport()).contains("评估报告");
        assertThat(Files.readString(runsDir.resolve("latest_assessment.md"))).contains("评估报告");
    }

    @Test
    void assessWithAgentShouldFallbackWhenReportTooShort() {
        Path experienceDir = tempDir.resolve("experience-assess");
        AutoHarnessConfig config =
            AutoHarnessConfig.builder().workspace(tempDir.toString()).experienceDir(experienceDir.toString()).build();
        var store = new com.openjiuwen.autoharness.experience.ExperienceStore(experienceDir.toString());

        String report;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeAssessAgent("too short"));
            report = AssessStage.runAssessWithFallback(config, store);
        }

        assertThat(report).contains("# 自动评估报告");
    }

    @Test
    void assessGapAnalysisShouldStreamAgentAndParseGaps() {
        String harnessState = "当前状态\n" + "x".repeat(3500);
        FakeAssessAgent agent = new FakeAssessAgent(
                """
                        | 竞品 | 功能 | 当前状态 | 差距描述 | 影响(0-1) | 可行性(0-1) | 建议方案 | 目标文件 |
                        | --- | --- | --- | --- | --- | --- | --- | --- |
                        | Claude Code | verify loop | partial | missing evaluator | 0.9 | 0.8 | port verify loop | openjiuwen/harness/verify.py, tests/test_verify.py |
                        | Codex | planning | basic | weak plan slicing | 0.5 | 0.5 | port planner | openjiuwen/harness/plan.py |
                        """);

        List<Gap> gaps;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class))).thenReturn(agent);
            gaps = AssessStage.runGapAnalysis(AutoHarnessConfig.builder().workspace(tempDir.toString()).build(),
                    "Claude Code", harnessState);
        }

        assertThat(agent.lastQuery).contains("分析 harness 与 Claude Code 的差距。");
        assertThat(agent.lastQuery).contains("当前 harness 状态:");
        assertThat(agent.lastQuery).contains("输出 markdown 表格，列：");
        assertThat(agent.lastQuery.length()).isLessThan(3300);
        assertThat(gaps).hasSize(2);
        assertThat(gaps.get(0).getCompetitor()).isEqualTo("Claude Code");
        assertThat(gaps.get(0).priority()).isEqualTo(0.7200000000000001);
        assertThat(gaps.get(0).getTargetFiles()).containsExactly("openjiuwen/harness/verify.py",
                "tests/test_verify.py");
    }

    @Test
    void assessGapAnalysisShouldReturnEmptyListWhenAgentFails() {
        List<Gap> gaps;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createAssessAgent(any(AutoHarnessConfig.class)))
                    .thenThrow(new RuntimeException("no model"));
            gaps = AssessStage.runGapAnalysis(AutoHarnessConfig.builder().workspace(tempDir.toString()).build(),
                    "Claude Code", "# state");
        }

        assertThat(gaps).isEmpty();
    }

    @Test
    void planStageShouldBuildPythonLikeQuery() {
        String query = PlanStage.buildPlanQuery(AutoHarnessConfig.builder().optimizationGoal("补齐 stages")
                .competitor("Claude Code").maxTasksPerSession(3).selfDrivenSlots(1).build(), "# 评估报告", List.of());

        assertThat(query).contains("本轮目标:\n补齐 stages");
        assertThat(query).contains("重点竞品:\nClaude Code");
        assertThat(query).contains("本轮任务规划必须遵守的范围");
        assertThat(query).contains("评估报告:\n# 评估报告");
        assertThat(query).contains("近期经验:\n无");
        assertThat(query).contains("配置任务上限: 3");
        assertThat(query).contains("规划阶段实际输出上限: 1");
        assertThat(query).contains("自驱动槽位: 1");
        assertThat(query).contains("你本轮只能输出 1 个最高优先级任务");
    }

    @Test
    void planStageShouldStreamAgentWriteArtifactAndReadExperiences() throws Exception {
        Path experienceDir = tempDir.resolve("experience-plan");
        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(tempDir.toString()).dataDir(tempDir.toString())
                        .experienceDir(experienceDir.toString()).optimizationGoal("补齐 plan").build());
        orchestrator.getExperienceStore()
                .record(com.openjiuwen.autoharness.schema.Experience.builder()
                        .type(com.openjiuwen.autoharness.schema.ExperienceType.INSIGHT).topic("prior")
                        .summary("keep one task").build());
        var ctx = new com.openjiuwen.autoharness.contexts.SessionContext(orchestrator);
        ctx.putArtifact("assessment", AssessmentArtifact.builder().report("# 评估报告\nOK").build());
        FakePlanAgent agent = new FakePlanAgent("""
                ```json
                [
                  {"topic": "task-1", "description": "d1", "files": ["openjiuwen/harness/a.py"]},
                  {"topic": "task-2", "description": "d2", "files": ["openjiuwen/core/b.py"]}
                ]
                ```
                """);

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createPlanAgent(any(AutoHarnessConfig.class))).thenReturn(agent);
            events = new PlanStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);
        TaskPlanArtifact artifact = (TaskPlanArtifact) result.getArtifacts().get("task_plan");

        assertThat(events).contains(BaseExecutionContext.message("[Phase A2] 制定优化计划..."));
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(OutputSchema.class));
        assertThat(agent.lastQuery).contains("评估报告:\n# 评估报告");
        assertThat(agent.lastQuery).contains("- [insight] prior: keep one task");
        assertThat(Files.readString(tempDir.resolve("runs").resolve("latest_plan.md"))).contains("task-2");
        assertThat(artifact.getTasks()).hasSize(1);
        assertThat(artifact.getTasks().get(0).getTopic()).isEqualTo("task-1");
        assertThat(result.getMessages()).contains("规划原始输出已保存: " + tempDir.resolve("runs").resolve("latest_plan.md"),
                "规划阶段只保留最高优先级的 1 个任务");
    }

    @Test
    void planStageResultShouldKeepOnlyTopTask() {
        StageResult result = PlanStage.stageResultFromPlanText("""
                ```json
                [
                  {"topic": "task-1", "description": "d1", "files": ["openjiuwen/harness/a.py"]},
                  {"topic": "task-2", "description": "d2", "files": ["openjiuwen/core/b.py"]}
                ]
                ```
                """);

        TaskPlanArtifact artifact = (TaskPlanArtifact) result.getArtifacts().get("task_plan");
        assertThat(artifact.getTasks()).hasSize(1);
        assertThat(artifact.getTasks().get(0).getTopic()).isEqualTo("task-1");
        assertThat(artifact.getRawPlan()).contains("task-2");
        assertThat(result.getMessages()).containsExactly("规划阶段只保留最高优先级的 1 个任务");
    }

    @Test
    void planStageResultShouldReportEmptyPlan() {
        StageResult result = PlanStage.stageResultFromPlanText("not json");

        TaskPlanArtifact artifact = (TaskPlanArtifact) result.getArtifacts().get("task_plan");
        assertThat(artifact.getTasks()).isEmpty();
        assertThat(result.getMessages()).containsExactly("规划阶段未生成任务，session 结束");
    }

    @Test
    void implementPromptShouldMatchPythonShapeAndStats() {
        String prompt = ImplementStage.buildImplementPrompt(
                OptimizationTask.builder().topic("restrict-scope").description("只允许改 harness/core 与配套文件")
                        .files(List.of("openjiuwen/harness/cli/ui/renderer.py")).build(),
                List.of(com.openjiuwen.autoharness.schema.Experience.builder()
                        .type(com.openjiuwen.autoharness.schema.ExperienceType.INSIGHT).topic("scope")
                        .summary("keep changes inside harness/core").build()));

        assertThat(prompt).contains("任务: restrict-scope");
        assertThat(prompt).contains("目标文件: openjiuwen/harness/cli/ui/renderer.py");
        assertThat(prompt).contains("- [insight] scope: keep changes inside harness/core");
        assertThat(prompt).contains("本轮实现阶段允许改动的路径");
        assertThat(prompt).contains("默认直接开始实施修改");
        assertThat(prompt).contains("是否需要我开始实现");
        assertThat(prompt).contains("严禁执行 git add、git commit");
        assertThat(ImplementStage.buildPromptDebugStats("line1\nline2")).containsEntry("chars", 11)
                .containsEntry("lines", 2).containsEntry("bytes", 11);
    }

    @Test
    void implementStageShouldStreamAgentAndRecordEditedFiles() throws Exception {
        Path repo = initGitRepo();
        Path target = repo.resolve("openjiuwen/harness/demo.py");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "print('changed')\n");
        var orchestrator = AutoHarnessFactory
                .createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(repo.toString()).build());
        orchestrator.getGit().setWorkspace(repo.toString());
        OptimizationTask task = OptimizationTask.builder().topic("实现 demo").description("修改 demo")
                .files(List.of("openjiuwen/harness/demo.py")).build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(repo.toString())
                .taskAgent(new FakeImplementAgent("## 任务完成总结\n实现已完成。")).build());

        List<Object> events = new ImplementStage().stream(ctx);
        StageResult result = (StageResult) events.get(events.size() - 1);
        CodeChangeArtifact artifact = (CodeChangeArtifact) result.getArtifacts().get("code_change");

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(events).contains(BaseExecutionContext.message("任务准备就绪: 实现 demo"));
        assertThat(events).contains(BaseExecutionContext.message("[1/5] 执行代码修改"));
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(OutputSchema.class));
        assertThat(artifact.getEditedFiles()).containsExactly("openjiuwen/harness/demo.py");
    }

    @Test
    void implementStreamShouldManageSessionLifecycle() {
        SessionAwareImplementAgent agent = new SessionAwareImplementAgent();
        RecordingImplementSession session = new RecordingImplementSession();
        OptimizationTask task = OptimizationTask.builder().topic("session-task").build();

        List<Object> chunks = ImplementStage.runImplementStream(agent, task, List.of(), session, null);

        assertThat(chunks).hasSize(1);
        assertThat(agent.session).isSameAs(session);
        assertThat(session.preRunCalls).isEqualTo(1);
        assertThat(session.preRunInputs).containsEntry("query", agent.query);
        assertThat(session.postRunCalls).isEqualTo(1);
    }

    @Test
    void implementStreamShouldUseSuppliedPrompt() {
        SessionAwareImplementAgent agent = new SessionAwareImplementAgent();
        OptimizationTask task = OptimizationTask.builder().topic("session-task").build();

        List<Object> chunks = ImplementStage.runImplementStream(agent, task, List.of(), null, "custom prompt");

        assertThat(chunks).hasSize(1);
        assertThat(agent.query).isEqualTo("custom prompt");
    }

    @Test
    void implementStageShouldFailOnControllerTaskFailedBeforeGitCheck() {
        var orchestrator =
            AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(".").build());
        OptimizationTask task = OptimizationTask.builder().topic("模型超时").build();
        TaskContext ctx = new TaskContext(orchestrator, task,
                TaskRuntime.builder().taskAgent(new FakeTaskFailedImplementAgent()).build());

        List<Object> events = new ImplementStage().stream(ctx);
        StageResult result = (StageResult) events.get(events.size() - 1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("Implement model call failed after");
        assertThat(result.getError()).contains("ReadTimeout");
        assertThat(result.getError()).contains("prompt_chars=");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void implementEditCandidateExtractionShouldIgnorePreexistingAndOutOfScope() {
        List<String> edited = ImplementStage.extractRepoEditCandidates("""
                 M openjiuwen/harness/tools/filesystem.py
                ?? openjiuwen/auto_harness/out_of_scope.py
                 M tests/unit/demo_test.py
                """, List.of("docs/zh/demo.md"), List.of("openjiuwen/harness/tools/filesystem.py"));

        assertThat(edited).containsExactly("tests/unit/demo_test.py", "docs/zh/demo.md");
    }

    @Test
    void implementEditCandidateExtractionShouldTolerateStrippedStatusPrefix() {
        List<String> edited =
            ImplementStage.extractRepoEditCandidates("M openjiuwen/harness/tools/filesystem.py", List.of(), List.of());

        assertThat(edited).containsExactly("openjiuwen/harness/tools/filesystem.py");
    }

    @Test
    void publishPrStageShouldCompleteLocalCommitResult() {
        var orchestrator =
            AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(".").build());
        OptimizationTask task = OptimizationTask.builder().topic("修复 PR draft").build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(".").build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of("gates", List.of(Map.of("name", "lint", "passed", true)))).build());
        ctx.putArtifact("commit_result",
                CommitArtifact.builder().isCommitted(true).branchName("auto-harness/topic")
                        .lastCommitStat("commit abc123")
                        .facts(CommitFacts.builder().allowedFiles(List.of("openjiuwen/harness/demo.py"))
                                .editedFiles(List.of("openjiuwen/harness/demo.py")).diffStat(" demo.py | 2 +-").build())
                        .build());

        StageResult result = new PublishPrStage().run(ctx);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        PullRequestArtifact pr = (PullRequestArtifact) result.getArtifacts().get("pull_request");
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertThat(pr.getPrUrl()).isEmpty();
        assertThat(pr.getSummary()).contains("修复 PR draft: 已完成");
        assertThat(pr.getSummary()).contains("CI=lint=PASS");
        assertThat(pr.getSummary()).contains("变更文件=openjiuwen/harness/demo.py");
        assertThat(pr.getSummary()).contains("交付=本地提交");
        assertThat(taskResult.isSuccess()).isTrue();
        assertThat(taskResult.getSummary()).isEqualTo(pr.getSummary());
        assertThat(result.getMessages()).contains("任务完成（本地提交）");
    }

    @Test
    void publishPrStageShouldFailWhenCommitMissing() {
        var orchestrator =
            AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(".").build());
        OptimizationTask task = OptimizationTask.builder().topic("缺少 commit").build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(".").build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().build());
        ctx.putArtifact("commit_result", CommitArtifact.builder().isCommitted(false).error("commit failed").build());

        StageResult result = new PublishPrStage().run(ctx);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getError()).isEqualTo("commit failed");
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertThat(taskResult.isSuccess()).isFalse();
        assertThat(taskResult.getError()).isEqualTo("commit failed");
    }

    @Test
    void publishPrStageShouldRetryDraftAndFailWhenDraftInvalid() {
        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(".").gitRemote("origin").forkOwner("tester").build());
        OptimizationTask task = OptimizationTask.builder().topic("PR draft invalid").build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(".").build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().build());
        ctx.putArtifact("commit_result", CommitArtifact.builder().isCommitted(true).branchName("main")
                .facts(CommitFacts.builder().allowedFiles(List.of("openjiuwen/harness/demo.py")).build()).build());

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createPrDraftAgent(any(AutoHarnessConfig.class), eq("."))).thenReturn(
                    new FakeDraftAgent(List.of("not json", "{\"title\":\"x\",\"body\":\"/kind unknown\"}")));
            events = new PublishPrStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("PR draft generation failed after 2 attempts");
        assertThat(result.getError()).contains("kind 必须是 bug/task/feature/refactor/clean_code 之一");
        assertThat(events).contains(BaseExecutionContext.message("[后置] 生成 PR draft"));
        assertThat(events).contains(BaseExecutionContext.message("[后置] 修正 PR draft (2/2)"));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void publishPrStageShouldGenerateDraftPushAndRecordSuccessExperience() throws Exception {
        Path repo = initGitRepo();
        Path bare = tempDir.resolve("publish-remote.git");
        run(tempDir, "git", "init", "--bare", bare.toString());
        run(repo, "git", "remote", "add", "origin", bare.toString());

        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .workspace(repo.toString()).experienceDir(tempDir.resolve("experience-publish").toString())
                .gitRemote("origin").forkOwner("tester").gitcodeToken("").build());
        orchestrator.getGit().setWorkspace(repo.toString());
        String branch = orchestrator.getGit().currentBranch();
        OptimizationTask task = OptimizationTask.builder().topic("发布 PR").description("生成 draft").expectedEffect("完成发布")
                .issueRef("#12").build();
        TaskContext ctx = new TaskContext(orchestrator, task,
                TaskRuntime.builder().wtPath(repo.toString())
                        .related(List.of(com.openjiuwen.autoharness.schema.Experience.builder()
                                .type(com.openjiuwen.autoharness.schema.ExperienceType.INSIGHT).topic("draft")
                                .summary("keep checklist").build()))
                        .build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of("gates", List.of(Map.of("name", "lint", "passed", true)))).build());
        ctx.putArtifact("commit_result",
                CommitArtifact.builder().isCommitted(true).branchName(branch).lastCommitStat("commit abc123")
                        .facts(CommitFacts.builder().allowedFiles(List.of("openjiuwen/harness/demo.py"))
                                .editedFiles(List.of("openjiuwen/harness/demo.py")).diffStat("demo.py | 1 +").build())
                        .build());

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createPrDraftAgent(any(AutoHarnessConfig.class), eq(repo.toString())))
                    .thenReturn(new FakeDraftAgent(List.of("""
                            {"title":"Publish auto harness PR","body":"/kind refactor\\n## Summary\\n- publish path"}
                            """)));
            events = new PublishPrStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);
        PullRequestArtifact pr = (PullRequestArtifact) result.getArtifacts().get("pull_request");

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getMessages()).contains("PR draft 已生成: Publish auto harness PR");
        assertThat(events).contains(BaseExecutionContext.message("[后置] 生成 PR draft"));
        assertThat(events).contains(BaseExecutionContext.message("[后置] 推送分支"));
        assertThat(events).contains(BaseExecutionContext.message("[后置] 创建 PR"));
        assertThat(pr.getSummary()).contains("发布 PR: 已完成");
        assertThat(Files.readString(tempDir.resolve("experience-publish").resolve("experiences.jsonl")))
                .contains("completed: 发布 PR");
    }

    @Test
    void publishPrStageShouldContinueCreatePrAfterPushFailureLikePython() throws Exception {
        Path repo = initGitRepo();
        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .workspace(repo.toString()).experienceDir(tempDir.resolve("experience-push-fail").toString())
                .gitRemote("missing-remote").forkOwner("tester").gitcodeToken("").build());
        orchestrator.getGit().setWorkspace(repo.toString());
        String branch = orchestrator.getGit().currentBranch();
        OptimizationTask task = OptimizationTask.builder().topic("push failed but create pr attempted").build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(repo.toString()).build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().ciResult(Map.of("passed", true)).build());
        ctx.putArtifact("commit_result",
                CommitArtifact.builder().isCommitted(true).branchName(branch).lastCommitStat("commit abc123")
                        .facts(CommitFacts.builder().allowedFiles(List.of("openjiuwen/harness/demo.py"))
                                .editedFiles(List.of("openjiuwen/harness/demo.py")).diffStat("demo.py | 1 +").build())
                        .build());

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createPrDraftAgent(any(AutoHarnessConfig.class), eq(repo.toString())))
                    .thenReturn(new FakeDraftAgent(
                            List.of("""
                                    {"title":"Publish after push failure","body":"/kind task\\n\\n## Summary\\n- continue create PR"}
                                    """)));
            events = new PublishPrStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(events).contains(BaseExecutionContext.message("[后置] 推送分支"));
        assertThat(events).contains(BaseExecutionContext.message("[后置] 创建 PR"));
        assertThat(result.getError()).isBlank();
    }

    @Test
    void publishPrStageShouldAcceptSimplifiedDraftWithoutRetry() throws Exception {
        Path repo = initGitRepo();
        Path bare = tempDir.resolve("publish-simple-remote.git");
        run(tempDir, "git", "init", "--bare", bare.toString());
        run(repo, "git", "remote", "add", "origin", bare.toString());

        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .workspace(repo.toString()).gitRemote("origin").forkOwner("tester").gitcodeToken("").build());
        orchestrator.getGit().setWorkspace(repo.toString());
        String branch = orchestrator.getGit().currentBranch();
        OptimizationTask task = OptimizationTask.builder().topic("简化 PR draft").build();
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(repo.toString()).build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().ciResult(Map.of("passed", true)).build());
        ctx.putArtifact("commit_result",
                CommitArtifact.builder().isCommitted(true).branchName(branch).lastCommitStat("commit abc123")
                        .facts(CommitFacts.builder().allowedFiles(List.of("docs/zh/demo.md"))
                                .editedFiles(List.of("docs/zh/demo.md")).diffStat("demo.md | 1 +").build())
                        .build());
        FakeDraftAgent agent = new FakeDraftAgent(List.of("""
                {"title":"docs(cli): add test line to README","body":"/kind task\\n\\n## 概述\\n简化版 body。"}
                """, "not json"));

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createPrDraftAgent(any(AutoHarnessConfig.class), eq(repo.toString())))
                    .thenReturn(agent);
            events = new PublishPrStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);

        assertThat(agent.calls).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(events).contains(BaseExecutionContext.message("[后置] 生成 PR draft"));
        assertThat(events).doesNotContain(BaseExecutionContext.message("[后置] 修正 PR draft (2/2)"));
    }

    @Test
    void publishPrStageShouldBuildPythonLikeDraftQuery() {
        var orchestrator =
            AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(".").build());
        TaskContext ctx = new TaskContext(orchestrator,
                OptimizationTask.builder().topic("draft query").description("描述").expectedEffect("效果").issueRef("#99")
                        .build(),
                TaskRuntime.builder()
                        .related(List.of(com.openjiuwen.autoharness.schema.Experience.builder()
                                .type(com.openjiuwen.autoharness.schema.ExperienceType.FAILURE).topic("old")
                                .summary("fix kind").build()))
                        .build());
        String query = PublishPrStage.buildPrDraftQuery(ctx,
                CommitFacts.builder().allowedFiles(List.of("openjiuwen/harness/demo.py"))
                        .editedFiles(List.of("openjiuwen/harness/demo.py")).diffStat("demo.py | 1 +").build(),
                Map.of("passed", true), "commit abc", "bad kind", "previous");

        assertThat(query).contains("任务主题: draft query");
        assertThat(query).contains("预期效果: 效果");
        assertThat(query).contains("关联 issue: #99");
        assertThat(query).contains("允许提交文件: openjiuwen/harness/demo.py");
        assertThat(query).contains("验证结果(JSON):");
        assertThat(query).contains("相关经验:\n- [failure] old: fix kind");
        assertThat(query).contains("上一次 PR draft 校验失败原因:\nbad kind");
        assertThat(query).contains("previous");
    }

    @Test
    void commitStageShouldDeriveAllowedFilesFromTaskAndTests() {
        CommitFacts facts = CommitFacts.builder()
                .taskDeclaredFiles(List.of("openjiuwen/harness/demo.py", "docs/en/guide.md", "docs/root.md",
                        "openjiuwen/auto_harness/internal.py"))
                .editedFiles(List.of("openjiuwen/harness/demo.py", "docs/en/guide.md", "docs/root.md",
                        "openjiuwen/auto_harness/internal.py", "tests/unit_tests/harness/test_demo.py",
                        "tests/unit_tests/harness/test_legacy.py"))
                .derivedTestFiles(List.of("tests/unit_tests/harness/test_demo.py"))
                .legacyRelatedTestFiles(List.of("tests/unit_tests/harness/test_legacy.py")).build();

        assertThat(CommitStage.deriveAllowedFiles(facts)).containsExactly("docs/en/guide.md",
                "openjiuwen/harness/demo.py", "tests/unit_tests/harness/test_demo.py",
                "tests/unit_tests/harness/test_legacy.py");
    }

    @Test
    void commitStageShouldBuildPythonLikeCommitPromptAndFailure() {
        CommitFacts facts = CommitFacts.builder().taskDeclaredFiles(List.of("openjiuwen/harness/demo.py"))
                .currentDirtyFiles(List.of("openjiuwen/harness/demo.py"))
                .editedFiles(List.of("openjiuwen/harness/demo.py")).allowedFiles(List.of("openjiuwen/harness/demo.py"))
                .derivedTestFiles(List.of("tests/unit_tests/harness/test_demo.py"))
                .legacyRelatedTestFiles(List.of("tests/unit_tests/harness/test_legacy.py"))
                .preexistingDirtyFiles(List.of("README.md")).diffStat(" demo.py | 2 +-").build();

        String prompt =
            CommitStage.buildCommitPrompt(OptimizationTask.builder().topic("修复 commit").description("提交范围").build(),
                    facts, "first failed", " M openjiuwen/harness/demo.py", "commit abc123");
        String failure = CommitStage.formatCommitFailure("Agent did not create a git commit during commit phase.",
                " M openjiuwen/harness/demo.py", "commit abc123");

        assertThat(prompt).contains("任务: 修复 commit");
        assertThat(prompt).contains("描述: 提交范围");
        assertThat(prompt).contains("允许提交文件: openjiuwen/harness/demo.py");
        assertThat(prompt).contains("禁止混入旧脏文件: README.md");
        assertThat(prompt).contains("上一次提交尝试失败原因:\nfirst failed");
        assertThat(prompt).contains("最近一次提交摘要:\ncommit abc123");
        assertThat(failure).contains("当前 git status --porcelain");
        assertThat(failure).contains("最近一次提交摘要");
    }

    @Test
    void commitStageShouldRetryAndSucceedWhenAgentCreatesCommit() throws Exception {
        Path repo = initGitRepo();
        Path file = repo.resolve("openjiuwen/harness/demo.py");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "print('changed')\n");

        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .workspace(repo.toString()).experienceDir(tempDir.resolve("experience-success").toString()).build());
        orchestrator.getGit().setWorkspace(repo.toString());
        OptimizationTask task =
            OptimizationTask.builder().topic("提交成功").files(List.of("openjiuwen/harness/demo.py")).build();
        TaskContext ctx = new TaskContext(orchestrator, task,
                TaskRuntime.builder().wtPath(repo.toString()).commitAgent(new FakeCommitAgent(repo, false))
                        .editSafetyRail(new EditedFilesRail(List.of("openjiuwen/harness/demo.py"))).build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().build());

        List<Object> events = new CommitStage().stream(ctx);
        StageResult result = (StageResult) events.get(events.size() - 1);
        CommitArtifact artifact = (CommitArtifact) result.getArtifacts().get("commit_result");

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(artifact.isCommitted()).isTrue();
        assertThat(artifact.getBranchName()).isEqualTo(orchestrator.getGit().currentBranch());
        assertThat(artifact.getBranchName()).isNotBlank();
        assertThat(artifact.getLastCommitStat()).contains("auto harness commit");
        assertThat(artifact.getFacts().getAllowedFiles()).containsExactly("openjiuwen/harness/demo.py");
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(OutputSchema.class));
    }

    @Test
    void commitStageShouldFailAfterTwoRoundsAndRecordExperience() throws Exception {
        Path repo = initGitRepo();
        Path file = repo.resolve("openjiuwen/harness/demo.py");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "print('changed')\n");

        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder()
                .workspace(repo.toString()).experienceDir(tempDir.resolve("experience-failure").toString()).build());
        orchestrator.getGit().setWorkspace(repo.toString());
        OptimizationTask task =
            OptimizationTask.builder().topic("提交失败").files(List.of("openjiuwen/harness/demo.py")).build();
        FakeCommitAgent agent = new FakeCommitAgent(repo, true);
        TaskContext ctx = new TaskContext(orchestrator, task, TaskRuntime.builder().wtPath(repo.toString())
                .commitAgent(agent).editSafetyRail(new EditedFilesRail(List.of("openjiuwen/harness/demo.py"))).build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().build());

        List<Object> events = new CommitStage().stream(ctx);
        StageResult result = (StageResult) events.get(events.size() - 1);
        CommitArtifact artifact = (CommitArtifact) result.getArtifacts().get("commit_result");

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("Agent did not create a git commit during commit phase.");
        assertThat(result.getMessages()).anySatisfy(message -> assertThat(message).contains("首次提交未成功"));
        assertThat(artifact.isCommitted()).isFalse();
        assertThat(artifact.getStatusText()).contains("openjiuwen/harness/demo.py");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(agent.calls).isEqualTo(2);
        assertThat(Files.readString(tempDir.resolve("experience-failure").resolve("experiences.jsonl")))
                .contains("commit failed").contains("openjiuwen/harness/demo.py");
    }

    @Test
    void learningsStageShouldBuildPythonLikePromptInputs() {
        List<CycleResult> results =
            List.of(CycleResult.builder().isSuccess(true).prUrl("https://gitcode.com/pr/1").build(),
                    CycleResult.builder().isSuccess(false).error("verify failed").isReverted(true).build());
        String resultsText = LearningsStage.buildResultsText(results);
        String existing = LearningsStage.buildExistingMemoriesText(List.of(com.openjiuwen.autoharness.schema.Experience
                .builder().type(com.openjiuwen.autoharness.schema.ExperienceType.FAILURE).topic("lint")
                .summary("keep logs short").build()));
        String query = LearningsStage.buildQuery(resultsText, existing);

        assertThat(resultsText).contains("- https://gitcode.com/pr/1 (success=true, isReverted=false)");
        assertThat(resultsText).contains("- verify failed (success=false, isReverted=true)");
        assertThat(existing).contains("- [failure] lint: keep logs short");
        assertThat(query).contains("本次 session 结果:");
        assertThat(query).contains("已有经验:");
    }

    @Test
    void learningsStageShouldReturnSessionResultsArtifact() {
        var orchestrator =
            AutoHarnessFactory.createAutoHarnessOrchestrator(AutoHarnessConfig.builder().workspace(".").build());
        orchestrator.recordCycleResult(CycleResult.builder().isSuccess(true).summary("ok").build());
        StageResult result =
            new LearningsStage().run(new com.openjiuwen.autoharness.contexts.SessionContext(orchestrator));

        SessionResultsArtifact artifact = (SessionResultsArtifact) result.getArtifacts().get("session_results");
        assertThat(artifact.getResults()).hasSize(1);
        assertThat(artifact.getResults().get(0).getSummary()).isEqualTo("ok");
    }

    @Test
    void learningsStageShouldStreamAgentAndRecordParsedLearnings() throws Exception {
        Path experienceDir = tempDir.resolve("experience-learnings");
        var orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(".").experienceDir(experienceDir.toString()).build());
        orchestrator.getExperienceStore()
                .record(com.openjiuwen.autoharness.schema.Experience.builder()
                        .type(com.openjiuwen.autoharness.schema.ExperienceType.FAILURE).topic("existing")
                        .summary("prior memory").build());
        orchestrator.recordCycleResult(
                CycleResult.builder().isSuccess(false).error("verify failed").isReverted(true).build());
        FakeLearningsAgent agent = new FakeLearningsAgent("""
                [
                  {"type":"insight","topic":"verify","summary":"keep logs short","details":"prefer concise failures"}
                ]
                """);
        var ctx = new com.openjiuwen.autoharness.contexts.SessionContext(orchestrator);
        ctx.putArtifact("session_results", SessionResultsArtifact.builder().results(orchestrator.getResults()).build());

        List<Object> events;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createLearningsAgent(any(AutoHarnessConfig.class),
                    eq(LearningsStage.buildResultsText(orchestrator.getResults())), any(String.class)))
                    .thenReturn(agent);
            events = new LearningsStage().stream(ctx);
        }
        StageResult result = (StageResult) events.get(events.size() - 1);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(OutputSchema.class));
        assertThat(agent.lastQuery).contains("本次 session 结果:");
        assertThat(agent.lastQuery).contains("verify failed");
        assertThat(agent.lastQuery).contains("已有经验:");
        assertThat(agent.lastQuery).contains("prior memory");
        String stored = Files.readString(experienceDir.resolve("experiences.jsonl"));
        assertThat(stored).contains("prior memory");
        assertThat(stored).contains("keep logs short");
        assertThat(stored).contains("prefer concise failures");
    }

    @Test
    void selectPipelineShouldHonorExplicitTaskPipeline() {
        PipelineSelectionArtifact result =
            SelectPipelineStage.runSelectPipeline(AutoHarnessConfig.builder().model(new Object()).build(),
                    OptimizationTask.builder().topic("t1").pipelineName("extended_evolve_pipeline").build(), "",
                    List.of("meta_evolve_pipeline", "extended_evolve_pipeline"));

        assertThat(result.getPipelineName()).isEqualTo("extended_evolve_pipeline");
        assertThat(result.getReason()).isEqualTo("task requested explicit pipeline");
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getFallbackPipeline()).isEqualTo("extended_evolve_pipeline");
    }

    @Test
    void selectPipelineShouldFallbackToMetaWhenNoModelConfigured() {
        PipelineSelectionArtifact result =
            SelectPipelineStage.runSelectPipeline(AutoHarnessConfig.builder().model(null).build(),
                    OptimizationTask.builder().topic("t1").build(), "", null);

        assertThat(result.getPipelineName()).isEqualTo("meta_evolve_pipeline");
        assertThat(result.getReason()).isEqualTo("no model configured, fallback to meta_evolve_pipeline");
        assertThat(result.getAlternatives()).containsExactly("extended_evolve_pipeline");
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.getFallbackPipeline()).isEqualTo("meta_evolve_pipeline");
    }

    @Test
    void selectPipelineShouldUseSelectorAgentWhenModelConfigured() {
        FakeSelectPipelineAgent agent = new FakeSelectPipelineAgent(
                """
                        {"pipeline_name":"extended_harness_pipeline","reason":"needs wider loop","alternatives":["pr_pipeline"],"confidence":0.75,"risk_level":"medium","required_inputs":["assessment"],"fallback_pipeline":"pr_pipeline"}
                        """);

        PipelineSelectionArtifact result;
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createSelectPipelineAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(agent);
            result = SelectPipelineStage.runSelectPipeline(AutoHarnessConfig.builder().model(new Object()).build(),
                    OptimizationTask.builder().topic("t1").description("fix flaky test")
                            .files(List.of("openjiuwen/harness/demo.py")).build(),
                    "assessment text", List.of("meta_evolve_pipeline", "extended_evolve_pipeline"));
        }

        assertThat(result.getPipelineName()).isEqualTo("extended_evolve_pipeline");
        assertThat(result.getReason()).isEqualTo("needs wider loop");
        assertThat(result.getAlternatives()).containsExactly("meta_evolve_pipeline");
        assertThat(result.getConfidence()).isEqualTo(0.75);
        assertThat(result.getRiskLevel()).isEqualTo("medium");
        assertThat(result.getRequiredInputs()).containsExactly("assessment");
        assertThat(result.getFallbackPipeline()).isEqualTo("meta_evolve_pipeline");
        assertThat(agent.lastQuery).contains("任务主题: t1");
        assertThat(agent.lastQuery).contains("评估摘要:\nassessment text");
        assertThat(agent.lastQuery).contains("- extended_evolve_pipeline");
    }

    @Test
    void selectPipelineShouldFallbackWhenSelectorOutputCannotBeParsed() {
        try (MockedStatic<AutoHarnessFactory> factory = mockStatic(AutoHarnessFactory.class)) {
            factory.when(() -> AutoHarnessFactory.createSelectPipelineAgent(any(AutoHarnessConfig.class)))
                    .thenReturn(new FakeSelectPipelineAgent("not json"));
            PipelineSelectionArtifact result =
                SelectPipelineStage.runSelectPipeline(AutoHarnessConfig.builder().model(new Object()).build(),
                        OptimizationTask.builder().topic("t1").build(), "", List.of("meta_evolve_pipeline"));

            assertThat(result.getPipelineName()).isEqualTo("meta_evolve_pipeline");
            assertThat(result.getReason()).isEqualTo("selector fallback to default pipeline");
            assertThat(result.getAlternatives()).containsExactly("extended_evolve_pipeline");
            assertThat(result.getConfidence()).isEqualTo(0.0);
            assertThat(result.getFallbackPipeline()).isEqualTo("meta_evolve_pipeline");
        }
    }

    @Test
    void selectPipelineQueryShouldMatchPythonPromptShapeAndTrimLongAssessment() {
        String longAssessment = "x".repeat(4010);
        String query = SelectPipelineStage
                .buildQuery(
                        OptimizationTask.builder().topic("t1").description("")
                                .files(List.of("openjiuwen/harness/demo.py")).build(),
                        longAssessment, List.of("meta_evolve_pipeline", "extended_evolve_pipeline"));

        assertThat(query).contains("任务主题: t1");
        assertThat(query).contains("任务描述: 无");
        assertThat(query).contains("目标文件: openjiuwen/harness/demo.py");
        assertThat(query).contains("评估摘要:\n");
        assertThat(query).contains("- meta_evolve_pipeline");
        assertThat(query).contains("- extended_evolve_pipeline");
        assertThat(query).contains("...");
        assertThat(query.length()).isLessThan(4300);
    }

    private Path initGitRepo() throws Exception {
        Path repo = tempDir.resolve("repo-" + System.nanoTime());
        Files.createDirectories(repo);
        run(repo, "git", "init");
        run(repo, "git", "config", "user.name", "Auto Harness");
        run(repo, "git", "config", "user.email", "auto@example.com");
        Files.writeString(repo.resolve("README.md"), "baseline\n");
        run(repo, "git", "add", "README.md");
        run(repo, "git", "commit", "-m", "initial");
        return repo;
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

    private static String messageText(OutputSchema schema) {
        Object payload = schema.getPayload();
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            return content == null ? "" : String.valueOf(content);
        }
        return "";
    }

    public static class EditedFilesRail {
        private final List<String> files;

        EditedFilesRail(List<String> files) {
            this.files = files;
        }

        public List<String> editedFiles() {
            return files;
        }
    }

    public static class FakeCommitAgent extends DeepAgent {
        private final Path repo;
        private final boolean skipCommit;
        private int calls;

        FakeCommitAgent(Path repo, boolean skipCommit) {
            super(AgentCard.builder().name("fake-commit").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(),
                    Workspace.builder().rootPath(repo.toString()).build());
            this.repo = repo;
            this.skipCommit = skipCommit;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            calls++;
            if (!skipCommit && calls == 2) {
                try {
                    AutoHarnessStagesCompatibilityTest.run(repo, "git", "add", "openjiuwen/harness/demo.py");
                    AutoHarnessStagesCompatibilityTest.run(repo, "git", "commit", "-m", "auto harness commit");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            return List
                    .<Object>of(
                            new OutputSchema("message", calls, Map.of("content", String.valueOf(inputs.get("query")))))
                    .iterator();
        }
    }

    public static void registerCustomStage(StageRegistry registry) {
        registry.register(StageSpec.builder().name("custom_stage").stageCls(CustomStage.class)
                .produces(List.of("custom_artifact")).build());
    }

    public static void registerCustomPipeline(PipelineRegistry registry, StageRegistry stageRegistry) {
        stageRegistry.require("custom_stage");
        registry.register(com.openjiuwen.autoharness.schema.PipelineSpec.builder().name("custom_pipeline")
                .pipelineCls(CustomPipeline.class).expectedOutputs(List.of("custom_artifact")).build());
    }

    public static class CustomStage extends com.openjiuwen.autoharness.stages.SessionStage {
        @Override
        public String name() {
            return "custom_stage";
        }

        @Override
        public String description() {
            return "custom stage";
        }

        @Override
        public List<String> produces() {
            return List.of("custom_artifact");
        }
    }

    public static class CustomPipeline extends com.openjiuwen.autoharness.pipelines.BasePipeline {
        @Override
        public String name() {
            return "custom_pipeline";
        }

        @Override
        public String description() {
            return "custom pipeline";
        }

        @Override
        public List<String> expectedOutputs() {
            return List.of("custom_artifact");
        }
    }

    public static class FakeDraftAgent extends DeepAgent {
        private final List<String> outputs;
        private int calls;

        FakeDraftAgent(List<String> outputs) {
            super(AgentCard.builder().name("fake-draft").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.outputs = outputs;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            String output = outputs.get(Math.min(calls, outputs.size() - 1));
            calls++;
            return List.<Object>of(new OutputSchema("message", calls, Map.of("content", output))).iterator();
        }
    }

    public static class FakeAssessAgent extends DeepAgent {
        private final String output;
        private String lastQuery = "";

        FakeAssessAgent(String output) {
            super(AgentCard.builder().name("fake-assess").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            lastQuery = String.valueOf(inputs.get("query"));
            return List.<Object>of(new OutputSchema("message", 0, Map.of("content", output))).iterator();
        }
    }

    public static class FakePlanAgent extends DeepAgent {
        private final String output;
        private String lastQuery = "";

        FakePlanAgent(String output) {
            super(AgentCard.builder().name("fake-plan").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            lastQuery = String.valueOf(inputs.get("query"));
            return List.<Object>of(new OutputSchema("message", 0, Map.of("content", output))).iterator();
        }
    }

    public static class FakeImplementAgent extends DeepAgent {
        private final String output;

        FakeImplementAgent(String output) {
            super(AgentCard.builder().name("fake-implement").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            return List.<Object>of(new OutputSchema("llm_output", 0, Map.of("content", output))).iterator();
        }
    }

    public static class SessionAwareImplementAgent extends DeepAgent {
        private Object session;
        private String query = "";

        SessionAwareImplementAgent() {
            super(AgentCard.builder().name("session-aware-implement").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
        }

        public Iterator<Object> stream(Map<String, Object> inputs, Object session) {
            this.session = session;
            this.query = String.valueOf(inputs.get("query"));
            return List.<Object>of(new OutputSchema("llm_output", 0, Map.of("content", "done"))).iterator();
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            this.query = String.valueOf(inputs.get("query"));
            return List.<Object>of(new OutputSchema("llm_output", 0, Map.of("content", "done"))).iterator();
        }
    }

    public static class RecordingImplementSession {
        private int preRunCalls;
        private int postRunCalls;
        private Map<String, Object> preRunInputs = Map.of();

        public void preRun(Map<String, Object> inputs) {
            preRunCalls++;
            preRunInputs = inputs;
        }

        public void postRun() {
            postRunCalls++;
        }
    }

    public static class FakeTaskFailedImplementAgent extends DeepAgent {
        FakeTaskFailedImplementAgent() {
            super(AgentCard.builder().name("fake-implement-failed").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            return List.<Object>of(new OutputSchema("controller_output", 0,
                    Map.of("type", "task_failed", "data", List.of(Map.of("type", "text", "text", "ReadTimeout")))))
                    .iterator();
        }
    }

    public static class FakeVerifyFixAgent extends DeepAgent {
        FakeVerifyFixAgent() {
            super(AgentCard.builder().name("fake-verify-fix").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            return List
                    .<Object>of(new OutputSchema("message", 0, Map.of("content", String.valueOf(inputs.get("query")))))
                    .iterator();
        }
    }

    public static class FakeEvalAgent extends DeepAgent {
        private final String output;

        FakeEvalAgent(String output) {
            super(AgentCard.builder().name("fake-eval").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            return List.<Object>of(new OutputSchema("message", 0, Map.of("content", output))).iterator();
        }
    }

    public static class FakeLearningsAgent extends DeepAgent {
        private final String output;
        private String lastQuery = "";

        FakeLearningsAgent(String output) {
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

    public static class FakeSelectPipelineAgent extends DeepAgent {
        private final String output;
        private String lastQuery = "";

        FakeSelectPipelineAgent(String output) {
            super(AgentCard.builder().name("fake-select-pipeline").description("fake").build(),
                    DeepAgentConfig.builder().enableTaskLoop(false).build(), Workspace.builder().rootPath(".").build());
            this.output = output;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            lastQuery = String.valueOf(inputs.get("query"));
            return List.<Object>of(new OutputSchema("message", 0, Map.of("content", output))).iterator();
        }
    }
}
