/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's cleanup regression coverage in
 * {@code tests/unit_tests/harness/tools/worktree/test_cleanup.py}.
 */
class WorktreeCleanupTest {

    @TempDir
    Path tempDir;

    @Test
    void teammateHex8IsEphemeral() {
        assertTrue(WorktreeCleanup.isEphemeralSlug("teammate-a1b2c3d4"));
    }

    @Test
    void agentHex7IsEphemeral() {
        assertTrue(WorktreeCleanup.isEphemeralSlug("agent-1234567"));
    }

    @Test
    void featureBranchIsNotEphemeral() {
        assertFalse(WorktreeCleanup.isEphemeralSlug("feature-auth"));
    }

    @Test
    void arbitrarySlugIsNotEphemeral() {
        assertFalse(WorktreeCleanup.isEphemeralSlug("my-worktree"));
    }

    @Test
    void teammateTooShortIsRejected() {
        assertFalse(WorktreeCleanup.isEphemeralSlug("teammate-abc"));
    }

    @Test
    void teammateUppercaseIsRejected() {
        assertFalse(WorktreeCleanup.isEphemeralSlug("teammate-A1B2C3D4"));
    }

    @Test
    void agentTooLongIsRejected() {
        assertFalse(WorktreeCleanup.isEphemeralSlug("agent-12345678"));
    }

    @Test
    void expiredWorktreeWithNoChangesIsRemoved() throws Exception {
        TestFixture fixture = createFixture();
        Path worktree = createWorktree(fixture.worktreesDir(), "teammate-a1b2c3d4", fixture.now().minus(Duration.ofDays(60)));
        fixture.dependencies().setStatus(worktree, List.of());
        fixture.dependencies().setUnpushed(worktree, false);

        int removed = WorktreeCleanup.cleanupStaleWorktrees(
                fixture.config(),
                fixture.backend(),
                null,
                fixture.dependencies()
        ).join();

        assertEquals(1, removed);
        assertEquals(List.of(worktree.toString()), fixture.backend().removedPaths());
        assertTrue(fixture.dependencies().pruned());
    }

    @Test
    void nonExpiredWorktreeIsSkipped() throws Exception {
        TestFixture fixture = createFixture();
        createWorktree(fixture.worktreesDir(), "teammate-a1b2c3d4", fixture.now());

        int removed = WorktreeCleanup.cleanupStaleWorktrees(
                fixture.config(),
                fixture.backend(),
                null,
                fixture.dependencies()
        ).join();

        assertEquals(0, removed);
        assertTrue(fixture.backend().removedPaths().isEmpty());
        assertFalse(fixture.dependencies().pruned());
    }

    @Test
    void worktreeWithChangesIsSkipped() throws Exception {
        TestFixture fixture = createFixture();
        Path worktree = createWorktree(fixture.worktreesDir(), "teammate-b2c3d4e5", fixture.now().minus(Duration.ofDays(60)));
        fixture.dependencies().setStatus(worktree, List.of("M dirty.py"));
        fixture.dependencies().setUnpushed(worktree, false);

        int removed = WorktreeCleanup.cleanupStaleWorktrees(
                fixture.config(),
                fixture.backend(),
                null,
                fixture.dependencies()
        ).join();

        assertEquals(0, removed);
        assertTrue(fixture.backend().removedPaths().isEmpty());
    }

    @Test
    void worktreeWithUnpushedCommitsIsSkipped() throws Exception {
        TestFixture fixture = createFixture();
        Path worktree = createWorktree(fixture.worktreesDir(), "agent-1234567", fixture.now().minus(Duration.ofDays(60)));
        fixture.dependencies().setStatus(worktree, List.of());
        fixture.dependencies().setUnpushed(worktree, true);

        int removed = WorktreeCleanup.cleanupStaleWorktrees(
                fixture.config(),
                fixture.backend(),
                null,
                fixture.dependencies()
        ).join();

        assertEquals(0, removed);
        assertTrue(fixture.backend().removedPaths().isEmpty());
    }

    @Test
    void currentWorktreeIsSkipped() throws Exception {
        TestFixture fixture = createFixture();
        Path worktree = createWorktree(fixture.worktreesDir(), "teammate-c3d4e5f6", fixture.now().minus(Duration.ofDays(60)));
        fixture.dependencies().setStatus(worktree, List.of());
        fixture.dependencies().setUnpushed(worktree, false);

        int removed = WorktreeCleanup.cleanupStaleWorktrees(
                fixture.config(),
                fixture.backend(),
                worktree.toString(),
                fixture.dependencies()
        ).join();

        assertEquals(0, removed);
        assertTrue(fixture.backend().removedPaths().isEmpty());
    }

    @Test
    void missingRepoRootReturnsZero() throws Exception {
        TestFixture fixture = createFixture();
        fixture.dependencies().setRepoRoot(null);

        int removed = WorktreeCleanup.cleanupStaleWorktrees(
                fixture.config(),
                fixture.backend(),
                null,
                fixture.dependencies()
        ).join();

        assertEquals(0, removed);
    }

    private TestFixture createFixture() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Path worktreesDir = Files.createDirectories(workspace.resolve(".worktrees"));
        WorktreeConfig config = new WorktreeConfig();
        config.setEnabled(true);
        config.setCleanupAfterDays(30);
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        FakeDependencies dependencies = new FakeDependencies(repo.toString(), workspace.toString(), repo.toString(), now);
        return new TestFixture(config, new FakeBackend(), dependencies, worktreesDir, now);
    }

    private Path createWorktree(Path worktreesDir, String slug, Instant modifiedAt) throws IOException {
        Path worktree = Files.createDirectories(worktreesDir.resolve(slug));
        Files.setLastModifiedTime(worktree, FileTime.from(modifiedAt));
        return worktree;
    }

    private record TestFixture(
            WorktreeConfig config,
            FakeBackend backend,
            FakeDependencies dependencies,
            Path worktreesDir,
            Instant now
    ) {
    }

    private static final class FakeBackend implements WorktreeBackend {
        private final java.util.ArrayList<String> removedPaths = new java.util.ArrayList<>();

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("create not used"));
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
            removedPaths.add(worktreePath);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            return CompletableFuture.completedFuture(false);
        }

        public List<String> removedPaths() {
            return List.copyOf(removedPaths);
        }
    }

    private static final class FakeDependencies implements WorktreeCleanup.Dependencies {
        private final String cwd;
        private final String workspace;
        private final Instant now;
        private final Map<String, List<String>> statuses = new HashMap<>();
        private final Map<String, Boolean> unpushed = new HashMap<>();
        private String repoRoot;
        private boolean pruned;

        private FakeDependencies(String cwd, String workspace, String repoRoot, Instant now) {
            this.cwd = cwd;
            this.workspace = workspace;
            this.repoRoot = repoRoot;
            this.now = now;
        }

        @Override
        public String getCwd() {
            return cwd;
        }

        @Override
        public String getWorkspace() {
            return workspace;
        }

        @Override
        public CompletableFuture<String> findCanonicalGitRoot(String cwd) {
            return CompletableFuture.completedFuture(repoRoot);
        }

        @Override
        public CompletableFuture<List<String>> statusPorcelain(String cwd) {
            return CompletableFuture.completedFuture(statuses.getOrDefault(cwd, List.of()));
        }

        @Override
        public CompletableFuture<Boolean> hasUnpushedCommits(String cwd) {
            return CompletableFuture.completedFuture(unpushed.get(cwd));
        }

        @Override
        public CompletableFuture<Void> worktreePrune(String repoRoot) {
            pruned = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<String> listEntries(Path dir) throws IOException {
            try (var stream = Files.list(dir)) {
                return stream.map(path -> path.getFileName().toString()).toList();
            }
        }

        @Override
        public Instant lastModified(Path path) throws IOException {
            return Files.getLastModifiedTime(path).toInstant();
        }

        @Override
        public Instant now() {
            return now;
        }

        private void setStatus(Path worktree, List<String> changes) {
            statuses.put(worktree.toString(), changes);
        }

        private void setUnpushed(Path worktree, Boolean hasUnpushedCommits) {
            unpushed.put(worktree.toString(), hasUnpushedCommits);
        }

        private void setRepoRoot(String repoRoot) {
            this.repoRoot = repoRoot;
        }

        private boolean pruned() {
            return pruned;
        }
    }
}
