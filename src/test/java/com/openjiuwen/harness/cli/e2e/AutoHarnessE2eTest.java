/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.harness.cli.AutoHarnessCliSupport;
import com.openjiuwen.harness.cli.AutoHarnessRunRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: auto-harness config loading, worktree isolation, CLI parameter overrides.
 * <p>
 * Mirrors Python's {@code test_auto_harness} in
 * {@code tests.cli.e2e.test_auto_harness}.
 */
class AutoHarnessE2eTest {

    @TempDir
    Path tmpPath;

    @Test
    void loadFullConfigFromYaml() throws IOException {
        String yaml = """
                local_repo: /home/user/agent-core
                git:
                  remote: myfork
                  base_branch: main
                  user_name: test-user
                  user_email: test@example.com
                  fork_owner: TestOwner
                  upstream_owner: openJiuwen
                  upstream_repo: agent-core
                gitcode:
                  access_token_env: MY_TOKEN
                budget:
                  session_secs: 1800
                  cost_limit_usd: 5.0
                  task_timeout_secs: 600
                  max_tasks_per_session: 2
                fix_loop:
                  phase1_max_retries: 5
                  phase2_max_retries: 3
                """;
        Path cfgFile = tmpPath.resolve("config.yaml");
        Files.writeString(cfgFile, yaml);

        AutoHarnessConfig cfg = AutoHarnessConfig.loadAutoHarnessConfig(cfgFile);

        assertEquals("/home/user/agent-core", cfg.getLocalRepo());
        assertEquals("myfork", cfg.getGitRemote());
        assertEquals("main", cfg.getGitBaseBranch());
        assertEquals("test-user", cfg.getGitUserName());
        assertEquals("test@example.com", cfg.getGitUserEmail());
        assertEquals("TestOwner", cfg.getForkOwner());
        assertEquals("MY_TOKEN", cfg.getGitcodeTokenEnv());
        assertEquals(1800.0, cfg.getSessionBudgetSecs());
        assertEquals(5.0, cfg.getCostLimitUsd());
        assertEquals(600.0, cfg.getTaskTimeoutSecs());
        assertEquals(2, cfg.getMaxTasksPerSession());
        assertEquals(5, cfg.getFixPhase1MaxRetries());
        assertEquals(3, cfg.getFixPhase2MaxRetries());
    }

    @Test
    void missingConfigUsesDefaults() {
        AutoHarnessConfig cfg = AutoHarnessConfig.loadAutoHarnessConfig(tmpPath.resolve("missing.yaml"));

        assertEquals("", cfg.getGitRemote());
        assertEquals("", cfg.getForkOwner());
        assertEquals(3600.0, cfg.getSessionBudgetSecs());
        assertEquals("develop", cfg.getGitBaseBranch());
        assertEquals(tmpPath.toAbsolutePath().toString(), Path.of(cfg.getDataDir()).toString());
    }

    @Test
    void partialConfigMerges() throws IOException {
        Path cfgFile = tmpPath.resolve("config.yaml");
        Files.writeString(cfgFile, """
                git:
                  remote: partial-fork
                """);

        AutoHarnessConfig cfg = AutoHarnessConfig.loadAutoHarnessConfig(cfgFile);

        assertEquals("partial-fork", cfg.getGitRemote());
        assertEquals("develop", cfg.getGitBaseBranch());
        assertEquals(3600.0, cfg.getSessionBudgetSecs());
    }

    @Test
    void worktreeLifecycle() {
        Path localRepo = tmpPath.resolve("local_repo");
        Path dataDir = tmpPath.resolve("data");
        mkdir(localRepo);
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());
        cfg.setLocalRepo(localRepo.toString());
        cfg.setGitBaseBranch("develop");
        cfg.setGitUserName("test");
        cfg.setGitUserEmail("test@e2e.local");
        WorktreeManager manager = new WorktreeManager(cfg, fakeGitRunner(), () -> 123L);

        String wtPath = manager.prepare("optimize-retrieval");

        assertTrue(wtPath.replace('\\', '/').contains("/data/worktrees/123-optimize-retrieval"));
        assertTrue(Files.exists(Path.of(wtPath)));

        manager.cleanup(wtPath);
        assertFalse(Files.exists(Path.of(wtPath)));
    }

    @Test
    void autoCloneWhenLocalRepoMissing() {
        Path dataDir = tmpPath.resolve("data");
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());
        cfg.setLocalRepo("");
        cfg.setGitBaseBranch("develop");
        AtomicBoolean cloneCalled = new AtomicBoolean(false);
        WorktreeManager manager = new WorktreeManager(cfg, (args, cwd, env) -> {
            if (args.size() >= 1 && "clone".equals(args.get(0))) {
                cloneCalled.set(true);
                mkdir(Path.of(args.get(args.size() - 1)).resolve(".git"));
            }
            if (args.size() >= 2 && "worktree".equals(args.get(0)) && "add".equals(args.get(1))) {
                mkdir(Path.of(args.get(4)));
            }
            if (args.size() >= 2 && "show-ref".equals(args.get(0))) {
                return new WorktreeManager.GitResult(1, "");
            }
            if (args.size() >= 2 && "remote".equals(args.get(0)) && "get-url".equals(args.get(1))) {
                return new WorktreeManager.GitResult(1, "");
            }
            return new WorktreeManager.GitResult(0, "ok");
        }, () -> 456L);

        String wtPath = manager.prepare("test-clone");

        assertTrue(cloneCalled.get());
        assertTrue(wtPath.replace('\\', '/').contains("/data/worktrees/456-test-clone"));
    }

    @Test
    void budgetOverride() throws IOException {
        Path cfgFile = tmpPath.resolve("config.yaml");
        Files.writeString(cfgFile, """
                budget:
                  session_secs: 3600
                """);
        AutoHarnessConfig cfg = AutoHarnessConfig.loadAutoHarnessConfig(cfgFile);
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setBudget(600.0);

        AutoHarnessCliSupport.applyRequest(cfg, request);

        assertEquals(600.0, cfg.getSessionBudgetSecs());
        assertTrue(cfg.getTaskTimeoutSecs() <= 570.0);
    }

    @Test
    void noPushOverride() throws IOException {
        Path cfgFile = tmpPath.resolve("config.yaml");
        Files.writeString(cfgFile, """
                git:
                  remote: myfork
                """);
        AutoHarnessConfig cfg = AutoHarnessConfig.loadAutoHarnessConfig(cfgFile);
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setNoPush(true);

        AutoHarnessCliSupport.applyRequest(cfg, request);

        assertEquals("", cfg.getGitRemote());
    }

    @Test
    void pathsUnderDataDir() {
        String dataDir = tmpPath.resolve("auto_harness").toString();
        String normalizedDataDir = dataDir.replace('\\', '/');
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir);

        assertTrue(cfg.getResolvedExperienceDir().startsWith(normalizedDataDir));
        assertTrue(cfg.getRunsDir().startsWith(normalizedDataDir));
        assertTrue(cfg.getWorktreesDir().startsWith(normalizedDataDir));
        assertTrue(cfg.getCacheRepoDir().startsWith(normalizedDataDir));
        assertFalse(cfg.getResolvedExperienceDir().contains("worktrees"));
        assertFalse(cfg.getRunsDir().contains("worktrees"));
    }

    @Test
    void worktreeNotPolluted() {
        Path localRepo = tmpPath.resolve("repo");
        Path dataDir = tmpPath.resolve("data");
        mkdir(localRepo);
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());
        cfg.setLocalRepo(localRepo.toString());
        WorktreeManager manager = new WorktreeManager(cfg, fakeGitRunner(), () -> 789L);

        String wtPath = manager.prepare("test-iso");

        assertFalse(Files.exists(Path.of(wtPath).resolve("experience")));
        assertFalse(Files.exists(Path.of(wtPath).resolve("runs")));
        assertEquals(dataDir.resolve("experience").toString().replace('\\', '/'), cfg.getResolvedExperienceDir());
        assertEquals(dataDir.resolve("runs").toString().replace('\\', '/'), cfg.getRunsDir());
    }

    @Test
    void runWithTask() {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setTask("fix-lint");

        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(
                new AutoHarnessCliSupport.CliOptions(),
                request,
                tmpPath);

        assertEquals(List.of("fix-lint"), prepared.getTasks());
    }

    @Test
    void runWithoutTask() {
        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(
                new AutoHarnessCliSupport.CliOptions(),
                new AutoHarnessRunRequest(),
                tmpPath);

        assertNull(prepared.getTasks());
    }

    @Test
    void dryRunSkipsExecutionPlan() {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setTask("test-task");
        request.setDryRun(true);

        AutoHarnessCliSupport.PreparedRun prepared = AutoHarnessCliSupport.prepareRun(
                new AutoHarnessCliSupport.CliOptions(),
                request,
                tmpPath);

        assertTrue(request.isDryRun());
        assertEquals(List.of("test-task"), prepared.getTasks());
    }

    @Test
    void budgetAndNoPushOverride() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setGitRemote("myfork");
        cfg.setTaskTimeoutSecs(600.0);
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setBudget(120.0);
        request.setNoPush(true);

        AutoHarnessCliSupport.applyRequest(cfg, request);

        assertEquals(120.0, cfg.getSessionBudgetSecs());
        assertEquals("", cfg.getGitRemote());
        assertTrue(cfg.getTaskTimeoutSecs() <= 114.0);
    }

    @Test
    void worktreeIsolationCreatesSeparateBranch() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(tmpPath.resolve("data").toString());
        cfg.setLocalRepo(tmpPath.resolve("local_repo").toString());
        WorktreeManager manager = new WorktreeManager(cfg);

        String first = manager.prepareSync("optimize retrieval");
        String second = manager.prepareSync("fix lint");

        assertTrue(first.replace('\\', '/').contains("/data/worktrees/optimize-retrieval"));
        assertTrue(second.replace('\\', '/').contains("/data/worktrees/fix-lint"));
        assertNotEquals(first, second);
    }

    @Test
    void cliOverrideSetsBudgetAndMaxTasks() {
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setGitRemote("myfork");
        cfg.setTaskTimeoutSecs(600.0);

        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setBudget(120.0);
        request.setNoPush(true);
        request.setGoal("stabilize tests");
        request.setCompetitor("competitor-x");

        AutoHarnessConfig applied = AutoHarnessCliSupport.applyRequest(cfg, request);

        assertSame(cfg, applied);
        assertEquals(120.0, cfg.getSessionBudgetSecs());
        assertTrue(cfg.getTaskTimeoutSecs() <= 114.0);
        assertEquals("", cfg.getGitRemote());
        assertEquals("stabilize tests", cfg.getOptimizationGoal());
        assertEquals("competitor-x", cfg.getCompetitor());
    }

    @Test
    @Disabled("Requires real LLM API and GITCODE_ACCESS_TOKEN")
    void optimizeAddTypeAnnotations() {
        assertTrue(true);
    }

    @Test
    @Disabled("Requires real LLM API and GITCODE_ACCESS_TOKEN")
    void optimizeImproveDocstrings() {
        assertTrue(true);
    }

    @Test
    @Disabled("Requires real LLM API and GITCODE_ACCESS_TOKEN")
    void optimizeFixLintIssues() {
        assertTrue(true);
    }

    @Test
    @Disabled("Requires real LLM API and GITCODE_ACCESS_TOKEN")
    void optimizeAddErrorHandling() {
        assertTrue(true);
    }

    private static WorktreeManager.GitCommandRunner fakeGitRunner() {
        return (args, cwd, env) -> {
            if (args.size() >= 2 && "show-ref".equals(args.get(0))) {
                return new WorktreeManager.GitResult(1, "");
            }
            if (args.size() >= 2 && "remote".equals(args.get(0)) && "get-url".equals(args.get(1))) {
                return new WorktreeManager.GitResult(1, "");
            }
            if (args.size() >= 2 && "worktree".equals(args.get(0)) && "add".equals(args.get(1))) {
                mkdir(Path.of(args.get(4)));
            }
            if (args.size() >= 2 && "worktree".equals(args.get(0)) && "remove".equals(args.get(1))) {
                Files.deleteIfExists(Path.of(args.get(3)));
            }
            return new WorktreeManager.GitResult(0, "ok");
        };
    }

    private static void mkdir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
