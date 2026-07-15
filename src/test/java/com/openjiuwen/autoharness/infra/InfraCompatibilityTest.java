
package com.openjiuwen.autoharness.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openjiuwen.autoharness.schema.AutoHarnessConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InfraCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void sessionBudgetShouldTrackTimeAndCost() {
        SessionBudgetController budget = new SessionBudgetController(100, 5, 10);
        budget.start();
        budget.addCost(1.5);

        assertThat(new SessionBudgetController(100, 5, 10).elapsedSecs()).isEqualTo(0.0);
        assertThat(new SessionBudgetController(100, 5, 10).remainingSecs()).isEqualTo(100.0);
        assertThat(budget.remainingCostUsd()).isEqualTo(3.5);
        assertThat(budget.checkTaskBudget(5.0)).isTrue();
        assertThat(budget.shouldStop()).isFalse();
    }

    @Test
    void sessionBudgetShouldMirrorStopAndFloorSemantics() throws Exception {
        SessionBudgetController wallClockBudget = new SessionBudgetController(0.001, 5, 10);
        wallClockBudget.start();
        Thread.sleep(5);

        SessionBudgetController costBudget = new SessionBudgetController(100, 1, 10);
        costBudget.addCost(5.0);

        SessionBudgetController taskBudget = new SessionBudgetController(0.001, 5, 10);
        taskBudget.start();
        SessionBudgetController customTaskBudget = new SessionBudgetController(100, 5, 10);
        customTaskBudget.start();

        assertThat(wallClockBudget.shouldStop()).isTrue();
        assertThat(costBudget.shouldStop()).isTrue();
        assertThat(costBudget.remainingCostUsd()).isEqualTo(0.0);
        assertThat(taskBudget.checkTaskBudget(null)).isFalse();
        assertThat(customTaskBudget.checkTaskBudget(0.0001)).isTrue();
    }

    @Test
    void fixLoopShouldStopWhenCiPassesOrEvaluatorApproves() throws Exception {
        FixLoopController loop = new FixLoopController(2, 2);
        FixLoopResult passInPhase1 =
            loop.run(() -> new FixLoopController.SimpleCheckResult(true, ""), () -> null, null);
        FixLoopResult passInPhase2 = loop.run(() -> new FixLoopController.SimpleCheckResult(false, "err"), () -> null,
                () -> new FixLoopController.SimpleApprovalResult(true));

        assertThat(passInPhase1.isSuccess()).isTrue();
        assertThat(passInPhase2.isSuccess()).isTrue();
        assertThat(passInPhase2.getPhase()).isEqualTo(2);
    }

    @Test
    void fixLoopShouldMirrorPythonDefaultsRetriesAndTimeouts() throws Exception {
        FixLoopResult defaults = FixLoopResult.builder().build();
        assertThat(defaults.isSuccess()).isFalse();
        assertThat(defaults.getAttempts()).isZero();
        assertThat(defaults.getPhase()).isEqualTo(1);
        assertThat(defaults.getErrorLog()).isEmpty();

        int[] calls = {0};
        FixLoopController retryLoop = new FixLoopController(5, 0);
        FixLoopResult passAfterRetries =
            retryLoop.run(() -> new FixLoopController.SimpleCheckResult(++calls[0] >= 3, "lint error"), errors -> {
            }, null);
        assertThat(passAfterRetries.isSuccess()).isTrue();
        assertThat(passAfterRetries.getAttempts()).isEqualTo(3);
        assertThat(passAfterRetries.getErrorLog()).containsExactly("Phase 1 attempt 1: lint error",
                "Phase 1 attempt 2: lint error");

        FixLoopController exhaustedLoop = new FixLoopController(2, 0);
        FixLoopResult exhausted =
            exhaustedLoop.run(() -> new FixLoopController.SimpleCheckResult(false, "fail"), errors -> {
            }, null);
        assertThat(exhausted.isSuccess()).isFalse();
        assertThat(exhausted.getAttempts()).isEqualTo(2);
        assertThat(exhausted.getPhase()).isEqualTo(1);
        assertThat(exhausted.getErrorLog()).containsExactly("Phase 1 attempt 1: fail", "Phase 1 attempt 2: fail");

        FixLoopController timeoutLoop = new FixLoopController(1, 0, 0.01);
        FixLoopResult timeout = timeoutLoop.run(() -> {
            Thread.sleep(100);
            return new FixLoopController.SimpleCheckResult(true, "");
        }, errors -> {
        }, null);
        assertThat(timeout.isSuccess()).isFalse();
        assertThat(timeout.getErrorLog()).containsExactly("Phase 1 attempt 1: CI timeout");
    }

    @Test
    void fixLoopShouldMirrorPythonPhase2ReviewLoop() throws Exception {
        int[] reviewCalls = {0};
        FixLoopController loop = new FixLoopController(1, 3);
        FixLoopResult approved = loop.run(() -> new FixLoopController.SimpleCheckResult(false, "err"), errors -> {
        }, () -> new FixLoopController.SimpleApprovalResult(++reviewCalls[0] >= 2));

        assertThat(approved.isSuccess()).isTrue();
        assertThat(approved.getPhase()).isEqualTo(2);
        assertThat(approved.getAttempts()).isEqualTo(3);
        assertThat(approved.getErrorLog()).containsExactly("Phase 1 attempt 1: err",
                "Phase 2 attempt 1: evaluator rejected");

        FixLoopResult noEvaluator =
            new FixLoopController(1, 3).run(() -> new FixLoopController.SimpleCheckResult(false, "err"), errors -> {
            }, null);
        assertThat(noEvaluator.isSuccess()).isFalse();
        assertThat(noEvaluator.getPhase()).isEqualTo(1);
    }

    @Test
    void ciGateAndGitWorktreeHelpersShouldExposeExpectedPlans() throws Exception {
        Path configPath = tempDir.resolve("ci_gate.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: lint
                    command: \"printf lint-ok\"
                    required: true
                  - name: test
                    command: \"printf test-ok\"
                    required: false
                """);

        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), "", "printf install-ok");
        CIGateResult result = runner.run("all");
        GitOperations git = new GitOperations(tempDir.toString(), "origin", "main");
        WorktreeManager worktree = new WorktreeManager(tempDir.toString());

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getExecutedCommands()).containsExactly("printf install-ok", "printf lint-ok",
                "printf test-ok");
        assertThat(result.getGateOutputs()).containsExactly("[lint]\nlint-ok", "[test]\ntest-ok");
        assertThat(result.getGates()).extracting("name", "passed", "output")
                .containsExactly(tuple("lint", true, "lint-ok"), tuple("test", true, "test-ok"));
        assertThat(git.describeSyncPlan()).contains("remote=origin");
        assertThat(worktree.worktreePath("feature-a").toString()).contains("feature-a");
    }

    @Test
    void gitOperationsShouldParseStatusAndDiffFromRealRepository() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "bot@example.com");
        run(tempDir, "git", "config", "user.name", "Auto Harness");
        Files.createDirectories(tempDir.resolve("openjiuwen/core"));
        Files.writeString(tempDir.resolve("openjiuwen/core/foo.py"), "print('a')\n");
        Files.writeString(tempDir.resolve("openjiuwen/core/old.py"), "print('old')\n");
        run(tempDir, "git", "add", "openjiuwen/core/foo.py");
        run(tempDir, "git", "add", "openjiuwen/core/old.py");
        run(tempDir, "git", "commit", "-m", "init");
        Files.writeString(tempDir.resolve("openjiuwen/core/foo.py"), "print('b')\n");
        run(tempDir, "git", "mv", "openjiuwen/core/old.py", "openjiuwen/core/new.py");
        Files.createDirectories(tempDir.resolve("tests/unit_tests"));
        Files.writeString(tempDir.resolve("tests/unit_tests/test_foo.py"), "def test_foo(): pass\n");

        GitOperations git = new GitOperations(tempDir.toString(), "origin", "main", "", "openJiuwen", "agent-core",
                "bot-user", "secret-token", "", "");

        Map<String, List<String>> status = git.collectStatus();

        assertThat(status.get("dirty_files")).containsExactly("openjiuwen/core/foo.py", "openjiuwen/core/new.py",
                "tests/unit_tests/test_foo.py");
        assertThat(status.get("tracked_modified_files")).containsExactly("openjiuwen/core/foo.py",
                "openjiuwen/core/new.py");
        assertThat(status.get("untracked_files")).containsExactly("tests/unit_tests/test_foo.py");
        assertThat(status.get("renamed_files")).containsExactly("openjiuwen/core/new.py");
        assertThat(git.statusPorcelain()).contains(" M openjiuwen/core/foo.py");
        assertThat(git.diffNameOnly("HEAD")).containsExactly("openjiuwen/core/foo.py", "openjiuwen/core/new.py");
        assertThat(git.diffStat()).contains("openjiuwen/core/foo.py");
        assertThat(git.currentHead()).isNotBlank();
        assertThat(git.currentBranch()).isNotBlank();
        assertThat(git.showLastCommitStat()).contains("commit");
        assertThat(git.gitEnv()).containsEntry("GIT_TERMINAL_PROMPT", "0");
        assertThat(git.gitEnv()).containsEntry("GIT_CONFIG_KEY_2", GitAuth.GITCODE_EXTRAHEADER_KEY);
    }

    @Test
    void gitOperationsPushShouldUseTaskScopedAuthEnv() throws Exception {
        Path origin = tempDir.resolve("origin-push.git");
        Path repo = tempDir.resolve("push_repo");
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), repo.toString());
        run(repo, "git", "config", "user.email", "bot@example.com");
        run(repo, "git", "config", "user.name", "Auto Harness");
        run(repo, "git", "checkout", "-b", "feature-branch");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        run(repo, "git", "add", "README.md");
        run(repo, "git", "commit", "-m", "init");

        GitOperations git = new GitOperations(repo.toString(), "origin", "develop", "", "openJiuwen", "agent-core",
                "bot-user", "secret-token", "", "");

        Map<String, Object> pushed = git.push("feature-branch");

        assertThat(pushed).containsEntry("success", true);
        assertThat(runCapture(origin, "git", "rev-parse", "feature-branch")).isNotBlank();
        assertThat(git.gitEnv()).containsEntry("GIT_TERMINAL_PROMPT", "0");
        assertThat(git.gitEnv()).containsEntry("GCM_INTERACTIVE", "never");
        assertThat(git.gitEnv()).containsEntry("GIT_CONFIG_KEY_2", GitAuth.GITCODE_EXTRAHEADER_KEY);
    }

    @Test
    void gitOperationsCreatePrShouldFailFastWithoutGitCodeToken() {
        GitOperations git = new GitOperations(tempDir.toString(), "origin", "develop", "fork-owner", "openJiuwen",
                "agent-core", "bot-user", "", "", "");

        Map<String, Object> result =
            git.createPr("test(auto-harness): draft", "/kind task\n\n## Summary\n- no token", "auto-harness/no-token");

        assertThat(result).containsEntry("success", false);
        assertThat(result).containsEntry("error", "missing GitCode token");
    }

    @Test
    void gitCommandShouldPreservePorcelainLeadingSpaceLikePython() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "bot@example.com");
        run(tempDir, "git", "config", "user.name", "Auto Harness");
        Files.writeString(tempDir.resolve("schema.py"), "print('a')\n");
        run(tempDir, "git", "add", "schema.py");
        run(tempDir, "git", "commit", "-m", "init");
        Files.writeString(tempDir.resolve("schema.py"), "print('b')\n");

        GitOperations git = new GitOperations(tempDir.toString(), "origin", "main");

        GitOperations.GitCommandResult result = git.git("status", "--porcelain");

        assertThat(result.code()).isZero();
        assertThat(result.output()).isEqualTo(" M schema.py");
    }

    @Test
    void gitStatusPorcelainShouldReturnRawOutputLikePython() throws Exception {
        run(tempDir, "git", "init");
        run(tempDir, "git", "config", "user.email", "bot@example.com");
        run(tempDir, "git", "config", "user.name", "Auto Harness");
        Files.createDirectories(tempDir.resolve("openjiuwen/auto_harness"));
        Files.createDirectories(tempDir.resolve("tests/unit_tests/auto_harness"));
        Files.writeString(tempDir.resolve("openjiuwen/auto_harness/schema.py"), "print('a')\n");
        run(tempDir, "git", "add", "openjiuwen/auto_harness/schema.py");
        run(tempDir, "git", "commit", "-m", "init");
        Files.writeString(tempDir.resolve("openjiuwen/auto_harness/schema.py"), "print('b')\n");
        Files.writeString(tempDir.resolve("tests/unit_tests/auto_harness/test_schema.py"), "def test_schema(): pass\n");

        GitOperations git = new GitOperations(tempDir.toString(), "origin", "main");

        assertThat(git.statusPorcelain()).isEqualTo("""
                 M openjiuwen/auto_harness/schema.py
                ?? tests/unit_tests/auto_harness/test_schema.py""");
    }

    @Test
    void ciGateShouldMapCheckAliasAndReportMissingAction() throws Exception {
        Path configPath = tempDir.resolve("ci_gate_alias.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: lint
                    command: \"printf alias-ok\"
                    required: true
                  - name: type-check
                    command: \"printf type-ok\"
                    required: true
                """);
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), "", "");

        CIGateResult allResult = runner.run("all");
        CIGateResult checkResult = runner.run("check");
        CIGateResult missingResult = runner.run("format");

        assertThat(runner.getConfigPath()).isEqualTo(configPath.toString());
        assertThat(allResult.isPassed()).isTrue();
        assertThat(allResult.getExecutedCommands()).containsExactly("printf alias-ok", "printf type-ok");
        assertThat(allResult.getGateOutputs()).containsExactly("[lint]\nalias-ok", "[type-check]\ntype-ok");
        assertThat(checkResult.getExecutedCommands()).containsExactly("printf alias-ok");
        assertThat(checkResult.getGateOutputs()).containsExactly("[lint]\nalias-ok");
        assertThat(missingResult.isPassed()).isFalse();
        assertThat(missingResult.getErrors()).isEqualTo("No gate matched action=format");
    }

    @Test
    void ciGateShouldTreatOnlyOmittedActionAsAllLikePythonDefault() throws Exception {
        Path configPath = tempDir.resolve("ci_gate_default_all.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: lint
                    command: \"printf lint-ok\"
                    required: true
                  - name: test
                    command: \"printf test-ok\"
                    required: true
                """);
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), "", "");

        CIGateResult result = runner.run();
        CIGateResult blank = runner.run("");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getExecutedCommands()).containsExactly("printf lint-ok", "printf test-ok");
        assertThat(result.getGates()).extracting("name", "passed").containsExactly(tuple("lint", true),
                tuple("test", true));
        assertThat(blank.isPassed()).isFalse();
        assertThat(blank.getExecutedCommands()).isEmpty();
        assertThat(blank.getErrors()).isEqualTo("No gate matched action=");
    }

    @Test
    void ciGateShouldReturnNoMatchForMissingYamlLikePythonEmptyGates() {
        Path missingConfig = tempDir.resolve("missing-ci-gate.yaml");
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), missingConfig.toString(), "", "");

        CIGateResult result = runner.run("all");

        assertThat(runner.getConfigPath()).isEqualTo(missingConfig.toString());
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getExecutedCommands()).isEmpty();
        assertThat(result.getGates()).isEmpty();
        assertThat(result.getErrors()).isEqualTo("No gate matched action=all");
    }

    @Test
    void ciGateShouldReturnFailedGateOutput() throws Exception {
        Path configPath = tempDir.resolve("ci_gate_failed.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: lint
                    command: \"echo E501 line too long; exit 1\"
                    required: true
                  - name: test
                    command: \"printf test-ok\"
                    required: true
                """);
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), "", "");

        CIGateResult result = runner.run("all");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("[lint]").contains("E501 line too long");
        assertThat(result.getGates()).extracting("name", "passed").containsExactly(tuple("lint", false),
                tuple("test", true));
    }

    @Test
    void ciGateShouldNormalizePythonBackedCommandsLikePythonRunner() throws Exception {
        Path python = tempDir.resolve("python3.11");
        Files.writeString(python, """
                #!/usr/bin/env bash
                printf 'PY:%s\\n' "$*"
                """);
        python.toFile().setExecutable(true);
        Path configPath = tempDir.resolve("ci_gate_python.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: test
                    command: "make test TESTFLAGS=tests/unit_tests/harness/"
                    required: true
                  - name: module
                    command: "python -m pytest -q"
                    required: true
                  - name: prefixed
                    command: "PATH=\\"/tmp/bin:$PATH\\" make test"
                    required: true
                """);
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), python.toString(), "");

        CIGateResult result = runner.run("all");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getExecutedCommands()).containsExactly(python + " -m pytest tests/unit_tests/harness/",
                python + " -m pytest -q", "PATH=\"/tmp/bin:$PATH\" " + python + " -m pytest");
        assertThat(result.getGateOutputs()).contains("[test]\nPY:-m pytest tests/unit_tests/harness/");
    }

    @Test
    void ciGateShouldInjectPythonEnvironmentLikePythonRunner() throws Exception {
        Path venv = tempDir.resolve(".venv");
        Path binDir = venv.resolve("bin");
        Files.createDirectories(binDir);
        Path python = binDir.resolve("python");
        Files.writeString(python, """
                #!/usr/bin/env bash
                exit 0
                """);
        python.toFile().setExecutable(true);
        Path configPath = tempDir.resolve("ci_gate_env.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: env
                    command: |
                      printf 'PY=%s\\nVENV=%s\\nPATH=%s\\nCI=%s\\n' "$AUTO_HARNESS_PYTHON" "$VIRTUAL_ENV" "$PATH" "$CI"
                    required: true
                """);
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), python.toString(), "");

        CIGateResult result = runner.run("all");

        assertThat(result.isPassed()).isTrue();
        String output = result.getGateOutputs().get(0);
        assertThat(output).contains("PY=" + python);
        assertThat(output).contains("VENV=" + venv);
        assertThat(output).contains("CI=1");
        String pathLine = output.lines().filter(line -> line.startsWith("PATH=")).findFirst().orElseThrow();
        assertThat(pathLine).startsWith("PATH=" + binDir);
    }

    @Test
    void ciGateShouldFilterPytestWarningSummaryFromFailedOutput() throws Exception {
        Path failScript = tempDir.resolve("fail.sh");
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
                ========================= 1 failed, 2 warnings in 0.10s ========================
                EOF
                exit 1
                """);
        failScript.toFile().setExecutable(true);
        Path configPath = tempDir.resolve("ci_gate_sanitize.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: test
                    command: "%s"
                    required: true
                """.formatted(failScript));
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), "", "");

        CIGateResult result = runner.run("all");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("AssertionError: expected value");
        assertThat(result.getErrors())
                .contains("FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x");
        assertThat(result.getErrors()).doesNotContain("test session starts");
        assertThat(result.getErrors()).doesNotContain("PydanticDeprecatedSince20");
        assertThat(result.getErrors()).doesNotContain("Generated html report");
    }

    @Test
    void ciGateShouldRunInstallCommandOnlyOnceBeforeGates() throws Exception {
        Path marker = tempDir.resolve("install-count.txt");
        Path installScript = tempDir.resolve("install.sh");
        Files.writeString(installScript, """
                #!/usr/bin/env bash
                count=0
                if [ -f "%s" ]; then
                  count=$(cat "%s")
                fi
                printf '%%s' "$((count + 1))" > "%s"
                """.formatted(marker, marker, marker));
        installScript.toFile().setExecutable(true);
        Path configPath = tempDir.resolve("ci_gate_install.yaml");
        Files.writeString(configPath, """
                ci_gates:
                  - name: test
                    command: "printf test-ok"
                    required: true
                  - name: lint
                    command: "printf lint-ok"
                    required: true
                """);
        CIGateRunner runner = new CIGateRunner(tempDir.toString(), configPath.toString(), "", installScript.toString());

        CIGateResult first = runner.run("test");
        CIGateResult second = runner.run("lint");

        assertThat(first.isPassed()).isTrue();
        assertThat(second.isPassed()).isTrue();
        assertThat(first.getExecutedCommands()).containsExactly(installScript.toString(), "printf test-ok");
        assertThat(second.getExecutedCommands()).containsExactly("printf lint-ok");
        assertThat(Files.readString(marker)).isEqualTo("1");
    }

    @Test
    void worktreeHelpersShouldMirrorPythonWorkspacePlanningSlice() {
        AutoHarnessConfig config = AutoHarnessConfig.builder().workspace(tempDir.resolve("workspace").toString())
                .dataDir(tempDir.resolve("data").toString()).upstreamRepo("agent-core")
                .repoUrl("https://gitcode.com/openJiuwen/agent-core.git").build();
        WorktreeManager manager = new WorktreeManager(config);
        Path managedWorktree = config.worktreesPath().resolve("old-fix-timeout");
        String porcelain = """
                worktree %s
                HEAD deadbeef
                branch refs/heads/auto-harness/fix-timeout

                worktree %s
                HEAD cafe1234
                branch refs/heads/develop

                worktree %s
                HEAD 1234abcd
                branch refs/heads/auto-harness/fix-timeout
                """.formatted(managedWorktree, tempDir.resolve("repo"), tempDir.resolve("foreign/fix-timeout"));

        assertThat(WorktreeManager.slugify("fix timeout bug")).isEqualTo("fix-timeout-bug");
        assertThat(WorktreeManager.slugify("add: feature/new!")).isEqualTo("add-feature-new");
        assertThat(WorktreeManager.slugify("修复超时问题")).isEqualTo("修复超时问题");
        assertThat(WorktreeManager.slugify("")).isEqualTo("task");
        assertThat(WorktreeManager.slugify("!!!")).isEqualTo("task");
        assertThat(WorktreeManager.slugify("a".repeat(100))).hasSizeLessThanOrEqualTo(40);
        assertThat(manager.branchNameForTopic("fix timeout bug")).isEqualTo("auto-harness/fix-timeout-bug");
        assertThat(manager.worktreeNameForTopic(123L, "fix timeout bug")).isEqualTo("123-fix-timeout-bug");
        assertThat(manager.worktreePath("feature-a")).isEqualTo(config.worktreesPath().resolve("feature-a"));
        assertThat(manager.readonlySnapshotPath(456L, "assess"))
                .isEqualTo(config.worktreesPath().resolve("456-assess"));
        assertThat(manager.baseRepoPath()).isEqualTo(config.cacheRepoPath());
        assertThat(manager.isManagedWorktreePath(config.worktreesPath().resolve("123-fix-timeout-bug").toString()))
                .isTrue();
        assertThat(manager.isManagedWorktreePath(tempDir.resolve("foreign/worktree").toString())).isFalse();
        assertThat(WorktreeManager.parseWorktreeListPorcelain(porcelain))
                .extracting(WorktreeManager.WorktreeEntry::path, WorktreeManager.WorktreeEntry::branch)
                .containsExactly(tuple(managedWorktree.toString(), "refs/heads/auto-harness/fix-timeout"),
                        tuple(tempDir.resolve("repo").toString(), "refs/heads/develop"),
                        tuple(tempDir.resolve("foreign/fix-timeout").toString(),
                                "refs/heads/auto-harness/fix-timeout"));
        assertThat(manager.managedEntriesForBranch(porcelain, "auto-harness/fix-timeout"))
                .extracting(WorktreeManager.WorktreeEntry::path).containsExactly(managedWorktree.toString());
        assertThat(manager.hasUnmanagedEntryForBranch(porcelain, "auto-harness/fix-timeout")).isTrue();
        assertThat(manager.hasUnmanagedEntryForBranch(porcelain, "refs/heads/develop")).isTrue();
        assertThat(manager.hasUnmanagedEntryForBranch(porcelain, "auto-harness/unknown")).isFalse();
    }

    @Test
    void configShouldDeriveWorktreeAndCacheRepoPaths() {
        AutoHarnessConfig fromDataDir =
            AutoHarnessConfig.builder().dataDir(tempDir.resolve("data").toString()).upstreamRepo("agent-core").build();
        AutoHarnessConfig fromRepoUrl = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data2").toString())
                .upstreamRepo("").repoUrl("https://gitcode.com/openJiuwen/custom-repo.git").build();
        AutoHarnessConfig withLocalRepo = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data3").toString())
                .localRepo(tempDir.resolve("local-repo").toString()).build();
        WorktreeManager manager = new WorktreeManager(withLocalRepo);

        assertThat(fromDataDir.resolveRepoName()).isEqualTo("agent-core");
        assertThat(fromDataDir.worktreesPath())
                .isEqualTo(tempDir.resolve("data/worktrees").toAbsolutePath().normalize());
        assertThat(fromDataDir.cacheRepoPath())
                .isEqualTo(tempDir.resolve("data/repo/agent-core").toAbsolutePath().normalize());
        assertThat(fromRepoUrl.resolveRepoName()).isEqualTo("custom-repo");
        assertThat(withLocalRepo.cacheRepoPath())
                .isEqualTo(tempDir.resolve("data3/repo/agent-core").toAbsolutePath().normalize());
        assertThat(manager.baseRepoPath()).isEqualTo(tempDir.resolve("local-repo").toAbsolutePath().normalize());
    }

    @Test
    void worktreePrepareAndCleanupShouldUseRealGitWorktree() throws Exception {
        Path origin = tempDir.resolve("origin.git");
        Path local = tempDir.resolve("local_repo");
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), local.toString());
        run(local, "git", "checkout", "-b", "develop");
        run(local, "git", "config", "user.email", "bot@example.com");
        run(local, "git", "config", "user.name", "Auto Harness");
        Files.writeString(local.resolve("README.md"), "hello\n");
        run(local, "git", "add", "README.md");
        run(local, "git", "commit", "-m", "init");
        run(local, "git", "push", "-u", "origin", "develop");

        AutoHarnessConfig config = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data").toString())
                .localRepo(local.toString()).gitBaseBranch("develop").gitUserName("test-user")
                .gitUserEmail("test@example.com").gitcodeUsername("bot-user").gitcodeToken("secret-token").build();
        WorktreeManager manager = new WorktreeManager(config);

        Path wt = manager.prepare("fix timeout");

        assertThat(wt).exists();
        assertThat(wt.getFileName().toString()).contains("fix-timeout");
        assertThat(runCapture(wt, "git", "branch", "--show-current")).isEqualTo("auto-harness/fix-timeout");
        assertThat(runCapture(wt, "git", "config", "user.name")).isEqualTo("test-user");
        assertThat(runCapture(wt, "git", "config", "user.email")).isEqualTo("test@example.com");
        assertThat(manager.gitEnv()).containsEntry("GIT_TERMINAL_PROMPT", "0");
        assertThat(manager.gitEnv()).containsEntry("GIT_CONFIG_KEY_2", GitAuth.GITCODE_EXTRAHEADER_KEY);
        Files.writeString(wt.resolve("STALE_MARKER"), "stale\n");

        Path replacement = manager.prepare("fix timeout");

        assertThat(replacement).exists();
        assertThat(replacement.resolve("STALE_MARKER")).doesNotExist();
        assertThat(runCapture(replacement, "git", "branch", "--show-current")).isEqualTo("auto-harness/fix-timeout");

        manager.cleanup(replacement.toString());

        assertThat(replacement).doesNotExist();
    }

    @Test
    void worktreePrepareShouldAddConfiguredForkRemoteWhenMissing() throws Exception {
        Path origin = tempDir.resolve("origin-fork.git");
        Path local = tempDir.resolve("local_fork");
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), local.toString());
        run(local, "git", "checkout", "-b", "develop");
        run(local, "git", "config", "user.email", "bot@example.com");
        run(local, "git", "config", "user.name", "Auto Harness");
        Files.writeString(local.resolve("README.md"), "hello\n");
        run(local, "git", "add", "README.md");
        run(local, "git", "commit", "-m", "init");
        run(local, "git", "push", "-u", "origin", "develop");

        WorktreeManager manager = new WorktreeManager(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data-fork").toString()).localRepo(local.toString()).gitBaseBranch("develop")
                .gitRemote("myfork").forkOwner("TestOwner").upstreamRepo("agent-core").build());

        Path wt = manager.prepare("test remote");

        assertThat(runCapture(local, "git", "remote", "get-url", "myfork"))
                .isEqualTo("https://gitcode.com/TestOwner/agent-core.git");
        assertThat(runCapture(wt, "git", "remote", "get-url", "myfork"))
                .isEqualTo("https://gitcode.com/TestOwner/agent-core.git");

        manager.cleanup(wt.toString());
    }

    @Test
    void worktreeCleanupShouldIgnoreRemoveFailureLikePythonWarningPath() throws Exception {
        Path repo = tempDir.resolve("cleanup_repo");
        Path notWorktree = tempDir.resolve("not_worktree");
        Files.createDirectories(repo);
        Files.createDirectories(notWorktree);
        run(repo, "git", "init");

        WorktreeManager manager = new WorktreeManager(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data-cleanup-failure").toString()).localRepo(repo.toString()).build());

        manager.cleanup(notWorktree.toString());

        assertThat(notWorktree).exists();
    }

    @Test
    void worktreeCleanupShouldNoopForMissingPathLikePython() throws Exception {
        Path repo = tempDir.resolve("cleanup_missing_repo");
        Files.createDirectories(repo);
        run(repo, "git", "init");

        WorktreeManager manager = new WorktreeManager(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data-cleanup-missing").toString()).localRepo(repo.toString()).build());
        Path missing = tempDir.resolve("missing_worktree");

        manager.cleanup(missing.toString());

        assertThat(missing).doesNotExist();
    }

    @Test
    void worktreeReadonlySnapshotShouldUseDetachedOriginBaseBranch() throws Exception {
        Path origin = tempDir.resolve("origin-readonly.git");
        Path local = tempDir.resolve("local_readonly");
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), local.toString());
        run(local, "git", "checkout", "-b", "develop");
        run(local, "git", "config", "user.email", "bot@example.com");
        run(local, "git", "config", "user.name", "Auto Harness");
        Files.writeString(local.resolve("README.md"), "base\n");
        run(local, "git", "add", "README.md");
        run(local, "git", "commit", "-m", "base");
        run(local, "git", "push", "-u", "origin", "develop");
        String originHead = runCapture(local, "git", "rev-parse", "origin/develop");

        WorktreeManager manager = new WorktreeManager(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data-readonly").toString()).localRepo(local.toString())
                .gitBaseBranch("develop").gitcodeUsername("bot-user").gitcodeToken("secret-token").build());

        Path snapshot = manager.prepareReadonlySnapshot("assess");

        assertThat(snapshot).exists();
        assertThat(snapshot.getFileName().toString()).endsWith("-assess");
        assertThat(runCapture(snapshot, "git", "branch", "--show-current")).isBlank();
        assertThat(runCapture(snapshot, "git", "rev-parse", "HEAD")).isEqualTo(originHead);
        assertThat(Files.readString(snapshot.resolve("README.md"))).isEqualTo("base\n");

        manager.cleanup(snapshot.toString());
        assertThat(snapshot).doesNotExist();
    }

    @Test
    void worktreePrepareShouldRejectUnmanagedExistingBranch() throws Exception {
        Path origin = tempDir.resolve("origin-unmanaged.git");
        Path local = tempDir.resolve("local_unmanaged");
        Path foreign = tempDir.resolve("foreign");
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), local.toString());
        run(local, "git", "checkout", "-b", "develop");
        run(local, "git", "config", "user.email", "bot@example.com");
        run(local, "git", "config", "user.name", "Auto Harness");
        Files.writeString(local.resolve("README.md"), "hello\n");
        run(local, "git", "add", "README.md");
        run(local, "git", "commit", "-m", "init");
        run(local, "git", "push", "-u", "origin", "develop");
        run(local, "git", "worktree", "add", "-b", "auto-harness/fix-timeout", foreign.toString(), "origin/develop");

        WorktreeManager manager =
            new WorktreeManager(AutoHarnessConfig.builder().dataDir(tempDir.resolve("data-unmanaged").toString())
                    .localRepo(local.toString()).gitBaseBranch("develop").build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.prepare("fix timeout"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unmanaged worktree");
    }

    @Test
    void worktreeEnsureBaseRepoShouldCloneAndFetchCacheRepoWithoutLocalRepo() throws Exception {
        Path origin = tempDir.resolve("origin-cache.git");
        Path seed = tempDir.resolve("seed_repo");
        run(tempDir, "git", "init", "--bare", origin.toString());
        run(tempDir, "git", "clone", origin.toString(), seed.toString());
        run(seed, "git", "checkout", "-b", "develop");
        run(seed, "git", "config", "user.email", "bot@example.com");
        run(seed, "git", "config", "user.name", "Auto Harness");
        Files.writeString(seed.resolve("README.md"), "base\n");
        run(seed, "git", "add", "README.md");
        run(seed, "git", "commit", "-m", "base");
        run(seed, "git", "push", "-u", "origin", "develop");

        AutoHarnessConfig config = AutoHarnessConfig.builder().dataDir(tempDir.resolve("data-cache").toString())
                .repoUrl(origin.toString()).gitBaseBranch("develop").build();
        WorktreeManager manager = new WorktreeManager(config);

        Path base = manager.ensureBaseRepo();

        assertThat(base).isEqualTo(config.cacheRepoPath());
        assertThat(base.resolve(".git")).isDirectory();
        assertThat(runCapture(base, "git", "branch", "--show-current")).isEqualTo("develop");
        assertThat(Files.readString(base.resolve("README.md"))).isEqualTo("base\n");

        Files.writeString(seed.resolve("README.md"), "updated\n");
        run(seed, "git", "add", "README.md");
        run(seed, "git", "commit", "-m", "update");
        run(seed, "git", "push", "origin", "develop");

        Path cachedAgain = manager.ensureBaseRepo();

        assertThat(cachedAgain).isEqualTo(base);
        assertThat(runCapture(base, "git", "rev-parse", "origin/develop"))
                .isEqualTo(runCapture(seed, "git", "rev-parse", "HEAD"));
    }

    @Test
    void worktreeEnsureBaseRepoShouldContinueWhenLocalRepoFetchFails() throws Exception {
        Path local = tempDir.resolve("local_fetch_failure");
        Files.createDirectories(local);
        run(local, "git", "init");
        run(local, "git", "remote", "add", "origin", tempDir.resolve("missing-origin.git").toString());

        WorktreeManager manager = new WorktreeManager(AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data-local-fetch-failure").toString()).localRepo(local.toString()).build());

        Path base = manager.ensureBaseRepo();

        assertThat(base).isEqualTo(local.toAbsolutePath().normalize());
    }

    @Test
    void worktreeEnsureBaseRepoShouldContinueWhenCacheRepoFetchFails() throws Exception {
        AutoHarnessConfig config =
            AutoHarnessConfig.builder().dataDir(tempDir.resolve("data-cache-fetch-failure").toString())
                    .repoUrl(tempDir.resolve("unused-origin.git").toString()).build();
        Path cache = config.cacheRepoPath();
        Files.createDirectories(cache);
        run(cache, "git", "init");
        run(cache, "git", "remote", "add", "origin", tempDir.resolve("missing-cache-origin.git").toString());
        WorktreeManager manager = new WorktreeManager(config);

        Path base = manager.ensureBaseRepo();

        assertThat(base).isEqualTo(cache);
    }

    @Test
    void worktreeEnsureBaseRepoShouldFailWhenInitialCloneFails() {
        WorktreeManager manager =
            new WorktreeManager(AutoHarnessConfig.builder().dataDir(tempDir.resolve("data-clone-failure").toString())
                    .repoUrl(tempDir.resolve("missing-clone-origin.git").toString()).gitBaseBranch("develop").build());

        org.assertj.core.api.Assertions.assertThatThrownBy(manager::ensureBaseRepo)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("git clone")
                .hasMessageContaining("failed");
    }

    @Test
    void gitAuthShouldDisablePromptsWithoutCredentials() {
        Map<String, String> env = GitAuth.buildGitAuthEnv(new LinkedHashMap<>(), "", "");

        assertThat(env).containsEntry("GIT_TERMINAL_PROMPT", "0");
        assertThat(env).containsEntry("GCM_INTERACTIVE", "never");
        assertThat(env).doesNotContainKeys("GIT_CONFIG_COUNT", "GIT_CONFIG_KEY_2", "GIT_CONFIG_VALUE_2");
    }

    @Test
    void gitAuthShouldInjectGitcodeHeaderWithCredentials() {
        Map<String, String> baseEnv = new LinkedHashMap<>();
        baseEnv.put("KEEP_ME", "1");

        Map<String, String> env = GitAuth.buildGitAuthEnv(baseEnv, "bot-user", "secret-token");
        String expected = Base64.getEncoder().encodeToString("bot-user:secret-token".getBytes(StandardCharsets.UTF_8));

        assertThat(env).containsEntry("KEEP_ME", "1");
        assertThat(env).containsEntry("GIT_CONFIG_COUNT", "3");
        assertThat(env).containsEntry("GIT_CONFIG_KEY_0", "credential.helper");
        assertThat(env).containsEntry("GIT_CONFIG_VALUE_0", "");
        assertThat(env).containsEntry("GIT_CONFIG_KEY_1", "credential.interactive");
        assertThat(env).containsEntry("GIT_CONFIG_VALUE_1", "never");
        assertThat(env).containsEntry("GIT_CONFIG_KEY_2", GitAuth.GITCODE_EXTRAHEADER_KEY);
        assertThat(env).containsEntry("GIT_CONFIG_VALUE_2", "AUTHORIZATION: basic " + expected);
    }

    @Test
    void commitScopeShouldDeriveAndMatchRelatedTests() {
        List<String> derived = CommitScope.deriveTestFiles(List.of("openjiuwen/auto_harness/schema.py",
                "tests/unit_tests/auto_harness/test_schema.py", "openjiuwen/auto_harness/__init__.py"));

        assertThat(derived).containsExactly("tests/unit_tests/**/test_schema.py",
                "tests/system_tests/**/test_schema.py");
        assertThat(CommitScope.isDerivedTestFile(List.of("openjiuwen/auto_harness/schema.py"),
                "tests/unit_tests/auto_harness/test_schema.py")).isTrue();
        assertThat(CommitScope.isDerivedTestFile(List.of("openjiuwen/auto_harness/schema.py"),
                "tests/unit_tests/auto_harness/test_other.py")).isFalse();
        assertThat(CommitScope.deriveTestFiles(
                List.of("openjiuwen/auto_harness/__init__.py", "tests/unit_tests/auto_harness/test_schema.py")))
                .isEmpty();
    }

    @Test
    void commitScopeShouldExtractVerifyRelatedFilesAndAllowedDocs() {
        CIGateResult result = CIGateResult.builder()
                .errors("FAILED tests/unit_tests/auto_harness/test_schema.py::test_x")
                .gateOutputs(List.of("See tests/system_tests/auto_harness/test_pipeline.py for more details")).build();

        assertThat(CommitScope.extractVerifyRelatedFiles(result,
                "re-run tests/unit_tests/auto_harness/test_schema.py now"))
                .containsExactly("tests/unit_tests/auto_harness/test_schema.py",
                        "tests/system_tests/auto_harness/test_pipeline.py");
        assertThat(CommitScope.deriveLegacyRelatedTestFiles(
                List.of("tests/unit_tests/auto_harness/test_schema.py", "tests/unit_tests/auto_harness/test_other.py"),
                List.of("tests/unit_tests/auto_harness/test_schema.py")))
                .containsExactly("tests/unit_tests/auto_harness/test_schema.py");
        assertThat(CommitScope.isAllowedDocumentationFile("docs/en/guide.md")).isTrue();
        assertThat(CommitScope.isAllowedDocumentationFile("docs/auto-harness-agent-design.md")).isFalse();
        assertThat(CommitScope.isAllowedDocumentationFile("README.md")).isFalse();
    }

    private static void run(Path cwd, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as(String.join(" ", command) + "\n" + output).isZero();
    }

    private static String runCapture(Path cwd, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as(String.join(" ", command) + "\n" + output).isZero();
        return output.strip();
    }
}
