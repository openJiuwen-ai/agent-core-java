package com.openjiuwen.autoharness;

import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.autoharness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.CodeChangeArtifact;
import com.openjiuwen.autoharness.schema.CommitFacts;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.Gap;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.ResearchContext;
import com.openjiuwen.autoharness.schema.SessionResultsArtifact;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskPlanArtifact;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.schema.VerifyReportArtifact;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.autoharness.stages.CommitStage;
import com.openjiuwen.autoharness.stages.PublishPrStage;
import com.openjiuwen.autoharness.stages.LearningsStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoHarnessCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void orchestratorShouldExposeDefaultRegistries() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace(".").build()
        );

        assertThat(orchestrator.getStageRegistry().names()).contains("assess", "plan", "implement", "verify");
        assertThat(orchestrator.getStageRegistry().require("commit").getStageCls()).isEqualTo(CommitStage.class);
        assertThat(orchestrator.getStageRegistry().require("publish_pr").getStageCls()).isEqualTo(PublishPrStage.class);
        assertThat(orchestrator.getStageRegistry().require("learnings").getStageCls()).isEqualTo(LearningsStage.class);
        assertThat(orchestrator.getPipelineRegistry().names()).contains("meta_evolve_pipeline", "extended_evolve_pipeline");
        assertThat(orchestrator.getPipelineRegistry().require("meta_evolve_pipeline").getPipelineCls())
                .isEqualTo(MetaEvolvePipeline.class);
        assertThat(orchestrator.getPipelineRegistry().require("extended_evolve_pipeline").getPipelineCls())
                .isEqualTo(ExtendedEvolvePipeline.class);
    }

    @Test
    void orchestratorShouldSelectExplicitPipelineWhenTaskRequestsOne() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().pipelineName("meta_evolve_pipeline").build()
        );

        PipelineSelectionArtifact selected = orchestrator.selectPipeline(List.of(
                OptimizationTask.builder().topic("Refine prompt rails").pipelineName("extended_evolve_pipeline").build()
        ));

        assertThat(selected.getPipelineName()).isEqualTo("extended_evolve_pipeline");
        assertThat(selected.getReason()).isEqualTo("tasks requested explicit pipeline");
        assertThat(selected.getAlternatives()).contains("meta_evolve_pipeline");
        assertThat(selected.getConfidence()).isEqualTo(1.0);
        assertThat(selected.getFallbackPipeline()).isEqualTo("extended_evolve_pipeline");
    }

    @Test
    void orchestratorShouldNormalizeLegacyPipelineAlias() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().pipelineName("meta_evolve_pipeline").build()
        );

        PipelineSelectionArtifact selected = orchestrator.selectPipeline(List.of(
                OptimizationTask.builder().topic("Refine prompt rails").pipelineName("pr_pipeline").build()
        ));

        assertThat(selected.getPipelineName()).isEqualTo("meta_evolve_pipeline");
        assertThat(selected.getFallbackPipeline()).isEqualTo("meta_evolve_pipeline");
    }

    @Test
    void orchestratorShouldRejectConflictingExplicitPipelines() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().pipelineName("meta_evolve_pipeline").build()
        );

        assertThatThrownBy(() -> orchestrator.selectPipeline(List.of(
                OptimizationTask.builder().topic("t1").pipelineName("meta_evolve_pipeline").build(),
                OptimizationTask.builder().topic("t2").pipelineName("extended_evolve_pipeline").build()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting task pipeline_name values");
    }

    @Test
    void orchestratorShouldFallbackWhenExplicitPipelineIsUnsupported() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().pipelineName("extended_evolve_pipeline").build()
        );

        PipelineSelectionArtifact selected = orchestrator.selectPipeline(List.of(
                OptimizationTask.builder().topic("t1").pipelineName("missing_pipeline").build()
        ));

        assertThat(selected.getPipelineName()).isEqualTo("meta_evolve_pipeline");
        assertThat(selected.getReason()).isEqualTo("requested session pipeline unsupported, fallback to meta_evolve_pipeline");
        assertThat(selected.getConfidence()).isZero();
        assertThat(selected.getFallbackPipeline()).isEqualTo("meta_evolve_pipeline");
    }

    @Test
    void orchestratorShouldRunTasksIntoCycleResults() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().workspace("./repo").build()
        );

        List<CycleResult> results = orchestrator.runSession(List.of(
                OptimizationTask.builder().topic("Improve task planning rail").build(),
                OptimizationTask.builder().topic("Tighten verify stage").build()
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).getError()).contains("No allowed repo file was changed");
        assertThat(orchestrator.getArtifacts().require("task_plan", "")).isInstanceOf(TaskPlanArtifact.class);
        assertThat(orchestrator.getArtifacts().require("session_results", "")).isInstanceOf(SessionResultsArtifact.class);
        assertThat(orchestrator.getArtifacts().require("task_result", "Improve task planning rail")).isEqualTo(results.get(0));
        assertThat(orchestrator.getLastCycleResult()).isEqualTo(results.get(1));
        assertThat(orchestrator.getRuntime().getSelectedPipeline()).isEqualTo("meta_evolve_pipeline");
        assertThat(orchestrator.getTaskContexts()).isEmpty();
    }

    @Test
    void orchestratorShouldStorePipelineSelectionArtifact() {
        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(
                AutoHarnessConfig.builder().pipelineName("meta_evolve_pipeline").build()
        );

        orchestrator.runSession(List.of(
                OptimizationTask.builder().topic("Refine prompt rails").build()
        ));

        Object artifact = orchestrator.getArtifacts().require("pipeline_selection", "");
        assertThat(artifact).isInstanceOf(PipelineSelectionArtifact.class);
        PipelineSelectionArtifact selection = (PipelineSelectionArtifact) artifact;
        assertThat(selection.getPipelineName()).isEqualTo("meta_evolve_pipeline");
        assertThat(selection.getReason()).isEqualTo("default session pipeline");
    }

    @Test
    void configShouldResolveWorkspacePath() {
        AutoHarnessConfig config = AutoHarnessConfig.builder().workspace("./repo").build();
        assertThat(config.workspacePath().toString()).contains("repo");
    }

    @Test
    void researchContextShouldMirrorPythonSchemaDefaults() {
        ResearchContext context = new ResearchContext();

        assertThat(context.getExperiences()).isEmpty();
        assertThat(context.getSourceFiles()).isEmpty();
        assertThat(context.getGapReport()).isNull();
    }

    @Test
    void enumJsonValuesShouldMirrorPythonStrEnumValues() {
        assertThat(TaskStatus.PENDING.value()).isEqualTo("pending");
        assertThat(TaskStatus.RUNNING.value()).isEqualTo("running");
        assertThat(TaskStatus.SUCCESS.value()).isEqualTo("success");
        assertThat(TaskStatus.FAILED.value()).isEqualTo("failed");
        assertThat(TaskStatus.TIMEOUT.value()).isEqualTo("timeout");
        assertThat(TaskStatus.REVERTED.value()).isEqualTo("reverted");
        assertThat(ExperienceType.OPTIMIZATION.value()).isEqualTo("optimization");
        assertThat(ExperienceType.FAILURE.value()).isEqualTo("failure");
        assertThat(ExperienceType.INSIGHT.value()).isEqualTo("insight");

        assertThat(JsonUtils.safeJsonDumps(TaskStatus.PENDING)).isEqualTo("\"pending\"");
        assertThat(JsonUtils.safeJsonLoads("\"success\"", TaskStatus.class)).isEqualTo(TaskStatus.SUCCESS);
        assertThat(JsonUtils.safeJsonDumps(ExperienceType.FAILURE)).isEqualTo("\"failure\"");
        assertThat(JsonUtils.safeJsonLoads("\"insight\"", ExperienceType.class)).isEqualTo(ExperienceType.INSIGHT);
    }

    @Test
    void schemaDefaultsShouldMirrorPythonCoreModels() {
        Gap emptyGap = new Gap();
        Gap weightedGap = Gap.builder()
                .impact(0.8)
                .feasibility(0.5)
                .build();
        OptimizationTask task = OptimizationTask.builder()
                .topic("fix timeout")
                .build();
        CycleResult cycle = new CycleResult();

        assertThat(emptyGap.getId()).isEmpty();
        assertThat(emptyGap.getImpact()).isEqualTo(0.0);
        assertThat(emptyGap.getTargetFiles()).isEmpty();
        assertThat(weightedGap.priority()).isEqualTo(0.4);
        assertThat(task.getTopic()).isEqualTo("fix timeout");
        assertThat(task.getIssueRef()).isNull();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getFiles()).isEmpty();
        assertThat(cycle.isSuccess()).isFalse();
        assertThat(cycle.getSummary()).isEmpty();
        assertThat(cycle.getPrUrl()).isEmpty();
        assertThat(cycle.isReverted()).isFalse();
    }

    @Test
    void configDefaultsShouldMirrorPythonSchema() {
        AutoHarnessConfig config = new AutoHarnessConfig();

        assertThat(config.getDataDir()).isEmpty();
        assertThat(config.getLocalRepo()).isEmpty();
        assertThat(config.getWorkspace()).isEmpty();
        assertThat(config.getSessionBudgetSecs()).isEqualTo(3600.0);
        assertThat(config.getModelTimeoutSecs()).isEqualTo(300.0);
        assertThat(config.getMaxTasksPerSession()).isEqualTo(3);
        assertThat(config.getGitRemote()).isEmpty();
        assertThat(config.getForkOwner()).isEmpty();
        assertThat(config.getGitUserName()).isEmpty();
        assertThat(config.getGitcodeUsername()).isEmpty();
        assertThat(config.getGitcodeTokenEnv()).isEqualTo("GITCODE_ACCESS_TOKEN");
        assertThat(config.getCiGatePythonExecutable()).isEmpty();
        assertThat(config.getCiGateInstallCommand()).isEmpty();
        assertThat(config.getImmutableFiles()).isEmpty();
        assertThat(config.resolveImmutableFiles()).isNotEmpty();
    }

    @Test
    void mutableDefaultsShouldBeIndependentLikePythonDefaultFactories() {
        AutoHarnessConfig firstConfig = new AutoHarnessConfig();
        AutoHarnessConfig secondConfig = new AutoHarnessConfig();
        Gap firstGap = new Gap();
        Gap secondGap = new Gap();
        OptimizationTask firstTask = OptimizationTask.builder()
                .topic("first")
                .build();
        OptimizationTask secondTask = OptimizationTask.builder()
                .topic("second")
                .build();
        ResearchContext firstContext = new ResearchContext();
        ResearchContext secondContext = new ResearchContext();
        TaskPlanArtifact firstPlan = new TaskPlanArtifact();
        TaskPlanArtifact secondPlan = new TaskPlanArtifact();
        PipelineSelectionArtifact firstSelection = new PipelineSelectionArtifact();
        PipelineSelectionArtifact secondSelection = new PipelineSelectionArtifact();
        SessionResultsArtifact firstSessionResults = new SessionResultsArtifact();
        SessionResultsArtifact secondSessionResults = new SessionResultsArtifact();
        CodeChangeArtifact firstCodeChange = new CodeChangeArtifact();
        CodeChangeArtifact secondCodeChange = new CodeChangeArtifact();
        VerifyReportArtifact firstVerifyReport = new VerifyReportArtifact();
        VerifyReportArtifact secondVerifyReport = new VerifyReportArtifact();
        CommitFacts firstCommitFacts = new CommitFacts();
        CommitFacts secondCommitFacts = new CommitFacts();
        StageResult firstStageResult = new StageResult();
        StageResult secondStageResult = new StageResult();

        firstConfig.getImmutableFiles().add("extra.py");
        firstConfig.getSkillsDirs().add("skills");
        firstConfig.getAgentIterations().put("custom", 4);
        firstGap.getTargetFiles().add("gap.py");
        firstTask.getFiles().add("task.py");
        firstContext.getSourceFiles().put("source.py", "content");
        firstContext.getExperiences().add(new com.openjiuwen.autoharness.schema.Experience());
        firstPlan.getTasks().add(firstTask);
        firstSelection.getAlternatives().add("extended_evolve_pipeline");
        firstSelection.getRequiredInputs().add("gap_report");
        firstSessionResults.getResults().add(new CycleResult());
        firstCodeChange.getEditedFiles().add("openjiuwen/auto_harness/schema.py");
        firstCodeChange.getRelated().add(new com.openjiuwen.autoharness.schema.Experience());
        firstVerifyReport.getCiResult().put("ok", true);
        firstCommitFacts.getEditedFiles().add("openjiuwen/auto_harness/stages/verify.py");
        firstCommitFacts.getAllowedFiles().add("openjiuwen/auto_harness/stages/verify.py");
        firstStageResult.getArtifacts().put("assessment", "report");
        firstStageResult.getMessages().add("done");
        firstStageResult.getMetrics().put("duration", 1);

        assertThat(secondConfig.getImmutableFiles()).doesNotContain("extra.py");
        assertThat(secondConfig.getSkillsDirs()).doesNotContain("skills");
        assertThat(secondConfig.getAgentIterations()).doesNotContainKey("custom");
        assertThat(secondGap.getTargetFiles()).isEmpty();
        assertThat(secondTask.getFiles()).isEmpty();
        assertThat(secondContext.getSourceFiles()).isEmpty();
        assertThat(secondContext.getExperiences()).isEmpty();
        assertThat(secondPlan.getTasks()).isEmpty();
        assertThat(secondSelection.getAlternatives()).isEmpty();
        assertThat(secondSelection.getRequiredInputs()).isEmpty();
        assertThat(secondSessionResults.getResults()).isEmpty();
        assertThat(secondCodeChange.getEditedFiles()).isEmpty();
        assertThat(secondCodeChange.getRelated()).isEmpty();
        assertThat(secondVerifyReport.getCiResult()).isEmpty();
        assertThat(secondCommitFacts.getEditedFiles()).isEmpty();
        assertThat(secondCommitFacts.getAllowedFiles()).isEmpty();
        assertThat(secondStageResult.getArtifacts()).isEmpty();
        assertThat(secondStageResult.getMessages()).isEmpty();
        assertThat(secondStageResult.getMetrics()).isEmpty();
    }

    @Test
    void configShouldDeriveDefaultAutoHarnessPaths() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir("./state")
                .upstreamRepo("agent-core")
                .build();
        AutoHarnessConfig repoUrlConfig = AutoHarnessConfig.builder()
                .dataDir("./state")
                .upstreamRepo("")
                .repoUrl("https://example.com/team/demo.git")
                .build();
        AutoHarnessConfig explicitExperienceConfig = AutoHarnessConfig.builder()
                .dataDir("./state")
                .experienceDir("./custom-experience")
                .build();

        assertThat(config.experiencePath().toString()).contains("state");
        assertThat(config.experiencePath().toString()).contains("experience");
        assertThat(config.worktreesPath().toString()).contains("state");
        assertThat(config.worktreesPath().toString()).contains("worktrees");
        assertThat(config.runsPath().toString()).contains("state");
        assertThat(config.runsPath().toString()).contains("runs");
        assertThat(config.cacheRepoPath().toString()).contains("state");
        assertThat(config.cacheRepoPath().toString()).contains("agent-core");
        assertThat(config.buildPaths().getExperienceDir()).contains("state");
        assertThat(config.buildPaths().getRunsDir()).contains("runs");
        assertThat(config.resolveRepoName()).isEqualTo("agent-core");
        assertThat(repoUrlConfig.resolveRepoName()).isEqualTo("demo");
        assertThat(repoUrlConfig.cacheRepoPath().toString()).contains("demo");
        assertThat(AutoHarnessConfig.builder()
                .dataDir("./state")
                .upstreamRepo("custom-repo.git")
                .build()
                .resolveRepoName()).isEqualTo("custom-repo");
        assertThat(explicitExperienceConfig.experiencePath().toString()).contains("custom-experience");
    }

    @Test
    void configShouldBuildProjectProfileAndRuntimeMetadata() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .workspace("./repo")
                .dataDir("./state")
                .gitBaseBranch("main")
                .isConfigBootstrapped(true)
                .suggestedLocalRepo("/tmp/local-repo")
                .immutableFiles(List.of("a.txt"))
                .highImpactPrefixes(List.of("src/main/"))
                .build();

        AutoHarnessOrchestrator orchestrator = AutoHarnessFactory.createAutoHarnessOrchestrator(config);

        assertThat(orchestrator.getPaths().getWorktreesDir()).contains("worktrees");
        assertThat(orchestrator.getProjectProfile().getRepoUrl()).isEqualTo(config.getRepoUrl());
        assertThat(orchestrator.getProjectProfile().getDefaultBaseBranch()).isEqualTo("main");
        assertThat(orchestrator.getProjectProfile().getImmutableFiles()).containsExactly("a.txt");
        assertThat(orchestrator.getProjectProfile().getHighImpactPrefixes()).containsExactly("src/main/");
        assertThat(orchestrator.getRuntime().getCurrentWorkspace()).isEqualTo("./repo");
        assertThat(orchestrator.getRuntime().isConfigBootstrapped()).isTrue();
        assertThat(orchestrator.getRuntime().getSuggestedLocalRepo()).isEqualTo("/tmp/local-repo");
    }

    @Test
    void configShouldLoadNestedYamlSectionsFromDict() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of(
                "local_repo", "./repo",
                "language", "en",
                "immutable_files", List.of("a.py", "b.py"),
                "git", Map.of(
                        "remote", "myfork",
                        "base_branch", "main",
                        "user_name", "test",
                        "user_email", "test@example.com",
                        "fork_owner", "TestOwner"
                ),
                "gitcode", Map.of(
                        "username", "bot-user",
                        "access_token_env", "AUTO_TOKEN",
                        "access_token", "inline-token"
                ),
                "budget", Map.of(
                        "session_secs", 600,
                        "cost_limit_usd", 5.0,
                        "task_timeout_secs", 300,
                        "model_timeout_secs", 240,
                        "max_tasks_per_session", 2
                ),
                "ci_gate", Map.of(
                        "config_path", "/tmp/ci_gate.yaml",
                        "python_executable", "/tmp/python3.11",
                        "install_command", "uv sync --active --group dev --extra cli"
                ),
                "fix_loop", Map.of(
                        "phase1_max_retries", 4,
                        "phase2_max_retries", 3
                ),
                "agent", Map.of(
                        "implement", 12,
                        "plan", 7
                ),
                "extensions", Map.of(
                        "stage_registrars", List.of("pkg.stage:register"),
                        "pipeline_registrars", List.of("pkg.pipeline:register")
                )
        ));

        assertThat(config.getLocalRepo()).isEqualTo("./repo");
        assertThat(config.getLanguage()).isEqualTo("en");
        assertThat(config.getImmutableFiles()).containsExactly("a.py", "b.py");
        assertThat(config.getGitRemote()).isEqualTo("myfork");
        assertThat(config.getGitBaseBranch()).isEqualTo("main");
        assertThat(config.getGitUserName()).isEqualTo("test");
        assertThat(config.getGitUserEmail()).isEqualTo("test@example.com");
        assertThat(config.getForkOwner()).isEqualTo("TestOwner");
        assertThat(config.getGitcodeUsername()).isEqualTo("bot-user");
        assertThat(config.getGitcodeTokenEnv()).isEqualTo("AUTO_TOKEN");
        assertThat(config.resolveGitcodeToken()).isEqualTo("inline-token");
        assertThat(config.getSessionBudgetSecs()).isEqualTo(600.0);
        assertThat(config.getCostLimitUsd()).isEqualTo(5.0);
        assertThat(config.getTaskTimeoutSecs()).isEqualTo(300.0);
        assertThat(config.getModelTimeoutSecs()).isEqualTo(240.0);
        assertThat(config.getMaxTasksPerSession()).isEqualTo(2);
        assertThat(config.getCiGateConfig()).isEqualTo("/tmp/ci_gate.yaml");
        assertThat(config.getCiGatePythonExecutable()).isEqualTo("/tmp/python3.11");
        assertThat(config.getCiGateInstallCommand()).isEqualTo("uv sync --active --group dev --extra cli");
        assertThat(config.getFixPhase1MaxRetries()).isEqualTo(4);
        assertThat(config.getFixPhase2MaxRetries()).isEqualTo(3);
        assertThat(config.resolveAgentIterations("implement", 30)).isEqualTo(12);
        assertThat(config.resolveAgentIterations("plan", 15)).isEqualTo(7);
        assertThat(config.resolveAgentIterations("unknown", 99)).isEqualTo(99);
        assertThat(config.getStageRegistrars()).containsExactly("pkg.stage:register");
        assertThat(config.getPipelineRegistrars()).containsExactly("pkg.pipeline:register");
    }

    @Test
    void configShouldLoadSupportedTopLevelFieldsFromDict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data_dir", "/tmp/ah");
        data.put("local_repo", "./repo");
        data.put("repo_url", "https://example.com/team/demo.git");
        data.put("language", "en");
        data.put("optimization_goal", "补齐 auto harness");
        data.put("competitor", "Claude Code");
        data.put("pipeline_name", "extended_evolve_pipeline");
        data.put("workspace", "/work/repo");
        data.put("experience_dir", "/tmp/experience");
        data.put("skills_dirs", List.of("/tmp/skills"));
        data.put("stage_registrars", List.of("pkg.stage:register"));
        data.put("pipeline_registrars", List.of("pkg.pipeline:register"));
        data.put("immutable_files", List.of("immutable.py"));
        data.put("high_impact_prefixes", List.of("openjiuwen/core/"));

        AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(data);

        assertThat(config.getDataDir()).isEqualTo("/tmp/ah");
        assertThat(config.getLocalRepo()).isEqualTo("./repo");
        assertThat(config.getRepoUrl()).isEqualTo("https://example.com/team/demo.git");
        assertThat(config.getLanguage()).isEqualTo("en");
        assertThat(config.getOptimizationGoal()).isEqualTo("补齐 auto harness");
        assertThat(config.getCompetitor()).isEqualTo("Claude Code");
        assertThat(config.getPipelineName()).isEqualTo("extended_evolve_pipeline");
        assertThat(config.getWorkspace()).isEqualTo("/work/repo");
        assertThat(config.getExperienceDir()).isEqualTo("/tmp/experience");
        assertThat(config.getSkillsDirs()).containsExactly("/tmp/skills");
        assertThat(config.getStageRegistrars()).containsExactly("pkg.stage:register");
        assertThat(config.getPipelineRegistrars()).containsExactly("pkg.pipeline:register");
        assertThat(config.getImmutableFiles()).containsExactly("immutable.py");
        assertThat(config.getHighImpactPrefixes()).containsExactly("openjiuwen/core/");
    }

    @Test
    void configShouldResolveGitcodeUsernameFromForkOwner() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .forkOwner("fallback-owner")
                .build();

        assertThat(config.resolveGitcodeUsername()).isEqualTo("fallback-owner");
    }

    @Test
    void configShouldResolveCiGatePythonExecutableFromWorkspaceAndLocalRepoVenv() throws Exception {
        Path workspacePython = tempDir.resolve("workspace").resolve(".venv").resolve("bin").resolve("python");
        Path localRepoPython = tempDir.resolve("local-repo").resolve(".venv").resolve("bin").resolve("python");
        Files.createDirectories(workspacePython.getParent());
        Files.createDirectories(localRepoPython.getParent());
        Files.writeString(workspacePython, "#!/usr/bin/env python\n");
        Files.writeString(localRepoPython, "#!/usr/bin/env python\n");

        AutoHarnessConfig explicitConfig = AutoHarnessConfig.builder()
                .workspace(tempDir.resolve("workspace").toString())
                .localRepo(tempDir.resolve("local-repo").toString())
                .ciGatePythonExecutable("/tmp/python3.11")
                .build();
        AutoHarnessConfig workspaceConfig = AutoHarnessConfig.builder()
                .workspace(tempDir.resolve("workspace").toString())
                .localRepo(tempDir.resolve("local-repo").toString())
                .build();
        AutoHarnessConfig localRepoConfig = AutoHarnessConfig.builder()
                .workspace(tempDir.resolve("missing-workspace").toString())
                .localRepo(tempDir.resolve("local-repo").toString())
                .build();

        assertThat(explicitConfig.resolveCiGatePythonExecutable()).isEqualTo("/tmp/python3.11");
        assertThat(workspaceConfig.resolveCiGatePythonExecutable())
                .isEqualTo(workspacePython.toAbsolutePath().normalize().toString());
        assertThat(localRepoConfig.resolveCiGatePythonExecutable())
                .isEqualTo(localRepoPython.toAbsolutePath().normalize().toString());
    }

    @Test
    void configShouldLoadYamlFileAndDefaultDataDir() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, String.join("\n",
                "local_repo: /tmp/repo",
                "git:",
                "  remote: myfork",
                "  fork_owner: TestOwner",
                "budget:",
                "  session_secs: 900",
                ""
        ));

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile.toString());

        assertThat(config.getLocalRepo()).isEqualTo("/tmp/repo");
        assertThat(config.getGitRemote()).isEqualTo("myfork");
        assertThat(config.getForkOwner()).isEqualTo("TestOwner");
        assertThat(config.getSessionBudgetSecs()).isEqualTo(900.0);
        assertThat(config.getConfigPath()).isEqualTo(configFile.toString());
        assertThat(config.getDataDir()).isEqualTo(tempDir.toString());
    }

    @Test
    void emptyYamlShouldReturnDefaultsAndConfigMetadata() throws Exception {
        Path configFile = tempDir.resolve("empty-config.yaml");
        Files.writeString(configFile, "");

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile.toString());

        assertThat(config.getSessionBudgetSecs()).isEqualTo(3600.0);
        assertThat(config.getGitRemote()).isEmpty();
        assertThat(config.getConfigPath()).isEqualTo(configFile.toString());
        assertThat(config.getDataDir()).isEqualTo(tempDir.toString());
        assertThat(config.isConfigBootstrapped()).isFalse();
    }

    @Test
    void missingConfigShouldBootstrapTemplateAndDetectLocalRepo() throws Exception {
        Path repo = tempDir.resolve("agent-core");
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("openjiuwen"));
        Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='x'\n");
        Path configFile = tempDir.resolve("auto_harness").resolve("config.yaml");

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile.toString(), tempDir.toString());

        assertThat(config.isConfigBootstrapped()).isTrue();
        assertThat(config.getSuggestedLocalRepo()).isEqualTo(repo.toAbsolutePath().normalize().toString());
        assertThat(config.getDataDir()).isEqualTo(configFile.getParent().toString());
        assertThat(Files.readString(configFile)).contains("# local_repo: \"./agent-core\"");
        assertThat(Files.readString(configFile)).doesNotContain(repo.toString());
    }

    @Test
    void configShouldIdentifyPlaceholderLocalRepo() {
        assertThat(AutoHarnessConfig.isPlaceholderLocalRepo("./agent-core")).isTrue();
        assertThat(AutoHarnessConfig.isPlaceholderLocalRepo("./repo-with-custom-name")).isFalse();
    }
}
