/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.sys_operation.Cwd;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors focused Python parity checks from
 * {@code tests/unit_tests/harness/tools/worktree/test_manager.py}.
 */
class WorktreeManagerTest {

    private static final String PYTHON_WINDOWS_PATH_FAILURE_REASON = "Disabled with Python baseline failure: "
            + "TestEnter::test_enter_creates_worktree_and_sets_session expected "
            + "'/mock/workspace/.worktrees/my-slug' but Python returned "
            + "'D:\\\\mock\\\\workspace\\\\.worktrees\\\\my-slug' on Windows. See "
            + "javaify-project/tests/python-baseline/pytest-20260605-133148.log lines 12036-12046.";

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);
        Cwd.clear();
    }

    @Test
    @Disabled(PYTHON_WINDOWS_PATH_FAILURE_REASON)
    void enterCreatesWorktreeAndSetsSession() throws Exception {
        Path repo = initRepo("enter");
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        workspace.resolve(".worktrees").resolve("my-slug").toString(),
                        "worktree-my-slug",
                        "abc123",
                        null,
                        true,
                        false
                )
        );
        RecordingHandler handler = new RecordingHandler();
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, handler, null);

        WorktreeSession session = manager.enter("my-slug", "member-1", "team-a").join();

        assertThat(session.getWorktreePath()).endsWith(".worktrees\\my-slug");
        assertThat(session.getWorktreeName()).isEqualTo("my-slug");
        assertThat(session.getMemberName()).isEqualTo("member-1");
        assertThat(session.getTeamName()).isEqualTo("team-a");
        assertThat(WorktreeSessionContext.getCurrentSession()).isSameAs(session);
        assertThat(Path.of(backend.lastRepoRoot).normalize()).isEqualTo(repo.normalize());
        assertThat(Path.of(backend.lastTargetPath).normalize())
                .isEqualTo(workspace.resolve(".worktrees").resolve("my-slug").normalize());
        assertThat(handler.lastEvent).isInstanceOf(WorktreeCreatedEvent.class);
    }

    @Test
    void enterRejectsInvalidSlug() {
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), new RecordingBackend(null), null, null);

        assertThatThrownBy(() -> manager.enter("../escape").join())
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid worktree name");
    }

    @Test
    void enterRaisesOutsideGitRepository() throws Exception {
        Path nonRepo = Files.createDirectories(tempDir.resolve("not-repo"));
        Cwd.initCwd(nonRepo.toString(), nonRepo.toString(), tempDir.resolve("workspace").toString(), null);
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), new RecordingBackend(null), null, null);

        assertThatThrownBy(() -> manager.enter("valid-slug").join())
                .rootCause()
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not in a git repository");
    }

    @Test
    void enterPublishesEvent() throws Exception {
        Path repo = initRepo("event");
        Path workspace = Files.createDirectories(tempDir.resolve("event-workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        workspace.resolve(".worktrees").resolve("ev-slug").toString(),
                        "worktree-ev-slug",
                        "abc123",
                        null,
                        true,
                        false
                )
        );
        RecordingHandler handler = new RecordingHandler();
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, handler, null);

        manager.enter("ev-slug", "m1", "t1").join();

        assertThat(handler.lastEvent).isInstanceOf(WorktreeCreatedEvent.class);
    }

    @Test
    void exitKeepClearsSessionWithoutRemoval() throws Exception {
        Path repo = initRepo("keep");
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        workspace.resolve(".worktrees").resolve("keep-slug").toString(),
                        "worktree-keep-slug",
                        "abc123",
                        null,
                        true,
                        false
                )
        );
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, null, null);
        manager.enter("keep-slug", null, null).join();

        var result = manager.exit("keep", false).join();

        assertThat(result).containsEntry("action", "keep");
        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
        assertThat(backend.removeCalled).isFalse();
    }

    @Test
    void exitRemoveRemovesWorktreeAndClearsSession() throws Exception {
        Path repo = initRepo("remove");
        Path workspace = Files.createDirectories(tempDir.resolve("remove-workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        repo.toString(),
                        "worktree-rm-slug",
                        Git.revParse("HEAD", repo.toString()).join(),
                        null,
                        true,
                        false
                )
        );
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, null, null);
        manager.enter("rm-slug", null, null).join();

        var result = manager.exit("remove", false).join();

        assertThat(result).containsEntry("action", "remove");
        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
        assertThat(backend.removeCalled).isTrue();
    }

    @Test
    void exitRemoveWithChangesRaises() throws Exception {
        Path repo = initRepo("dirty");
        Path workspace = Files.createDirectories(tempDir.resolve("dirty-workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        repo.toString(),
                        "worktree-dirty-slug",
                        Git.revParse("HEAD", repo.toString()).join(),
                        null,
                        true,
                        false
                )
        );
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, null, null);
        manager.enter("dirty-slug", null, null).join();
        Files.writeString(repo.resolve("dirty.txt"), "changed\n");

        assertThatThrownBy(() -> manager.exit("remove", false).join())
                .hasCauseInstanceOf(ValidationError.class)
                .rootCause()
                .hasMessageContaining("uncommitted files")
                .hasMessageContaining("discard_changes=True");
        assertThat(backend.removeCalled).isFalse();
    }

    @Test
    void exitRemoveFailsClosedWhenStateCannotBeVerified() throws Exception {
        Path repo = initRepo("unknown");
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        workspace.resolve(".worktrees").resolve("unknown-slug").toString(),
                        "worktree-unknown-slug",
                        null,
                        null,
                        true,
                        false
                )
        );
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, null, null);
        manager.enter("unknown-slug", null, null).join();

        assertThatThrownBy(() -> manager.exit("remove", false).join())
                .hasCauseInstanceOf(ValidationError.class)
                .rootCause()
                .hasMessageContaining("Could not verify worktree state");
        assertThat(backend.removeCalled).isFalse();
    }

    @Test
    void exitRemoveWithChangesDiscardProceeds() throws Exception {
        Path repo = initRepo("discard");
        Path workspace = Files.createDirectories(tempDir.resolve("discard-workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        repo.toString(),
                        "worktree-discard-slug",
                        Git.revParse("HEAD", repo.toString()).join(),
                        null,
                        true,
                        false
                )
        );
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, null, null);
        manager.enter("discard-slug", null, null).join();
        Files.writeString(repo.resolve("dirty.txt"), "changed\n");

        var result = manager.exit("remove", true).join();

        assertThat(result).containsEntry("action", "remove");
        assertThat(backend.removeCalled).isTrue();
    }

    @Test
    void createOwnerWorktreeDoesNotModifySessionState() throws Exception {
        Path repo = initRepo("owner");
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        Path worktreePath = workspace.resolve(".worktrees").resolve("agent-slug");
        Files.createDirectories(worktreePath);
        RecordingBackend backend = new RecordingBackend(
                new WorktreeCreateResult(
                        worktreePath.toString(),
                        "worktree-agent-slug",
                        "abc123",
                        null,
                        true,
                        false
                )
        );
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, null, null);

        WorktreeCreateResult result = manager.createOwnerWorktree("agent-slug").join();

        assertThat(result.getWorktreePath()).isEqualTo(worktreePath.toString());
        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
    }

    @Test
    void createOwnerWorktreeRejectsInvalidSlug() {
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), new RecordingBackend(null), null, null);

        assertThatThrownBy(() -> manager.createOwnerWorktree("../../bad").join())
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid worktree name");
    }

    @Test
    void ownerSlugFormatsLongOwnerId() {
        assertThat(WorktreeManager.ownerSlug("abcdef123456")).isEqualTo("teammate-abcdef12");
    }

    @Test
    void ownerSlugPreservesShortOwnerId() {
        assertThat(WorktreeManager.ownerSlug("abc")).isEqualTo("teammate-abc");
    }

    @Test
    void resolvePolicyAutoResolvesToEphemeral() {
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), new RecordingBackend(null), null, List.of());

        assertThat(manager.resolvePolicy()).isEqualTo(WorktreeLifecyclePolicy.EPHEMERAL);
    }

    @Test
    void resolvePolicyRespectsExplicitDurable() {
        WorktreeConfig config = new WorktreeConfig();
        config.setLifecyclePolicy(WorktreeLifecyclePolicy.DURABLE);
        WorktreeManager manager = new WorktreeManager(config, new RecordingBackend(null), null, List.of());

        assertThat(manager.resolvePolicy()).isEqualTo(WorktreeLifecyclePolicy.DURABLE);
    }

    @Test
    void fireRailReturnsLastNonNullValue() {
        WorktreeManager manager = new WorktreeManager(
                new WorktreeConfig(),
                new RecordingBackend(null),
                null,
                List.of(new RailA(), new RailB())
        );

        Object result = manager.fireRail("onEnter", "arg1").join();

        assertThat(result).isEqualTo("b");
    }

    @Test
    void fireRailSkipsRailsWithoutTargetMethod() {
        WorktreeManager manager = new WorktreeManager(
                new WorktreeConfig(),
                new RecordingBackend(null),
                null,
                List.of(new Object())
        );

        Object result = manager.fireRail("onEnter").join();

        assertThat(result).isNull();
    }

    private Path initRepo(String name) throws IOException, InterruptedException {
        Path repo = Files.createDirectories(tempDir.resolve(name));
        runGit(repo, "init", "--quiet");
        runGit(repo, "symbolic-ref", "HEAD", "refs/heads/main");
        runGit(repo, "config", "user.email", "codex@example.com");
        runGit(repo, "config", "user.name", "Codex");
        Files.writeString(repo.resolve("README.md"), "# test\n");
        runGit(repo, "add", "README.md");
        runGit(repo, "commit", "--quiet", "-m", "init");
        runGit(repo, "update-ref", "refs/remotes/origin/main", "HEAD");
        return repo;
    }

    private static void runGit(Path cwd, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(new String(process.getErrorStream().readAllBytes()));
        }
    }

    private static final class RecordingBackend implements WorktreeBackend {
        private final WorktreeCreateResult result;
        private String lastRepoRoot;
        private String lastTargetPath;
        private boolean removeCalled;

        private RecordingBackend(WorktreeCreateResult result) {
            this.result = result;
        }

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath) {
            lastRepoRoot = repoRoot;
            lastTargetPath = targetPath;
            return CompletableFuture.completedFuture(result == null ? new WorktreeCreateResult(targetPath) : result);
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
            removeCalled = true;
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            return CompletableFuture.completedFuture(true);
        }
    }

    private static final class RecordingHandler implements WorktreeEventHandler {
        private WorktreeEvent lastEvent;

        @Override
        public CompletableFuture<Void> handle(WorktreeEvent event) {
            lastEvent = event;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RailA {
        public CompletableFuture<String> onEnter(String arg) {
            return CompletableFuture.completedFuture("a");
        }
    }

    private static final class RailB {
        public CompletableFuture<String> onEnter(String arg) {
            return CompletableFuture.completedFuture("b");
        }
    }
}
