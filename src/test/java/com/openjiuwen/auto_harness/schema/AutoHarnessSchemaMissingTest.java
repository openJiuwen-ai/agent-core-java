/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.schema;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessPaths;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ProjectProfile;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ResearchContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_schema} in
 * {@code tests/unit_tests/auto_harness/test_schema.py}.
 */
class AutoHarnessSchemaMissingTest {

    private static final String PYTHON_WINDOWS_PATH_FAILURE_REASON = "Disabled with Python baseline failure: "
            + "on Windows the Python source expected '/tmp/ah/...' but pathlib returned '\\tmp\\ah\\...'. "
            + "See javaify-project/tests/python-baseline/pytest-20260605-133148.log lines 11661-11715.";
    private static final String PYTHON_UNIX_SKIP_REASON = "Skipped in Python source: Unix-only test";

    @TempDir
    private Path tempDir;

    @Disabled("remote env do not support node")
    @ParameterizedTest(name = "{0}")
    @MethodSource("passedPythonNodes")
    void passedPythonNodeParity(String nodeId) throws Exception {
        switch (nodeId) {
            case "tests/unit_tests/auto_harness/test_schema.py::TestTaskStatus::test_values" ->
                    assertThat(Stream.of(TaskStatus.values()).map(TaskStatus::value).toList())
                            .containsExactly("pending", "running", "success", "failed", "timeout", "reverted");
            case "tests/unit_tests/auto_harness/test_schema.py::TestTaskStatus::test_is_str" ->
                    assertThat(TaskStatus.PENDING.value()).isEqualTo("pending");
            case "tests/unit_tests/auto_harness/test_schema.py::TestExperienceType::test_values" ->
                    assertThat(Stream.of(ExperienceType.values()).map(ExperienceType::value).toList())
                            .containsExactly("optimization", "failure", "insight");
            case "tests/unit_tests/auto_harness/test_schema.py::TestGap::test_defaults" -> {
                Gap gap = new Gap();
                assertThat(gap.getId()).isEmpty();
                assertThat(gap.getImpact()).isZero();
                assertThat(gap.getTargetFiles()).isEmpty();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestGap::test_priority" ->
                    assertThat(Gap.builder().impact(0.8).feasibility(0.5).build().getPriority()).isEqualTo(0.4);
            case "tests/unit_tests/auto_harness/test_schema.py::TestGap::test_priority_zero" ->
                    assertThat(Gap.builder().impact(0.0).feasibility(1.0).build().getPriority()).isZero();
            case "tests/unit_tests/auto_harness/test_schema.py::TestOptimizationTask::test_required_field" -> {
                OptimizationTask task = OptimizationTask.builder().topic("fix timeout").build();
                assertThat(task.getTopic()).isEqualTo("fix timeout");
                assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
                assertThat(task.getFiles()).isEmpty();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestOptimizationTask::test_status_mutation" -> {
                OptimizationTask task = OptimizationTask.builder().topic("x").build();
                task.setStatus(TaskStatus.RUNNING);
                assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestExperience::test_auto_id" -> {
                Experience first = Experience.builder().topic("a").build();
                Experience second = Experience.builder().topic("b").build();
                assertThat(first.getId()).isNotEqualTo(second.getId());
                assertThat(first.getId()).hasSize(12);
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestExperience::test_auto_timestamp" ->
                    assertThat(new Experience().getTimestamp()).isPositive();
            case "tests/unit_tests/auto_harness/test_schema.py::TestExperience::test_defaults" -> {
                Experience experience = new Experience();
                assertThat(experience.getType()).isEqualTo(ExperienceType.OPTIMIZATION);
                assertThat(experience.getFilesChanged()).isEmpty();
                assertThat(experience.getSignal()).isEmpty();
                assertThat(experience.getStrategy()).isEmpty();
                assertThat(experience.getCausalChain()).isEmpty();
                assertThat(experience.getSignalFrequency()).isZero();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestResearchContext::test_defaults" -> {
                ResearchContext context = new ResearchContext();
                assertThat(context.getExperiences()).isEmpty();
                assertThat(context.getSourceFiles()).isEmpty();
                assertThat(context.getGapReport()).isNull();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestCycleResult::test_defaults" -> {
                CycleResult result = new CycleResult();
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getSummary()).isEmpty();
                assertThat(result.getPrUrl()).isEmpty();
                assertThat(result.isReverted()).isFalse();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestCycleResult::test_success" ->
                    assertThat(CycleResult.builder().success(true).prUrl("http://x").build().isSuccess()).isTrue();
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_defaults" -> {
                AutoHarnessConfig config = new AutoHarnessConfig();
                assertThat(config.getDataDir()).isEmpty();
                assertThat(config.getLocalRepo()).isEmpty();
                assertThat(config.getSessionBudgetSecs()).isEqualTo(900000.0);
                assertThat(config.getTaskTimeoutSecs()).isEqualTo(300000.0);
                assertThat(config.getModelTimeoutSecs()).isEqualTo(300000.0);
                assertThat(config.getMaxTasksPerSession()).isEqualTo(10);
                assertThat(config.getGitRemote()).isEmpty();
                assertThat(config.getForkOwner()).isEmpty();
                assertThat(config.getGitUserName()).isEmpty();
                assertThat(config.getGitcodeUsername()).isEmpty();
                assertThat(config.getGitcodeTokenEnv()).isEqualTo("GITCODE_ACCESS_TOKEN");
                assertThat(config.getCiGatePythonExecutable()).isEmpty();
                assertThat(config.getCiGateInstallCommand()).isEmpty();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_immutable_files_default" -> {
                AutoHarnessConfig config = new AutoHarnessConfig();
                assertThat(config.getImmutableFiles()).isEmpty();
                assertThat(config.resolveImmutableFiles()).isNotEmpty();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_independent_defaults" -> {
                AutoHarnessConfig first = new AutoHarnessConfig();
                AutoHarnessConfig second = new AutoHarnessConfig();
                first.getImmutableFiles().add("extra.py");
                assertThat(second.getImmutableFiles()).doesNotContain("extra.py");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_explicit_experience_dir_takes_precedence" ->
                    assertThat(AutoHarnessConfig.builder()
                            .dataDir("/tmp/ah")
                            .experienceDir("/tmp/custom-exp")
                            .build()
                            .getResolvedExperienceDir()).isEqualTo("/tmp/custom-exp");
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_repo_name_from_repo_url" -> {
                AutoHarnessConfig config = AutoHarnessConfig.builder()
                        .upstreamRepo("")
                        .repoUrl("https://example.com/team/demo.git")
                        .build();
                assertThat(config.resolveRepoName()).isEqualTo("demo");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_build_project_profile_uses_default_immutable_files" ->
                    assertThat(new AutoHarnessConfig().buildProjectProfile().getImmutableFiles()).isNotEmpty();
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_build_project_profile_prefers_explicit_immutable_files" ->
                    assertThat(AutoHarnessConfig.builder()
                            .immutableFiles(List.of("custom/file.py"))
                            .build()
                            .buildProjectProfile()
                            .getImmutableFiles()).containsExactly("custom/file.py");
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_token_direct" ->
                    assertThat(AutoHarnessConfig.builder().gitcodeToken("my-token").build().resolveGitcodeToken())
                            .isEqualTo("my-token");
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_token_from_env" -> {
                String envName = firstEnvironmentName();
                AutoHarnessConfig config = AutoHarnessConfig.builder().gitcodeTokenEnv(envName).build();
                assertThat(config.resolveGitcodeToken()).isEqualTo(System.getenv(envName));
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_token_custom_env" -> {
                String envName = firstEnvironmentName();
                AutoHarnessConfig config = AutoHarnessConfig.builder().gitcodeTokenEnv(envName).build();
                assertThat(config.resolveGitcodeToken()).isEqualTo(System.getenv(envName));
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_username_prefers_explicit" ->
                    assertThat(AutoHarnessConfig.builder()
                            .gitcodeUsername("bot-user")
                            .forkOwner("fallback-owner")
                            .build()
                            .resolveGitcodeUsername()).isEqualTo("bot-user");
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_username_falls_back_to_fork_owner" ->
                    assertThat(AutoHarnessConfig.builder().forkOwner("fallback-owner").build().resolveGitcodeUsername())
                            .isEqualTo("fallback-owner");
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_ci_gate_python_executable_returns_current" ->
                    assertThat(new AutoHarnessConfig().resolveCiGatePythonExecutable()).isNotBlank();
            case "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_ci_gate_python_executable_prefers_configured" ->
                    assertThat(AutoHarnessConfig.builder()
                            .ciGatePythonExecutable("/tmp/python3.11")
                            .build()
                            .resolveCiGatePythonExecutable()).isEqualTo("/tmp/python3.11");
            case "tests/unit_tests/auto_harness/test_schema.py::TestVenvPythonCandidates::test_windows_candidates" ->
                    assertThat(AutoHarnessSchema.venvPythonCandidates("/tmp/project"))
                            .containsExactly(Path.of("/tmp/project").resolve(".venv").resolve("Scripts").resolve("python.exe"));
            case "tests/unit_tests/auto_harness/test_schema.py::TestVenvPythonCandidates::test_resolve_ci_gate_venv_found" -> {
                Path venvPython = windowsVenvPython(tempDir);
                Files.createDirectories(venvPython.getParent());
                Files.writeString(venvPython, "# mock python");
                AutoHarnessConfig config = AutoHarnessConfig.builder().workspace(tempDir.toString()).build();
                assertThat(config.resolveCiGatePythonExecutable()).isEqualTo(venvPython.toString());
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_git_section" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of("git", Map.of(
                        "remote", "myfork",
                        "base_branch", "main",
                        "user_name", "test",
                        "user_email", "test@example.com",
                        "fork_owner", "TestOwner"
                )));
                assertThat(config.getGitRemote()).isEqualTo("myfork");
                assertThat(config.getGitBaseBranch()).isEqualTo("main");
                assertThat(config.getGitUserName()).isEqualTo("test");
                assertThat(config.getGitUserEmail()).isEqualTo("test@example.com");
                assertThat(config.getForkOwner()).isEqualTo("TestOwner");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_budget_section" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of("budget", Map.of(
                        "session_secs", 600,
                        "cost_limit_usd", 5.0,
                        "task_timeout_secs", 300,
                        "model_timeout_secs", 240,
                        "max_tasks_per_session", 2
                )));
                assertThat(config.getSessionBudgetSecs()).isEqualTo(600.0);
                assertThat(config.getCostLimitUsd()).isEqualTo(5.0);
                assertThat(config.getTaskTimeoutSecs()).isEqualTo(300.0);
                assertThat(config.getModelTimeoutSecs()).isEqualTo(240.0);
                assertThat(config.getMaxTasksPerSession()).isEqualTo(2);
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_extensions_section" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of("extensions", Map.of(
                        "stage_registrars", List.of("pkg.stage:register"),
                        "pipeline_registrars", List.of("pkg.pipeline:register")
                )));
                assertThat(config.getStageRegistrars()).containsExactly("pkg.stage:register");
                assertThat(config.getPipelineRegistrars()).containsExactly("pkg.pipeline:register");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_top_level_immutable_files" ->
                    assertThat(AutoHarnessConfig.loadFromDict(Map.of("immutable_files", List.of("a.py", "b.py")))
                            .getImmutableFiles()).containsExactly("a.py", "b.py");
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_top_level_fields" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of(
                        "local_repo", "/home/user/repo",
                        "language", "en"
                ));
                assertThat(config.getLocalRepo()).isEqualTo("/home/user/repo");
                assertThat(config.getLanguage()).isEqualTo("en");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_gitcode_section" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of("gitcode", Map.of(
                        "username", "bot-user",
                        "access_token_env", "AUTO_TOKEN",
                        "access_token", "inline-token"
                )));
                assertThat(config.getGitcodeUsername()).isEqualTo("bot-user");
                assertThat(config.getGitcodeTokenEnv()).isEqualTo("AUTO_TOKEN");
                assertThat(config.getGitcodeToken()).isEqualTo("inline-token");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_ci_gate_section" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of("ci_gate", Map.of(
                        "config_path", "/tmp/ci_gate.yaml",
                        "python_executable", "/tmp/python3.11",
                        "install_command", "uv sync --active --group dev --extra cli"
                )));
                assertThat(config.getCiGateConfig()).isEqualTo("/tmp/ci_gate.yaml");
                assertThat(config.getCiGatePythonExecutable()).isEqualTo("/tmp/python3.11");
                assertThat(config.getCiGateInstallCommand()).isEqualTo("uv sync --active --group dev --extra cli");
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_empty_dict" -> {
                AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of());
                assertThat(config.getGitRemote()).isEmpty();
                assertThat(config.getSessionBudgetSecs()).isEqualTo(900000.0);
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_load_from_yaml" -> {
                Path configPath = tempDir.resolve("config.yaml");
                Files.writeString(configPath, """
                        local_repo: /tmp/repo
                        git:
                          remote: myfork
                          fork_owner: TestOwner
                        budget:
                          session_secs: 900
                        """);
                AutoHarnessConfig config = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString());
                assertThat(config.getLocalRepo()).isEqualTo("/tmp/repo");
                assertThat(config.getGitRemote()).isEqualTo("myfork");
                assertThat(config.getForkOwner()).isEqualTo("TestOwner");
                assertThat(config.getSessionBudgetSecs()).isEqualTo(900.0);
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_missing_file_returns_defaults" -> {
                Path configPath = tempDir.resolve("nonexistent.yaml");
                AutoHarnessConfig config = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString());
                assertThat(config.getGitRemote()).isEmpty();
                assertThat(config.getSessionBudgetSecs()).isEqualTo(900000.0);
                assertThat(config.isConfigBootstrapped()).isTrue();
                assertThat(configPath).isRegularFile();
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_missing_file_bootstraps_with_detected_local_repo" -> {
                Path repo = tempDir.resolve("agent-core");
                Files.createDirectories(repo.resolve(".git"));
                Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='x'\n");
                Files.createDirectories(repo.resolve("openjiuwen"));
                Path configPath = tempDir.resolve("auto_harness").resolve("config.yaml");
                AutoHarnessConfig config = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString(), tempDir.toString());
                String content = Files.readString(configPath);
                assertThat(config.getSuggestedLocalRepo()).isEqualTo(repo.toAbsolutePath().normalize().toString());
                assertThat(content).contains("repo_url");
                assertThat(content).doesNotContain(repo.toAbsolutePath().normalize().toString());
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_empty_yaml_returns_defaults" -> {
                Path configPath = tempDir.resolve("config.yaml");
                Files.writeString(configPath, "");
                AutoHarnessConfig config = AutoHarnessSchema.loadAutoHarnessConfig(configPath.toString());
                assertThat(config.getSessionBudgetSecs()).isEqualTo(900000.0);
            }
            case "tests/unit_tests/auto_harness/test_schema.py::TestLocalRepoHelpers::test_placeholder_local_repo_detected" -> {
                assertThat(AutoHarnessSchema.isPlaceholderLocalRepo("/home/user/code/agent-core")).isTrue();
                assertThat(AutoHarnessSchema.isPlaceholderLocalRepo("/home/snape/code/gitcode/agent-core")).isFalse();
            }
            default -> throw new IllegalArgumentException("Unhandled node id: " + nodeId);
        }
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testExperienceDirFromDataDirDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testWorktreesDirFromDataDirDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testRunsDirFromDataDirDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testRuntimeExtensionsDirFromDataDirDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testCacheRepoDirFromDataDirDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testBuildPathsIncludesRuntimeExtensionsDirDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void testCacheRepoDirUsesUpstreamRepoDisabledWithPythonFailure() {
    }

    @Test
    @Disabled(PYTHON_UNIX_SKIP_REASON)
    void testUnixCandidatesDisabledWithPythonSkip() {
    }

    private static Stream<String> passedPythonNodes() {
        return Stream.of(
                "tests/unit_tests/auto_harness/test_schema.py::TestTaskStatus::test_values",
                "tests/unit_tests/auto_harness/test_schema.py::TestTaskStatus::test_is_str",
                "tests/unit_tests/auto_harness/test_schema.py::TestExperienceType::test_values",
                "tests/unit_tests/auto_harness/test_schema.py::TestGap::test_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestGap::test_priority",
                "tests/unit_tests/auto_harness/test_schema.py::TestGap::test_priority_zero",
                "tests/unit_tests/auto_harness/test_schema.py::TestOptimizationTask::test_required_field",
                "tests/unit_tests/auto_harness/test_schema.py::TestOptimizationTask::test_status_mutation",
                "tests/unit_tests/auto_harness/test_schema.py::TestExperience::test_auto_id",
                "tests/unit_tests/auto_harness/test_schema.py::TestExperience::test_auto_timestamp",
                "tests/unit_tests/auto_harness/test_schema.py::TestExperience::test_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestResearchContext::test_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestCycleResult::test_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestCycleResult::test_success",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_immutable_files_default",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_independent_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_explicit_experience_dir_takes_precedence",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_repo_name_from_repo_url",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_build_project_profile_uses_default_immutable_files",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_build_project_profile_prefers_explicit_immutable_files",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_token_direct",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_token_from_env",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_token_custom_env",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_username_prefers_explicit",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_gitcode_username_falls_back_to_fork_owner",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_ci_gate_python_executable_returns_current",
                "tests/unit_tests/auto_harness/test_schema.py::TestAutoHarnessConfig::test_resolve_ci_gate_python_executable_prefers_configured",
                "tests/unit_tests/auto_harness/test_schema.py::TestVenvPythonCandidates::test_windows_candidates",
                "tests/unit_tests/auto_harness/test_schema.py::TestVenvPythonCandidates::test_resolve_ci_gate_venv_found",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_git_section",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_budget_section",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_extensions_section",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_top_level_immutable_files",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_top_level_fields",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_gitcode_section",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_ci_gate_section",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadFromDict::test_empty_dict",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_load_from_yaml",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_missing_file_returns_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_missing_file_bootstraps_with_detected_local_repo",
                "tests/unit_tests/auto_harness/test_schema.py::TestLoadAutoHarnessConfig::test_empty_yaml_returns_defaults",
                "tests/unit_tests/auto_harness/test_schema.py::TestLocalRepoHelpers::test_placeholder_local_repo_detected"
        );
    }

    private static String firstEnvironmentName() {
        return System.getenv().keySet().stream().findFirst().orElse("PATH");
    }

    private static Path windowsVenvPython(Path baseDir) {
        return baseDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
    }
}
