/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.cli.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.harness.cli.ui.CliRepl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's e2e auto-harness CLI tests in
 * {@code tests/cli/e2e/test_auto_harness.py}.</p>
 */
class AutoHarnessE2eTest {

    @TempDir
    Path tempDir;

    @Test
    void loadFullConfigMapsYamlFields() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
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
                """);

        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(config.toString());

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
        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(tempDir.resolve("nonexistent.yaml").toString());

        assertEquals("", cfg.getGitRemote());
        assertEquals("", cfg.getForkOwner());
        assertEquals(900000.0, cfg.getSessionBudgetSecs());
        assertEquals("develop", cfg.getGitBaseBranch());
    }

    @Test
    void partialConfigMergesDefaults() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                git:
                  remote: partial-fork
                """);

        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(config.toString());

        assertEquals("partial-fork", cfg.getGitRemote());
        assertEquals("develop", cfg.getGitBaseBranch());
        assertEquals(900000.0, cfg.getSessionBudgetSecs());
    }

    @Test
    void worktreeLifecycleWithLocalRepo() throws Exception {
        Path localRepo = Files.createDirectories(tempDir.resolve("local_repo"));
        Path dataDir = tempDir.resolve("data");
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());
        cfg.setLocalRepo(localRepo.toString());
        cfg.setGitBaseBranch("develop");
        cfg.setGitUserName("test");
        cfg.setGitUserEmail("test@e2e.local");
        RecordingGitExecutor executor = new RecordingGitExecutor();
        WorktreeManager manager = new WorktreeManager(cfg, executor);

        String worktreePath = manager.prepare("optimize-retrieval");

        assertTrue(worktreePath.contains(dataDir.resolve("worktrees").toString()));
        assertTrue(Files.exists(Path.of(worktreePath)));
        manager.cleanup(worktreePath);
        assertFalse(Files.exists(Path.of(worktreePath)));
    }

    @Test
    void worktreeAutoCloneWithoutLocalRepo() throws Exception {
        Path dataDir = tempDir.resolve("data");
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());
        cfg.setLocalRepo("");
        cfg.setGitBaseBranch("develop");
        RecordingGitExecutor executor = new RecordingGitExecutor();
        WorktreeManager manager = new WorktreeManager(cfg, executor);

        String worktreePath = manager.prepare("test-clone");

        assertTrue(executor.cloneCalled);
        assertTrue(worktreePath.contains(dataDir.resolve("worktrees").toString()));
    }

    @Test
    void budgetOverrideMatchesCliSemantics() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                budget:
                  session_secs: 3600
                """);
        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(config.toString());

        cfg.setSessionBudgetSecs(600);
        cfg.setTaskTimeoutSecs(Math.min(cfg.getTaskTimeoutSecs(), 600 * 0.95));

        assertEquals(600.0, cfg.getSessionBudgetSecs());
        assertTrue(cfg.getTaskTimeoutSecs() <= 570.0);
    }

    @Test
    void noPushOverrideClearsGitRemote() throws Exception {
        Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
                git:
                  remote: myfork
                """);
        AutoHarnessConfig cfg = AutoHarnessSchema.loadAutoHarnessConfig(config.toString());

        cfg.setGitRemote("");

        assertEquals("", cfg.getGitRemote());
    }

    @Test
    void pathsStayUnderDataDir() {
        Path dataDir = tempDir.resolve("auto_harness");
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());

        assertTrue(cfg.getResolvedExperienceDir().startsWith(dataDir.toString()));
        assertTrue(cfg.getRunsDir().startsWith(dataDir.toString()));
        assertTrue(cfg.getWorktreesDir().startsWith(dataDir.toString()));
        assertTrue(cfg.getCacheRepoDir().startsWith(dataDir.toString()));
        assertFalse(cfg.getResolvedExperienceDir().contains("worktrees"));
        assertFalse(cfg.getRunsDir().contains("worktrees"));
    }

    @Test
    void worktreeDoesNotReceiveExperienceOrRunsArtifacts() throws Exception {
        Path dataDir = tempDir.resolve("data");
        Path localRepo = Files.createDirectories(tempDir.resolve("repo"));
        AutoHarnessConfig cfg = new AutoHarnessConfig();
        cfg.setDataDir(dataDir.toString());
        cfg.setLocalRepo(localRepo.toString());
        RecordingGitExecutor executor = new RecordingGitExecutor();
        WorktreeManager manager = new WorktreeManager(cfg, executor);

        String worktreePath = manager.prepare("test-iso");

        assertFalse(Files.exists(Path.of(worktreePath).resolve("experience")));
        assertFalse(Files.exists(Path.of(worktreePath).resolve("runs")));
        assertEquals(dataDir.resolve("experience").toString(), cfg.getResolvedExperienceDir());
        assertEquals(dataDir.resolve("runs").toString(), cfg.getRunsDir());
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.cli.e2e.test_auto_harness.TestSubcmdRunIntegration::test_run_with_task: "
                    + "UnicodeEncodeError under GBK console encoding. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void runWithTaskStreamsOrchestrator() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.cli.e2e.test_auto_harness.TestSubcmdRunIntegration::test_run_without_task: "
                    + "UnicodeEncodeError under GBK console encoding. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void runWithoutTaskUsesAssessPlanFlow() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Test
    void dryRunSkipsExecution() {
        CliRepl.PreparedRun prepared = new CliRepl().subcmdRun(
                List.of("--task", "test-task", "--dry-run"),
                tempDir.toString(),
                tempDir
        );

        assertTrue(prepared.dryRun());
        assertEquals("test-task", prepared.task());
        assertNotNull(prepared.tasks());
        assertEquals(1, prepared.tasks().size());
        assertEquals("test-task", prepared.tasks().get(0).getTopic());
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.cli.e2e.test_auto_harness.TestSubcmdRunIntegration::"
                    + "test_budget_and_no_push_override: UnicodeEncodeError under GBK console encoding. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void budgetAndNoPushOverrideInSubcmdRun() {
        // Python source test failed in baseline; keep disabled until the Python baseline changes.
    }

    @Disabled("Skipped in Python source: 需要真实 LLM API 和 GITCODE_ACCESS_TOKEN")
    @Test
    void optimizeAddTypeAnnotationsRequiresRealServices() {
        // Python source test is skipped.
    }

    @Disabled("Skipped in Python source: 需要真实 LLM API 和 GITCODE_ACCESS_TOKEN")
    @Test
    void optimizeImproveDocstringsRequiresRealServices() {
        // Python source test is skipped.
    }

    @Disabled("Skipped in Python source: 需要真实 LLM API 和 GITCODE_ACCESS_TOKEN")
    @Test
    void optimizeFixLintIssuesRequiresRealServices() {
        // Python source test is skipped.
    }

    @Disabled("Skipped in Python source: 需要真实 LLM API 和 GITCODE_ACCESS_TOKEN")
    @Test
    void optimizeAddErrorHandlingRequiresRealServices() {
        // Python source test is skipped.
    }

    private static final class RecordingGitExecutor implements WorktreeManager.GitCommandExecutor {
        private final List<List<String>> commands = new ArrayList<>();
        private boolean cloneCalled;

        @Override
        public WorktreeManager.GitCommandResult execute(List<String> args, String cwd, Map<String, String> env)
                throws IOException {
            commands.add(List.copyOf(args));
            if (args.size() >= 2 && "clone".equals(args.get(0))) {
                cloneCalled = true;
                Files.createDirectories(Path.of(args.get(args.size() - 1)));
            }
            if (args.size() >= 2 && "worktree".equals(args.get(0)) && "add".equals(args.get(1))) {
                Files.createDirectories(Path.of(args.get(4)));
            }
            if (args.size() >= 2 && "worktree".equals(args.get(0)) && "remove".equals(args.get(1))) {
                deleteIfEmpty(Path.of(args.get(args.size() - 1)));
            }
            if (args.size() >= 2 && "remote".equals(args.get(0)) && "get-url".equals(args.get(1))) {
                return new WorktreeManager.GitCommandResult(1, "");
            }
            return new WorktreeManager.GitCommandResult(0, "ok");
        }

        private static void deleteIfEmpty(Path path) throws IOException {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        }
    }
}
