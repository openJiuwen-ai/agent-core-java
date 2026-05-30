/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.WorktreeCleanup;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeCreateResult;
import com.openjiuwen.agent_teams.worktree.models.WorktreeBackend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for worktree cleanup.
 *
 * <p>Mirrors Python's {@code test_cleanup} in
 * {@code tests.unit_tests.agent_teams.worktree.test_cleanup}.</p>
 */
@DisplayName("TestCleanup")
class TestCleanup {

    @Nested
    @DisplayName("Test isEphemeralSlug")
    class TestIsEphemeralSlug {

        @Test
        @Tag("level0")
        @DisplayName("teammate hex8 is ephemeral")
        void testTeammateHex8() {
            assertTrue(WorktreeCleanup.isEphemeralSlug("teammate-a1b2c3d4"));
        }

        @Test
        @Tag("level0")
        @DisplayName("agent hex7 is ephemeral")
        void testAgentHex7() {
            assertTrue(WorktreeCleanup.isEphemeralSlug("agent-1234567"));
        }

        @Test
        @Tag("level0")
        @DisplayName("feature branch is not ephemeral")
        void testFeatureBranchNotEphemeral() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("feature-auth"));
        }

        @Test
        @Tag("level0")
        @DisplayName("arbitrary slug is not ephemeral")
        void testArbitrarySlugNotEphemeral() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("my-worktree"));
        }

        @Test
        @Tag("level0")
        @DisplayName("teammate too short is not ephemeral")
        void testTeammateTooShort() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("teammate-abc"));
        }

        @Test
        @Tag("level1")
        @DisplayName("teammate uppercase not matched")
        void testTeammateUppercaseNotMatched() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("teammate-A1B2C3D4"));
        }

        @Test
        @Tag("level1")
        @DisplayName("agent too long is not ephemeral")
        void testAgentTooLong() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("agent-12345678"));
        }

        @Test
        @Tag("level0")
        @DisplayName("null slug is not ephemeral")
        void testNullSlug() {
            assertFalse(WorktreeCleanup.isEphemeralSlug(null));
        }

        @Test
        @Tag("level0")
        @DisplayName("empty string is not ephemeral")
        void testEmptyString() {
            assertFalse(WorktreeCleanup.isEphemeralSlug(""));
        }
    }

    @Nested
    @DisplayName("Test cleanupStaleWorktrees")
    class TestCleanupStaleWorktrees {

        @Test
        @Tag("level1")
        @DisplayName("expired worktree without changes is removed")
        void testExpiredNoChangesRemoved(@TempDir Path tempDir) throws Exception {
            Fixture fixture = new Fixture(tempDir);
            FakeGitProbe probe = FakeGitProbe.forRepo(fixture.repo);
            FakeBackend backend = new FakeBackend();
            Path worktree = fixture.expiredWorktree("teammate-a1b2c3d4");

            int removed = new WorktreeCleanup(probe).cleanupStaleWorktrees(
                    config(),
                    backend,
                    null,
                    fixture.repo.toString(),
                    fixture.workspace.toString()).join();

            assertEquals(1, removed);
            assertEquals(List.of(worktree.toString()), backend.removedPaths);
            assertEquals(List.of(fixture.repo.toString()), backend.repoPaths);
            assertEquals(1, probe.pruneCalls);
        }

        @Test
        @Tag("level1")
        @DisplayName("recent worktree is skipped")
        void testNotExpiredSkipped(@TempDir Path tempDir) throws Exception {
            Fixture fixture = new Fixture(tempDir);
            FakeGitProbe probe = FakeGitProbe.forRepo(fixture.repo);
            FakeBackend backend = new FakeBackend();
            fixture.worktree("teammate-a1b2c3d4");

            int removed = new WorktreeCleanup(probe).cleanupStaleWorktrees(
                    config(),
                    backend,
                    null,
                    fixture.repo.toString(),
                    fixture.workspace.toString()).join();

            assertEquals(0, removed);
            assertTrue(backend.removedPaths.isEmpty());
            assertTrue(probe.statusPaths.isEmpty());
        }

        @Test
        @Tag("level1")
        @DisplayName("worktree with uncommitted changes is skipped")
        void testHasChangesSkipped(@TempDir Path tempDir) throws Exception {
            Fixture fixture = new Fixture(tempDir);
            FakeGitProbe probe = FakeGitProbe.forRepo(fixture.repo);
            probe.statusLines = List.of("M dirty.py");
            FakeBackend backend = new FakeBackend();
            fixture.expiredWorktree("teammate-b2c3d4e5");

            int removed = new WorktreeCleanup(probe).cleanupStaleWorktrees(
                    config(),
                    backend,
                    null,
                    fixture.repo.toString(),
                    fixture.workspace.toString()).join();

            assertEquals(0, removed);
            assertTrue(backend.removedPaths.isEmpty());
            assertEquals(0, probe.pruneCalls);
        }

        @Test
        @Tag("level1")
        @DisplayName("worktree with unpushed commits is skipped")
        void testHasUnpushedCommitsSkipped(@TempDir Path tempDir) throws Exception {
            Fixture fixture = new Fixture(tempDir);
            FakeGitProbe probe = FakeGitProbe.forRepo(fixture.repo);
            probe.hasUnpushed = true;
            FakeBackend backend = new FakeBackend();
            fixture.expiredWorktree("agent-1234567");

            int removed = new WorktreeCleanup(probe).cleanupStaleWorktrees(
                    config(),
                    backend,
                    null,
                    fixture.repo.toString(),
                    fixture.workspace.toString()).join();

            assertEquals(0, removed);
            assertTrue(backend.removedPaths.isEmpty());
            assertEquals(0, probe.pruneCalls);
        }

        @Test
        @Tag("level1")
        @DisplayName("current worktree is skipped")
        void testCurrentWorktreeSkipped(@TempDir Path tempDir) throws Exception {
            Fixture fixture = new Fixture(tempDir);
            FakeGitProbe probe = FakeGitProbe.forRepo(fixture.repo);
            FakeBackend backend = new FakeBackend();
            Path worktree = fixture.expiredWorktree("teammate-c3d4e5f6");

            int removed = new WorktreeCleanup(probe).cleanupStaleWorktrees(
                    config(),
                    backend,
                    worktree.toString(),
                    fixture.repo.toString(),
                    fixture.workspace.toString()).join();

            assertEquals(0, removed);
            assertTrue(backend.removedPaths.isEmpty());
            assertTrue(probe.statusPaths.isEmpty());
        }

        @Test
        @Tag("level1")
        @DisplayName("cleanup returns zero outside a git repo")
        void testNoRepoReturnsZero(@TempDir Path tempDir) throws Exception {
            Fixture fixture = new Fixture(tempDir);
            FakeGitProbe probe = new FakeGitProbe();
            FakeBackend backend = new FakeBackend();

            int removed = new WorktreeCleanup(probe).cleanupStaleWorktrees(
                    config(),
                    backend,
                    null,
                    fixture.repo.toString(),
                    fixture.workspace.toString()).join();

            assertEquals(0, removed);
            assertTrue(backend.removedPaths.isEmpty());
        }
    }

    private static WorktreeConfig config() {
        WorktreeConfig config = new WorktreeConfig();
        config.setEnabled(true);
        config.setCleanupAfterDays(30);
        return config;
    }

    private static class Fixture {
        private final Path repo;
        private final Path workspace;
        private final Path worktreesDir;

        Fixture(Path tempDir) throws Exception {
            this.repo = tempDir.resolve("repo");
            this.workspace = tempDir.resolve("workspace");
            this.worktreesDir = workspace.resolve(".worktrees");
            Files.createDirectories(repo);
            Files.createDirectories(worktreesDir);
        }

        Path worktree(String slug) throws Exception {
            Path path = worktreesDir.resolve(slug);
            Files.createDirectories(path);
            return path;
        }

        Path expiredWorktree(String slug) throws Exception {
            Path path = worktree(slug);
            FileTime oldTime = FileTime.fromMillis(
                    System.currentTimeMillis() - Duration.ofDays(60).toMillis());
            Files.setLastModifiedTime(path, oldTime);
            return path;
        }
    }

    private static class FakeGitProbe implements WorktreeCleanup.GitProbe {
        private String repoRoot;
        private List<String> statusLines = List.of();
        private Boolean hasUnpushed = false;
        private int pruneCalls;
        private final List<String> statusPaths = new ArrayList<>();

        static FakeGitProbe forRepo(Path repo) {
            FakeGitProbe probe = new FakeGitProbe();
            probe.repoRoot = repo.toString();
            return probe;
        }

        @Override
        public CompletableFuture<String> findCanonicalGitRoot(String cwd) {
            return CompletableFuture.completedFuture(repoRoot);
        }

        @Override
        public CompletableFuture<List<String>> statusPorcelain(String cwd) {
            statusPaths.add(cwd);
            return CompletableFuture.completedFuture(statusLines);
        }

        @Override
        public CompletableFuture<Boolean> hasUnpushedCommits(String cwd) {
            return CompletableFuture.completedFuture(hasUnpushed);
        }

        @Override
        public CompletableFuture<Void> worktreePrune(String repoRoot) {
            pruneCalls++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class FakeBackend implements WorktreeBackend {
        private final List<String> removedPaths = new ArrayList<>();
        private final List<String> repoPaths = new ArrayList<>();

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoPath, String target) {
            return CompletableFuture.completedFuture(new WorktreeCreateResult(target, null, null, false));
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoPath) {
            removedPaths.add(worktreePath);
            repoPaths.add(repoPath);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
