/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the auto-harness worktree manager.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/auto_harness/infra/test_workspace.py}
 * for {@code openjiuwen/auto_harness/infra/worktree_manager.py}.
 */
class WorktreeManagerTest {

    @TempDir
    private Path tempDir;

    @Test
    void slugifyConvertsSpacesToHyphens() {
        assertThat(WorktreeManager.slugify("fix timeout bug")).isEqualTo("fix-timeout-bug");
    }

    @Test
    void slugifyRemovesSpecialCharacters() {
        assertThat(WorktreeManager.slugify("add: feature/new!")).doesNotContain("/", ":", "!");
    }

    @Test
    void slugifyKeepsChineseCharacters() {
        assertThat(WorktreeManager.slugify("修复超时问题")).isNotEmpty();
    }

    @Test
    void slugifyTruncatesToFortyCharacters() {
        assertThat(WorktreeManager.slugify("a".repeat(100))).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void slugifyEmptyFallsBackToTask() {
        assertThat(WorktreeManager.slugify("")).isEqualTo("task");
    }

    @Test
    void slugifyOnlySpecialFallsBackToTask() {
        assertThat(WorktreeManager.slugify("!!!")).isEqualTo("task");
    }

    @Test
    void prepareWithLocalRepoFetchesAddsWorktreeAndConfiguresRemote() throws Exception {
        Path local = Files.createDirectories(tempDir.resolve("local_repo"));
        RecordingExecutor executor = new RecordingExecutor();
        WorktreeManager manager = new WorktreeManager(makeConfig(local.toString()), executor);

        String worktreePath = manager.prepare("fix timeout");

        assertThat(worktreePath).contains("worktrees");
        assertThat(executor.commands).contains(
                List.of("fetch", "origin"),
                List.of("worktree", "prune")
        );
        assertThat(executor.indexOfPrefix(List.of("worktree", "add", "-b", "auto-harness/fix-timeout")))
                .isGreaterThanOrEqualTo(0);
        assertThat(executor.commands).anySatisfy(args -> assertThat(args).contains("config", "user.name", "test-user"));
        assertThat(executor.commands).anySatisfy(args -> assertThat(args).contains("remote", "add", "myfork"));
    }

    @Test
    void prepareDeletesExistingBranchBeforeAdd() throws Exception {
        Path local = Files.createDirectories(tempDir.resolve("local_repo"));
        String branchRef = "refs/heads/auto-harness/fix-timeout";
        RecordingExecutor executor = new RecordingExecutor();
        executor.enqueue("show-ref", 0, "");
        executor.enqueue("worktree list", 0, "worktree " + local + "\nHEAD deadbeef\nbranch refs/heads/develop\n");
        WorktreeManager manager = new WorktreeManager(makeConfig(local.toString()), executor);

        manager.prepare("fix timeout");

        int pruneIdx = executor.indexOfPrefix(List.of("worktree", "prune"));
        int showRefIdx = executor.indexOfPrefix(List.of("show-ref", "--verify", "--quiet", branchRef));
        int deleteIdx = executor.indexOfPrefix(List.of("branch", "-D", "auto-harness/fix-timeout"));
        int addIdx = executor.indexOfPrefix(List.of("worktree", "add"));
        assertThat(pruneIdx).isLessThan(showRefIdx);
        assertThat(showRefIdx).isLessThan(deleteIdx);
        assertThat(deleteIdx).isLessThan(addIdx);
    }

    @Test
    void prepareRemovesManagedStaleWorktreeBeforeDeletingBranch() throws Exception {
        Path local = Files.createDirectories(tempDir.resolve("local_repo"));
        AutoHarnessConfig config = makeConfig(local.toString());
        Path stale = Path.of(config.getWorktreesDir()).resolve("old-fix-timeout");
        String branchRef = "refs/heads/auto-harness/fix-timeout";
        RecordingExecutor executor = new RecordingExecutor();
        executor.enqueue("show-ref", 0, "");
        executor.enqueue("worktree list", 0, "worktree " + stale + "\nHEAD deadbeef\nbranch " + branchRef + "\n");
        WorktreeManager manager = new WorktreeManager(config, executor);

        manager.prepare("fix timeout");

        int removeIdx = executor.indexOfPrefix(List.of("worktree", "remove", "--force", stale.toString()));
        int deleteIdx = executor.indexOfPrefix(List.of("branch", "-D", "auto-harness/fix-timeout"));
        int addIdx = executor.indexOfPrefix(List.of("worktree", "add"));
        assertThat(removeIdx).isLessThan(deleteIdx);
        assertThat(deleteIdx).isLessThan(addIdx);
    }

    @Test
    void prepareRejectsUnmanagedStaleWorktreeForExistingBranch() throws Exception {
        Path local = Files.createDirectories(tempDir.resolve("local_repo"));
        Path unmanaged = tempDir.resolve("foreign").resolve("fix-timeout");
        String branchRef = "refs/heads/auto-harness/fix-timeout";
        RecordingExecutor executor = new RecordingExecutor();
        executor.enqueue("show-ref", 0, "");
        executor.enqueue("worktree list", 0, "worktree " + unmanaged + "\nHEAD deadbeef\nbranch " + branchRef + "\n");
        WorktreeManager manager = new WorktreeManager(makeConfig(local.toString()), executor);

        assertThatThrownBy(() -> manager.prepare("fix timeout"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unmanaged worktree");
        assertThat(executor.indexOfPrefix(List.of("branch", "-D", "auto-harness/fix-timeout"))).isEqualTo(-1);
        assertThat(executor.indexOfPrefix(List.of("worktree", "add"))).isEqualTo(-1);
    }

    @Test
    void prepareWithoutLocalRepoClonesBeforeAddingWorktree() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        WorktreeManager manager = new WorktreeManager(makeConfig(""), executor);

        manager.prepare("add feature");

        int cloneIdx = executor.indexOfPrefix(List.of("clone", "-b", "develop"));
        int addIdx = executor.indexOfPrefix(List.of("worktree", "add", "-b", "auto-harness/add-feature"));
        assertThat(cloneIdx).isGreaterThanOrEqualTo(0);
        assertThat(addIdx).isGreaterThan(cloneIdx);
    }

    @Test
    void cleanupRemovesExistingWorktree() throws Exception {
        Path worktree = Files.createDirectories(tempDir.resolve("data").resolve("worktrees").resolve("wt1"));
        RecordingExecutor executor = new RecordingExecutor();
        WorktreeManager manager = new WorktreeManager(makeConfig(""), executor);

        manager.cleanup(worktree.toString());

        assertThat(executor.commands).anySatisfy(args -> assertThat(args)
                .containsExactly("worktree", "remove", "--force", worktree.toString()));
    }

    @Test
    void cleanupSkipsMissingPath() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        WorktreeManager manager = new WorktreeManager(makeConfig(""), executor);

        manager.cleanup(tempDir.resolve("missing").toString());

        assertThat(executor.commands).isEmpty();
    }

    @Test
    void prepareAddsForkRemoteWhenMissing() throws Exception {
        Path local = Files.createDirectories(tempDir.resolve("local_repo"));
        RecordingExecutor executor = new RecordingExecutor();
        WorktreeManager manager = new WorktreeManager(makeConfig(local.toString()), executor);

        manager.prepare("test remote");

        assertThat(executor.commands).anySatisfy(args -> assertThat(args).containsExactly(
                "remote",
                "add",
                "myfork",
                "https://gitcode.com/TestOwner/agent-core.git"
        ));
    }

    @Test
    void prepareUsesTaskScopedGitAuthEnv() throws Exception {
        Path local = Files.createDirectories(tempDir.resolve("local_repo"));
        AutoHarnessConfig config = makeConfig(local.toString());
        config.setGitcodeUsername("bot-user");
        config.setGitcodeToken("secret-token");
        RecordingExecutor executor = new RecordingExecutor();
        WorktreeManager manager = new WorktreeManager(config, executor);

        manager.prepare("auth test");

        assertThat(executor.environments).isNotEmpty();
        assertThat(executor.environments).allSatisfy(env -> {
            assertThat(env).containsEntry("GIT_TERMINAL_PROMPT", "0");
            assertThat(env).containsEntry("GIT_CONFIG_KEY_2", "http.https://gitcode.com/.extraheader");
        });
    }

    private AutoHarnessConfig makeConfig(String localRepo) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setLocalRepo(localRepo);
        config.setGitBaseBranch("develop");
        config.setGitUserName("test-user");
        config.setGitUserEmail("test@example.com");
        config.setGitRemote("myfork");
        config.setForkOwner("TestOwner");
        config.setUpstreamRepo("agent-core");
        return config;
    }

    /**
     * Mirrors Python's patched {@code _run_git} fake in
     * {@code tests/unit_tests/auto_harness/infra/test_workspace.py}.
     */
    private static final class RecordingExecutor implements WorktreeManager.GitCommandExecutor {
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Map<String, String>> environments = new ArrayList<>();
        private final Deque<QueuedResult> queued = new ArrayDeque<>();

        void enqueue(String prefix, int code, String output) {
            queued.addLast(new QueuedResult(prefix, code, output));
        }

        int indexOfPrefix(List<String> prefix) {
            for (int i = 0; i < commands.size(); i++) {
                List<String> command = commands.get(i);
                if (command.size() >= prefix.size() && command.subList(0, prefix.size()).equals(prefix)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public WorktreeManager.GitCommandResult execute(List<String> args, String cwd, Map<String, String> env)
                throws IOException {
            commands.add(List.copyOf(args));
            environments.add(Map.copyOf(env));
            if (args.size() >= 2 && "worktree".equals(args.get(0)) && "add".equals(args.get(1))) {
                Files.createDirectories(Path.of(args.get(4)));
            }
            if (args.size() >= 2 && "remote".equals(args.get(0)) && "get-url".equals(args.get(1))) {
                return new WorktreeManager.GitCommandResult(1, "not found");
            }
            for (QueuedResult result : queued) {
                if (String.join(" ", args).startsWith(result.prefix())) {
                    queued.remove(result);
                    return new WorktreeManager.GitCommandResult(result.code(), result.output());
                }
            }
            return new WorktreeManager.GitCommandResult(0, "ok");
        }
    }

    /**
     * Mirrors Python's queued mock git results for
     * {@code tests/unit_tests/auto_harness/infra/test_workspace.py}.
     */
    private record QueuedResult(String prefix, int code, String output) {
    }
}
