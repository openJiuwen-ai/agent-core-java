/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_workspace}.
 */
@DisplayName("Workspace Tests")
class TestWorkspace {

    @Nested
    @DisplayName("Slugify Tests")
    class TestSlugify {

        @Test
        void testBasic(@TempDir Path tmpPath) {
            WorktreeManager manager = new WorktreeManager(makeConfig(tmpPath, ""));
            assertEquals("fix-timeout-bug", manager.worktreeNameFor("fix timeout bug"));
        }

        @Test
        void testSpecialChars(@TempDir Path tmpPath) {
            WorktreeManager manager = new WorktreeManager(makeConfig(tmpPath, ""));
            String slug = manager.worktreeNameFor("add: feature/new!");
            assertFalse(slug.contains("/"));
            assertFalse(slug.contains(":"));
            assertFalse(slug.contains("!"));
        }

        @Test
        void testChinese(@TempDir Path tmpPath) {
            WorktreeManager manager = new WorktreeManager(makeConfig(tmpPath, ""));
            String slug = manager.worktreeNameFor("\u4fee\u590d\u8d85\u65f6\u95ee\u9898");
            assertTrue(slug.length() > 0);
        }

        @Test
        void testTruncation(@TempDir Path tmpPath) {
            WorktreeManager manager = new WorktreeManager(makeConfig(tmpPath, ""));
            String longTopic = "a".repeat(100);
            String slug = manager.worktreeNameFor(longTopic);
            assertTrue(slug.length() <= 40);
        }

        @Test
        void testEmpty(@TempDir Path tmpPath) {
            WorktreeManager manager = new WorktreeManager(makeConfig(tmpPath, ""));
            assertEquals("task", manager.worktreeNameFor(""));
        }

        @Test
        void testOnlySpecial(@TempDir Path tmpPath) {
            WorktreeManager manager = new WorktreeManager(makeConfig(tmpPath, ""));
            assertEquals("task", manager.worktreeNameFor("!!!"));
        }
    }

    @Nested
    @DisplayName("WorktreeManager Tests")
    class TestWorktreeManager {

        @Test
        void testPrepareWithLocalRepo(@TempDir Path tmpPath) throws IOException {
            Path local = Files.createDirectory(tmpPath.resolve("local_repo"));
            AutoHarnessConfig cfg = makeConfig(tmpPath, local.toString());
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "remote", "get-url", "myfork")) {
                    return new WorktreeManager.GitResult(1, "not found");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            String result = manager.prepare("fix timeout");

            assertTrue(normalize(result).contains("/worktrees/"));
            List<GitCall> fetchCalls = callsStartingWith(runner.calls, "fetch");
            assertEquals(1, fetchCalls.size());
            assertEquals(local.toAbsolutePath().normalize().toString(), fetchCalls.get(0).cwd());

            List<GitCall> worktreeAdds = callsStartingWith(runner.calls, "worktree", "add");
            assertEquals(1, worktreeAdds.size());
            assertEquals("auto-harness/fix-timeout", worktreeAdds.get(0).args().get(3));

            List<GitCall> configNameCalls = callsStartingWith(runner.calls, "config", "user.name");
            assertEquals(1, configNameCalls.size());
            assertEquals(result, configNameCalls.get(0).cwd());
        }

        @Test
        void testPrepareDeletesExistingBranchBeforeAdd(@TempDir Path tmpPath) throws IOException {
            Path local = Files.createDirectory(tmpPath.resolve("local_repo"));
            AutoHarnessConfig cfg = makeConfig(tmpPath, local.toString());
            String branchRef = "refs/heads/auto-harness/fix-timeout";
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "show-ref", "--verify", "--quiet", branchRef)) {
                    return new WorktreeManager.GitResult(0, "");
                }
                if (argsEquals(args, "worktree", "list", "--porcelain")) {
                    return new WorktreeManager.GitResult(0,
                            "worktree " + local + "\nHEAD deadbeef\nbranch refs/heads/develop\n");
                }
                if (argsEquals(args, "remote", "get-url", "myfork")) {
                    return new WorktreeManager.GitResult(1, "not found");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            manager.prepare("fix timeout");

            int pruneIdx = indexOf(runner.calls, "worktree", "prune");
            int showRefIdx = indexOf(runner.calls, "show-ref", "--verify", "--quiet", branchRef);
            int deleteIdx = indexOf(runner.calls, "branch", "-D", "auto-harness/fix-timeout");
            int addIdx = indexStartingWith(runner.calls, "worktree", "add");
            assertTrue(pruneIdx < showRefIdx);
            assertTrue(showRefIdx < deleteIdx);
            assertTrue(deleteIdx < addIdx);
        }

        @Test
        void testPrepareRemovesManagedWorktreeForExistingBranch(@TempDir Path tmpPath) throws IOException {
            Path local = Files.createDirectory(tmpPath.resolve("local_repo"));
            AutoHarnessConfig cfg = makeConfig(tmpPath, local.toString());
            String branchRef = "refs/heads/auto-harness/fix-timeout";
            Path staleWorktree = Path.of(cfg.getWorktreesDir()).resolve("old-fix-timeout");
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "show-ref", "--verify", "--quiet", branchRef)) {
                    return new WorktreeManager.GitResult(0, "");
                }
                if (argsEquals(args, "worktree", "list", "--porcelain")) {
                    return new WorktreeManager.GitResult(0,
                            "worktree " + staleWorktree + "\nHEAD deadbeef\nbranch " + branchRef + "\n");
                }
                if (argsEquals(args, "remote", "get-url", "myfork")) {
                    return new WorktreeManager.GitResult(1, "not found");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            manager.prepare("fix timeout");

            int removeIdx = indexOf(runner.calls, "worktree", "remove", "--force", staleWorktree.toString());
            int deleteIdx = indexOf(runner.calls, "branch", "-D", "auto-harness/fix-timeout");
            int addIdx = indexStartingWith(runner.calls, "worktree", "add");
            assertTrue(removeIdx < deleteIdx);
            assertTrue(deleteIdx < addIdx);
        }

        @Test
        void testPrepareRejectsUnmanagedWorktreeForExistingBranch(@TempDir Path tmpPath) throws IOException {
            Path local = Files.createDirectory(tmpPath.resolve("local_repo"));
            AutoHarnessConfig cfg = makeConfig(tmpPath, local.toString());
            String branchRef = "refs/heads/auto-harness/fix-timeout";
            Path unmanagedWorktree = tmpPath.resolve("foreign").resolve("fix-timeout");
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "show-ref", "--verify", "--quiet", branchRef)) {
                    return new WorktreeManager.GitResult(0, "");
                }
                if (argsEquals(args, "worktree", "list", "--porcelain")) {
                    return new WorktreeManager.GitResult(0,
                            "worktree " + unmanagedWorktree + "\nHEAD deadbeef\nbranch " + branchRef + "\n");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            RuntimeException error = assertThrows(RuntimeException.class, () -> manager.prepare("fix timeout"));

            assertTrue(error.getMessage().contains("unmanaged worktree"));
            assertEquals(-1, indexOfOrMissing(runner.calls, "branch", "-D", "auto-harness/fix-timeout"));
            assertEquals(-1, indexStartingWithOrMissing(runner.calls, "worktree", "add"));
        }

        @Test
        void testPrepareWithoutLocalRepoClones(@TempDir Path tmpPath) {
            AutoHarnessConfig cfg = makeConfig(tmpPath, "");
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "remote", "get-url", "myfork")) {
                    return new WorktreeManager.GitResult(1, "not found");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            manager.prepare("add feature");

            List<GitCall> cloneCalls = callsStartingWith(runner.calls, "clone");
            assertEquals(1, cloneCalls.size());
        }

        @Test
        void testCleanup(@TempDir Path tmpPath) throws IOException {
            AutoHarnessConfig cfg = makeConfig(tmpPath, "");
            RecordingRunner runner = new RecordingRunner();
            WorktreeManager manager = newManager(cfg, runner);
            Path worktree = Files.createDirectories(tmpPath.resolve("data").resolve("worktrees").resolve("wt1"));

            manager.cleanup(worktree.toString());

            List<GitCall> removeCalls = callsStartingWith(runner.calls, "worktree", "remove");
            assertEquals(1, removeCalls.size());
        }

        @Test
        void testCleanupNonexistentPath(@TempDir Path tmpPath) {
            AutoHarnessConfig cfg = makeConfig(tmpPath, "");
            RecordingRunner runner = new RecordingRunner();
            WorktreeManager manager = newManager(cfg, runner);

            manager.cleanup(tmpPath.resolve("nonexistent").toString());

            assertTrue(runner.calls.isEmpty());
        }

        @Test
        void testPrepareAddsForkRemote(@TempDir Path tmpPath) throws IOException {
            Path local = Files.createDirectory(tmpPath.resolve("local_repo"));
            AutoHarnessConfig cfg = makeConfig(tmpPath, local.toString());
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "remote", "get-url", "myfork")) {
                    return new WorktreeManager.GitResult(1, "not found");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            manager.prepare("test remote");

            List<GitCall> remoteAdds = callsStartingWith(runner.calls, "remote", "add");
            assertEquals(1, remoteAdds.size());
            assertTrue(remoteAdds.get(0).args().contains("myfork"));
        }

        @Test
        void testPrepareUsesTaskScopedGitAuthEnv(@TempDir Path tmpPath) throws IOException {
            Path local = Files.createDirectory(tmpPath.resolve("local_repo"));
            AutoHarnessConfig cfg = makeConfig(tmpPath, local.toString());
            cfg.setForkOwner("ForkOwner");
            cfg.setGitcodeUsername("bot-user");
            cfg.setGitcodeToken("secret-token");
            RecordingRunner runner = new RecordingRunner((args, cwd, env) -> {
                if (argsEquals(args, "remote", "get-url", "myfork")) {
                    return new WorktreeManager.GitResult(1, "not found");
                }
                return new WorktreeManager.GitResult(0, "ok");
            });
            WorktreeManager manager = newManager(cfg, runner);

            manager.prepare("auth test");

            assertFalse(runner.calls.isEmpty());
            assertTrue(runner.calls.stream().allMatch(call -> "0".equals(call.env().get("GIT_TERMINAL_PROMPT"))));
            assertTrue(runner.calls.stream().allMatch(call ->
                    "http.https://gitcode.com/.extraheader".equals(call.env().get("GIT_CONFIG_KEY_2"))));
        }
    }

    private static AutoHarnessConfig makeConfig(Path tmpPath, String localRepo) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tmpPath.resolve("data").toString());
        config.setLocalRepo(localRepo);
        config.setGitBaseBranch("develop");
        config.setGitUserName("test-user");
        config.setGitUserEmail("test@example.com");
        config.setGitRemote("myfork");
        config.setForkOwner("TestOwner");
        config.setUpstreamRepo("agent-core");
        return config;
    }

    private static WorktreeManager newManager(AutoHarnessConfig config, RecordingRunner runner) {
        return new WorktreeManager(config, runner, () -> 1_700_000_000L);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    private static List<GitCall> callsStartingWith(List<GitCall> calls, String... prefix) {
        return calls.stream()
                .filter(call -> argsStartWith(call.args(), prefix))
                .toList();
    }

    private static int indexOf(List<GitCall> calls, String... args) {
        int index = indexOfOrMissing(calls, args);
        assertTrue(index >= 0, "expected git call " + List.of(args));
        return index;
    }

    private static int indexOfOrMissing(List<GitCall> calls, String... args) {
        for (int i = 0; i < calls.size(); i++) {
            if (argsEquals(calls.get(i).args(), args)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexStartingWith(List<GitCall> calls, String... prefix) {
        int index = indexStartingWithOrMissing(calls, prefix);
        assertTrue(index >= 0, "expected git call starting with " + List.of(prefix));
        return index;
    }

    private static int indexStartingWithOrMissing(List<GitCall> calls, String... prefix) {
        for (int i = 0; i < calls.size(); i++) {
            if (argsStartWith(calls.get(i).args(), prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean argsEquals(List<String> args, String... expected) {
        return args.equals(List.of(expected));
    }

    private static boolean argsStartWith(List<String> args, String... prefix) {
        if (args.size() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!args.get(i).equals(prefix[i])) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface GitResponder {
        WorktreeManager.GitResult respond(List<String> args, String cwd, Map<String, String> env);
    }

    private record GitCall(List<String> args, String cwd, Map<String, String> env) {}

    private static final class RecordingRunner implements WorktreeManager.GitCommandRunner {
        private final List<GitCall> calls = new ArrayList<>();
        private final GitResponder responder;

        private RecordingRunner() {
            this(null);
        }

        private RecordingRunner(GitResponder responder) {
            this.responder = responder;
        }

        @Override
        public WorktreeManager.GitResult run(List<String> args, String cwd, Map<String, String> env) {
            calls.add(new GitCall(new ArrayList<>(args), cwd, new LinkedHashMap<>(env)));
            if (responder != null) {
                return responder.respond(args, cwd, env);
            }
            return new WorktreeManager.GitResult(0, "ok");
        }
    }
}
