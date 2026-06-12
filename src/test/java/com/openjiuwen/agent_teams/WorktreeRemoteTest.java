/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.tools.worktree.WorktreeBackend;
import com.openjiuwen.harness.tools.worktree.WorktreeConfig;
import com.openjiuwen.harness.tools.worktree.WorktreeCreateResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused parity coverage for the remote worktree request/handler contract.
 *
 * <p>Mirrors Python's behavior in
 * {@code openjiuwen/agent_teams/worktree_remote.py}.</p>
 */
class WorktreeRemoteTest {

    @TempDir
    Path tempDir;

    @Test
    void remoteBackendCreateSerializesRequestAndMapsResponse() throws IOException, InterruptedException {
        RecordingMessager messager = new RecordingMessager(Map.of(
                "success", true,
                "worktree_path", "/tmp/remote/worktree",
                "worktree_branch", "worktree-demo",
                "head_commit", "abc123",
                "existed", true
        ));
        WorktreeRemote.RemoteWorktreeBackend backend = new WorktreeRemote.RemoteWorktreeBackend(
                new WorktreeConfig(),
                messager,
                "node-42"
        );
        Path remoteRepo = initRemoteRepo("backend-create");
        Path repo = cloneRemoteRepo("backend-create-local", remoteRepo);

        WorktreeCreateResult result = backend.create("demo", repo.toString(), tempDir.resolve("ignored").toString()).join();

        assertThat(result.getWorktreePath()).isEqualTo("/tmp/remote/worktree");
        assertThat(result.getWorktreeBranch()).isEqualTo("worktree-demo");
        assertThat(result.getHeadCommit()).isEqualTo("abc123");
        assertThat(result.isExisted()).isTrue();
        assertThat(messager.nodeId).isEqualTo("node-42");
        assertThat(messager.payload)
                .containsEntry("action", "create")
                .containsEntry("slug", "demo");
        assertThat(messager.payload).containsKeys("repo_url", "base_branch", "config");
    }

    @Test
    void remoteHandlerCreateFetchesAndDelegatesToManager() throws IOException, InterruptedException {
        Path remoteRepo = initRemoteRepo("handler-create");
        FakeWorktreeBackend backend = new FakeWorktreeBackend();
        FakeRemoteManager manager = new FakeRemoteManager(backend);
        WorktreeRemote.WorktreeRemoteHandler handler = new WorktreeRemote.WorktreeRemoteHandler(manager);

        WorktreeRemote.WorktreeRemoteResponse response = handler.handle(
                new WorktreeRemote.WorktreeRemoteRequest(
                        "create",
                        "teammate-demo",
                        remoteRepo.toUri().toString(),
                        "main",
                        null,
                        Map.of("enabled", true)
                )
        ).join();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getWorktreePath()).isEqualTo(tempDir.resolve("created").resolve("teammate-demo").toString());
        assertThat(response.getWorktreeBranch()).isEqualTo("worktree-teammate-demo");
        assertThat(response.getHeadCommit()).isEqualTo("deadbeef");
        assertThat(manager.createdSlugs).containsExactly("teammate-demo");
    }

    @Test
    void remoteHandlerRemoveAndExistsDelegateToManagerBackend() {
        FakeWorktreeBackend backend = new FakeWorktreeBackend();
        backend.existsResult = true;
        FakeRemoteManager manager = new FakeRemoteManager(backend);
        WorktreeRemote.WorktreeRemoteHandler handler = new WorktreeRemote.WorktreeRemoteHandler(manager);

        WorktreeRemote.WorktreeRemoteResponse remove = handler.handle(
                new WorktreeRemote.WorktreeRemoteRequest("remove", null, null, null, tempDir.resolve("missing").toString(), null)
        ).join();
        WorktreeRemote.WorktreeRemoteResponse exists = handler.handle(
                new WorktreeRemote.WorktreeRemoteRequest("exists", null, null, null, tempDir.resolve("tracked").toString(), null)
        ).join();

        assertThat(remove.isSuccess()).isFalse();
        assertThat(remove.getError()).contains("Cannot find repo root");
        assertThat(exists.isSuccess()).isTrue();
        assertThat(exists.isExists()).isTrue();
        assertThat(backend.existsPaths).containsExactly(tempDir.resolve("tracked").toString());
    }

    @Test
    void remoteHandlerRejectsUnknownAction() {
        FakeRemoteManager manager = new FakeRemoteManager(new FakeWorktreeBackend());
        WorktreeRemote.WorktreeRemoteHandler handler = new WorktreeRemote.WorktreeRemoteHandler(manager);

        WorktreeRemote.WorktreeRemoteResponse response = handler.handle(
                new WorktreeRemote.WorktreeRemoteRequest("noop", null, null, null, null, null)
        ).join();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Unknown action: noop");
    }

    private Path initRemoteRepo(String name) throws IOException, InterruptedException {
        Path remote = Files.createDirectories(tempDir.resolve(name).resolve("remote.git"));
        runGit(remote.getParent(), "init", "--bare", remote.toString());
        Path seed = Files.createDirectories(tempDir.resolve(name).resolve("seed"));
        runGit(seed, "init", "-b", "main");
        runGit(seed, "config", "user.name", "Codex");
        runGit(seed, "config", "user.email", "codex@example.com");
        Files.writeString(seed.resolve("README.md"), "# remote\n");
        runGit(seed, "add", "README.md");
        runGit(seed, "commit", "-m", "init");
        runGit(seed, "remote", "add", "origin", remote.toString());
        runGit(seed, "push", "-u", "origin", "main");
        return remote;
    }

    private Path cloneRemoteRepo(String name, Path remoteRepo) throws IOException, InterruptedException {
        Path clone = tempDir.resolve(name);
        runGit(tempDir, "clone", remoteRepo.toString(), clone.toString());
        return clone;
    }

    private static void runGit(Path cwd, String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(buildCommand(args));
        builder.directory(cwd.toFile());
        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IOException("git command failed: " + String.join(" ", args) + " :: " + stderr);
        }
    }

    private static List<String> buildCommand(String... args) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private static final class RecordingMessager implements WorktreeRemote.RemoteWorktreeMessager {
        private final Map<String, Object> response;
        private String nodeId;
        private Map<String, Object> payload;

        private RecordingMessager(Map<String, Object> response) {
            this.response = response;
        }

        @Override
        public java.util.concurrent.CompletionStage<Map<String, Object>> sendAndWait(
                String nodeId,
                Map<String, Object> payload
        ) {
            this.nodeId = nodeId;
            this.payload = new LinkedHashMap<>(payload);
            return CompletableFuture.completedFuture(response);
        }
    }

    private final class FakeRemoteManager implements WorktreeRemote.RemoteWorktreeManager {
        private final FakeWorktreeBackend backend;
        private final java.util.List<String> createdSlugs = new java.util.ArrayList<>();

        private FakeRemoteManager(FakeWorktreeBackend backend) {
            this.backend = backend;
        }

        @Override
        public java.util.concurrent.CompletionStage<WorktreeCreateResult> createOwnerWorktree(String slug) {
            createdSlugs.add(slug);
            return CompletableFuture.completedFuture(new WorktreeCreateResult(
                    tempDir.resolve("created").resolve(slug).toString(),
                    "worktree-" + slug,
                    "deadbeef",
                    null,
                    false,
                    false
            ));
        }

        @Override
        public java.util.concurrent.CompletionStage<Boolean> removeWorktree(String worktreePath, String repoRoot) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public WorktreeBackend getBackend() {
            return backend;
        }
    }

    private static final class FakeWorktreeBackend implements WorktreeBackend {
        private boolean existsResult;
        private final java.util.List<String> existsPaths = new java.util.ArrayList<>();

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath) {
            return CompletableFuture.completedFuture(new WorktreeCreateResult(targetPath));
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            existsPaths.add(worktreePath);
            return CompletableFuture.completedFuture(existsResult);
        }
    }
}
