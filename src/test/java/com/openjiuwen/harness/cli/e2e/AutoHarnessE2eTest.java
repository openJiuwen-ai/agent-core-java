/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.harness.cli.AutoHarnessCliSupport;
import com.openjiuwen.harness.cli.AutoHarnessRunRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
