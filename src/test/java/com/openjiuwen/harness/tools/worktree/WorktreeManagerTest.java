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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors focused Python parity checks from
 * {@code tests/unit_tests/harness/tools/worktree/test_manager.py}.
 */
class WorktreeManagerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);
        Cwd.clear();
    }

    @Test
    void enterSetsSessionAndPublishesEvent() throws Exception {
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
    void ownerSlugAndResolvePolicyMirrorPythonRules() {
        WorktreeConfig config = new WorktreeConfig();
        config.setLifecyclePolicy(WorktreeLifecyclePolicy.DURABLE);
        WorktreeManager manager = new WorktreeManager(config, new RecordingBackend(null), null, List.of());

        assertThat(WorktreeManager.ownerSlug("abcdef123456")).isEqualTo("teammate-abcdef12");
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
