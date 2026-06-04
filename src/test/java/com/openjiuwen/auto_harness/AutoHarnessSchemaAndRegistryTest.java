package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.schema.Gap;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.ProjectProfile;
import com.openjiuwen.auto_harness.schema.ResearchContext;
import com.openjiuwen.auto_harness.schema.TaskStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.test_schema}.
 */
class AutoHarnessSchemaAndRegistryTest {

    @Test
    void testTaskStatusValues() {
        assertEquals("pending", TaskStatus.PENDING.toString());
        assertEquals("running", TaskStatus.RUNNING.toString());
        assertEquals("success", TaskStatus.SUCCESS.toString());
        assertEquals("failed", TaskStatus.FAILED.toString());
        assertEquals("timeout", TaskStatus.TIMEOUT.toString());
        assertEquals("reverted", TaskStatus.REVERTED.toString());
    }

    @Test
    void testTaskStatusIsStringLike() {
        assertEquals("pending", TaskStatus.PENDING.toString());
    }

    @Test
    void testExperienceTypeValues() {
        assertEquals("optimization", ExperienceType.OPTIMIZATION.toString());
        assertEquals("failure", ExperienceType.FAILURE.toString());
        assertEquals("insight", ExperienceType.INSIGHT.toString());
    }

    @Test
    void testGapDefaults() {
        Gap gap = new Gap();
        assertEquals("", gap.getId());
        assertEquals(0.0, gap.getImpact());
        assertEquals(List.of(), gap.getTargetFiles());
    }

    @Test
    void testGapPriority() {
        Gap gap = new Gap();
        gap.setImpact(0.8);
        gap.setFeasibility(0.5);
        assertEquals(0.4, gap.getPriority(), 1e-9);
    }

    @Test
    void testGapPriorityZero() {
        Gap gap = new Gap();
        gap.setImpact(0.0);
        gap.setFeasibility(1.0);
        assertEquals(0.0, gap.getPriority());
    }

    @Test
    void testOptimizationTaskRequiredField() {
        OptimizationTask task = new OptimizationTask("fix timeout");
        assertEquals("fix timeout", task.getTopic());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(List.of(), task.getFiles());
    }

    @Test
    void testOptimizationTaskStatusMutation() {
        OptimizationTask task = new OptimizationTask("x");
        task.setStatus(TaskStatus.RUNNING);
        assertEquals(TaskStatus.RUNNING, task.getStatus());
    }

    @Test
    void testExperienceAutoId() {
        Experience first = new Experience();
        first.setTopic("a");
        Experience second = new Experience();
        second.setTopic("b");
        assertNotEquals(first.getId(), second.getId());
        assertEquals(12, first.getId().length());
    }

    @Test
    void testExperienceAutoTimestamp() {
        Experience experience = new Experience();
        assertTrue(experience.getTimestamp() > 0);
    }

    @Test
    void testExperienceDefaults() {
        Experience experience = new Experience();
        assertEquals(ExperienceType.OPTIMIZATION, experience.getType());
        assertEquals(List.of(), experience.getFilesChanged());
    }

    @Test
    void testResearchContextDefaults() {
        ResearchContext context = new ResearchContext();
        assertEquals(List.of(), context.getExperiences());
        assertTrue(context.getSourceFiles().isEmpty());
        assertNull(context.getGapReport());
    }

    @Test
    void testCycleResultDefaults() {
        CycleResult result = new CycleResult();
        assertFalse(result.isSuccess());
        assertEquals("", result.getSummary());
        assertEquals("", result.getPrUrl());
        assertFalse(result.isReverted());
    }

    @Test
    void testCycleResultSuccess() {
        CycleResult result = new CycleResult();
        result.setSuccess(true);
        result.setPrUrl("http://x");
        assertTrue(result.isSuccess());
    }

    @Test
    void testAutoHarnessConfigDefaults() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        assertEquals("", config.getDataDir());
        assertEquals("", config.getLocalRepo());
        assertEquals(3600.0, config.getSessionBudgetSecs());
        assertEquals(300.0, config.getModelTimeoutSecs());
        assertEquals(3, config.getMaxTasksPerSession());
        assertEquals("", config.getGitRemote());
        assertEquals("", config.getForkOwner());
        assertEquals("", config.getGitUserName());
        assertEquals("", config.getGitcodeUsername());
        assertEquals("GITCODE_ACCESS_TOKEN", config.getGitcodeTokenEnv());
        assertEquals("", config.getCiGatePythonExecutable());
        assertEquals("", config.getCiGateInstallCommand());
    }

    @Test
    void testImmutableFilesDefault() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        assertEquals(List.of(), config.getImmutableFiles());
        assertTrue(config.resolveImmutableFiles().size() >= 1);
    }

    @Test
    void testIndependentDefaults() {
        AutoHarnessConfig first = new AutoHarnessConfig();
        AutoHarnessConfig second = new AutoHarnessConfig();
        first.getImmutableFiles().add("extra.py");
        assertFalse(second.getImmutableFiles().contains("extra.py"));
    }

    @Test
    void testExperienceDirFromDataDir() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        assertEquals("/tmp/ah/experience", slash(config.getResolvedExperienceDir()));
    }

    @Test
    void testExplicitExperienceDirTakesPrecedence() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        config.setExperienceDir("/tmp/custom-exp");
        assertEquals("/tmp/custom-exp", slash(config.getResolvedExperienceDir()));
    }

    @Test
    void testWorktreesDirFromDataDir() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        assertEquals("/tmp/ah/worktrees", slash(config.getWorktreesDir()));
    }

    @Test
    void testRunsDirFromDataDir() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        assertEquals("/tmp/ah/runs", slash(config.getRunsDir()));
    }

    @Test
    void testCacheRepoDirFromDataDir() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        assertEquals("/tmp/ah/repo/agent-core", slash(config.getCacheRepoDir()));
    }

    @Test
    void testCacheRepoDirUsesUpstreamRepo() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        config.setUpstreamRepo("custom-repo");
        assertEquals("/tmp/ah/repo/custom-repo", slash(config.getCacheRepoDir()));
    }

    @Test
    void testResolveRepoNameFromRepoUrl() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setUpstreamRepo("");
        config.setRepoUrl("https://example.com/team/demo.git");
        assertEquals("demo", config.resolveRepoName());
    }

    @Test
    void testBuildProjectProfileUsesDefaultImmutableFiles() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        ProjectProfile profile = config.buildProjectProfile();
        assertTrue(profile.getImmutableFiles().size() >= 1);
    }

    @Test
    void testBuildProjectProfilePrefersExplicitImmutableFiles() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setImmutableFiles(List.of("custom/file.py"));
        ProjectProfile profile = config.buildProjectProfile();
        assertEquals(List.of("custom/file.py"), profile.getImmutableFiles());
    }

    @Test
    void testResolveGitcodeTokenDirect() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setGitcodeToken("my-token");
        assertEquals("my-token", config.resolveGitcodeToken(Map.of()));
    }

    @Test
    void testResolveGitcodeTokenFromEnv() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        assertEquals("env-token", config.resolveGitcodeToken(Map.of("GITCODE_ACCESS_TOKEN", "env-token")));
    }

    @Test
    void testResolveGitcodeTokenCustomEnv() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setGitcodeTokenEnv("MY_TOKEN");
        assertEquals("custom", config.resolveGitcodeToken(Map.of("MY_TOKEN", "custom")));
    }

    @Test
    void testResolveGitcodeUsernamePrefersExplicit() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setGitcodeUsername("bot-user");
        config.setForkOwner("fallback-owner");
        assertEquals("bot-user", config.resolveGitcodeUsername());
    }

    @Test
    void testResolveGitcodeUsernameFallsBackToForkOwner() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setForkOwner("fallback-owner");
        assertEquals("fallback-owner", config.resolveGitcodeUsername());
    }

    @Test
    void testResolveCiGatePythonExecutableReturnsCurrent() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        assertFalse(config.resolveCiGatePythonExecutable().isBlank());
    }

    @Test
    void testResolveCiGatePythonExecutablePrefersConfigured() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setCiGatePythonExecutable("/tmp/python3.11");
        assertEquals("/tmp/python3.11", config.resolveCiGatePythonExecutable());
    }

    @Test
    void testLoadFromDictGitSection() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of(
                "git",
                Map.of(
                        "remote", "myfork",
                        "base_branch", "main",
                        "user_name", "test",
                        "user_email", "test@example.com",
                        "fork_owner", "TestOwner"
                )
        ));
        assertEquals("myfork", config.getGitRemote());
        assertEquals("main", config.getGitBaseBranch());
        assertEquals("test", config.getGitUserName());
        assertEquals("test@example.com", config.getGitUserEmail());
        assertEquals("TestOwner", config.getForkOwner());
    }

    @Test
    void testLoadFromDictBudgetSection() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of(
                "budget",
                Map.of(
                        "session_secs", 600,
                        "cost_limit_usd", 5.0,
                        "task_timeout_secs", 300,
                        "model_timeout_secs", 240,
                        "max_tasks_per_session", 2
                )
        ));
        assertEquals(600.0, config.getSessionBudgetSecs());
        assertEquals(5.0, config.getCostLimitUsd());
        assertEquals(300.0, config.getTaskTimeoutSecs());
        assertEquals(240.0, config.getModelTimeoutSecs());
        assertEquals(2, config.getMaxTasksPerSession());
    }

    @Test
    void testLoadFromDictExtensionsSection() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of(
                "extensions",
                Map.of(
                        "stage_registrars", List.of("pkg.stage:register"),
                        "pipeline_registrars", List.of("pkg.pipeline:register")
                )
        ));
        assertEquals(List.of("pkg.stage:register"), config.getStageRegistrars());
        assertEquals(List.of("pkg.pipeline:register"), config.getPipelineRegistrars());
    }

    @Test
    void testLoadFromDictTopLevelImmutableFiles() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of("immutable_files", List.of("a.py", "b.py")));
        assertEquals(List.of("a.py", "b.py"), config.getImmutableFiles());
    }

    @Test
    void testLoadFromDictTopLevelFields() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of(
                "local_repo", "/home/user/repo",
                "language", "en"
        ));
        assertEquals("/home/user/repo", config.getLocalRepo());
        assertEquals("en", config.getLanguage());
    }

    @Test
    void testLoadFromDictGitcodeSection() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of(
                "gitcode",
                Map.of(
                        "username", "bot-user",
                        "access_token_env", "AUTO_TOKEN",
                        "access_token", "inline-token"
                )
        ));
        assertEquals("bot-user", config.getGitcodeUsername());
        assertEquals("AUTO_TOKEN", config.getGitcodeTokenEnv());
        assertEquals("inline-token", config.getGitcodeToken());
    }

    @Test
    void testLoadFromDictCiGateSection() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of(
                "ci_gate",
                Map.of(
                        "config_path", "/tmp/ci_gate.yaml",
                        "python_executable", "/tmp/python3.11",
                        "install_command", "uv sync --active --group dev --extra cli"
                )
        ));
        assertEquals("/tmp/ci_gate.yaml", config.getCiGateConfig());
        assertEquals("/tmp/python3.11", config.getCiGatePythonExecutable());
        assertEquals("uv sync --active --group dev --extra cli", config.getCiGateInstallCommand());
    }

    @Test
    void testLoadFromDictEmptyDict() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromMap(Map.of());
        assertEquals("", config.getGitRemote());
        assertEquals(3600.0, config.getSessionBudgetSecs());
    }

    @Test
    void testLoadAutoHarnessConfigFromYaml(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(
                configFile,
                "local_repo: /tmp/repo\n"
                        + "git:\n"
                        + "  remote: myfork\n"
                        + "  fork_owner: TestOwner\n"
                        + "budget:\n"
                        + "  session_secs: 900\n",
                StandardCharsets.UTF_8
        );

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile);

        assertEquals("/tmp/repo", slash(config.getLocalRepo()));
        assertEquals("myfork", config.getGitRemote());
        assertEquals("TestOwner", config.getForkOwner());
        assertEquals(900.0, config.getSessionBudgetSecs());
    }

    @Test
    void testMissingFileReturnsDefaults(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("nonexistent.yaml");

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile);

        assertEquals("", config.getGitRemote());
        assertEquals(3600.0, config.getSessionBudgetSecs());
        assertTrue(config.isConfigBootstrapped());
        assertTrue(Files.isRegularFile(configFile));
    }

    @Test
    void testMissingFileBootstrapsWithDetectedLocalRepo(@TempDir Path tempDir) throws IOException {
        Path repo = tempDir.resolve("agent-core");
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve("pyproject.toml"), "[project]\nname='x'\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("openjiuwen"));
        Path configFile = tempDir.resolve("auto_harness").resolve("config.yaml");

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile, tempDir.toString());

        assertEquals(repo.toAbsolutePath().normalize().toString(), config.getSuggestedLocalRepo());
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("# local_repo: \"/home/user/code/agent-core\""));
        assertFalse(content.contains(repo.toAbsolutePath().normalize().toString()));
    }

    @Test
    void testEmptyYamlReturnsDefaults(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, "", StandardCharsets.UTF_8);

        AutoHarnessConfig config = AutoHarnessConfig.loadAutoHarnessConfig(configFile);

        assertEquals(3600.0, config.getSessionBudgetSecs());
    }

    @Test
    void testPlaceholderLocalRepoDetected() {
        assertTrue(AutoHarnessConfig.isPlaceholderLocalRepo("/home/user/code/agent-core"));
        assertFalse(AutoHarnessConfig.isPlaceholderLocalRepo("/home/snape/code/gitcode/agent-core"));
    }

    private static String slash(String path) {
        return path.replace('\\', '/');
    }
}
