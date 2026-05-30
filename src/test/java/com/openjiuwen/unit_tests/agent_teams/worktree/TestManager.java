/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.WorktreeChangeSummary;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeLifecyclePolicy;
import com.openjiuwen.agent_teams.worktree.WorktreeManager;
import com.openjiuwen.agent_teams.worktree.WorktreeModels;
import com.openjiuwen.agent_teams.worktree.WorktreeSession;
import com.openjiuwen.agent_teams.worktree.WorktreeSessionHolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for worktree manager module.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_manager}.</p>
 */
class TestManager {

    @AfterEach
    void cleanSession() {
        WorktreeSessionHolder.setCurrentSession(null);
    }

    @Nested
    class TestEnter {
        @Test
        @Tag("level0")
        void testEnterCreatesWorktreeAndSetsSession(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            FakeGitProbe git = new FakeGitProbe(tempDir.resolve("repo").toString(), "main");
            Path cwd = tempDir.resolve("cwd");
            Path workspace = tempDir.resolve("workspace");
            WorktreeManager manager = makeManager(backend, git, cwd, workspace);

            WorktreeSession session = manager.enter("my-slug", "m1", "t1");

            String expectedTarget = workspace.resolve(".worktrees").resolve("my-slug").toString();
            assertEquals(expectedTarget, session.getWorktreePath());
            assertEquals("my-slug", session.getWorktreeName());
            assertEquals("m1", session.getMemberName());
            assertEquals("t1", session.getTeamName());
            assertEquals("main", session.getOriginalBranch());
            assertEquals("abc123", session.getOriginalHeadCommit());
            assertEquals("my-slug", backend.createdSlug);
            assertEquals(tempDir.resolve("repo").toString(), backend.createdRepoRoot);
            assertEquals(expectedTarget, backend.createdTarget);
            assertSame(session, WorktreeSessionHolder.getCurrentSession());
        }

        @Test
        @Tag("level0")
        void testEnterInvalidSlugRaises(@TempDir Path tempDir) {
            WorktreeManager manager = makeManager(new FakeBackend(), new FakeGitProbe("/repo", "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.enter("../escape", null, null)
            );

            assertTrue(error.getMessage().contains("Invalid worktree name"));
        }

        @Test
        @Tag("level0")
        void testEnterNotInGitRepoRaises(@TempDir Path tempDir) {
            WorktreeManager manager = makeManager(new FakeBackend(), new FakeGitProbe(null, "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> manager.enter("valid-slug", null, null)
            );

            assertTrue(error.getMessage().contains("not in a git repository"));
        }

        @Test
        @Tag("level0")
        void testEnterPublishesEvent(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            FakeGitProbe git = new FakeGitProbe(tempDir.resolve("repo").toString(), "main");
            List<WorktreeManager.WorktreeEvent> events = new ArrayList<>();
            WorktreeManager manager = makeManager(backend, git, tempDir.resolve("cwd"), tempDir.resolve("workspace"));
            manager.setEventHandler(events::add);

            manager.enter("ev-slug", "m1", "t1");

            assertEquals(1, events.size());
            assertEquals("worktree_created", events.get(0).getType());
        }
    }

    @Nested
    class TestExit {
        @Test
        @Tag("level0")
        void testExitKeep(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            FakeGitProbe git = new FakeGitProbe(tempDir.resolve("repo").toString(), "main");
            WorktreeManager manager = makeManager(backend, git, tempDir.resolve("cwd"), tempDir.resolve("workspace"));
            manager.enter("keep-slug", null, null);

            Map<String, String> result = manager.exit("keep");

            assertEquals("keep", result.get("action"));
            assertNull(WorktreeSessionHolder.getCurrentSession());
            assertEquals(0, backend.removeCalls.get());
        }

        @Test
        @Tag("level0")
        void testExitRemove(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            FakeGitProbe git = new FakeGitProbe(tempDir.resolve("repo").toString(), "main");
            git.statusLines = List.of();
            git.commitCount = 0;
            WorktreeManager manager = makeManager(backend, git, tempDir.resolve("cwd"), tempDir.resolve("workspace"));
            manager.enter("rm-slug", null, null);

            Map<String, String> result = manager.exit("remove");

            assertEquals("remove", result.get("action"));
            assertNull(WorktreeSessionHolder.getCurrentSession());
            assertEquals(1, backend.removeCalls.get());
        }

        @Test
        @Tag("level1")
        void testExitRemoveWithChangesRaises(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            FakeGitProbe git = new FakeGitProbe(tempDir.resolve("repo").toString(), "main");
            git.statusLines = List.of("M file.py");
            git.commitCount = 0;
            WorktreeManager manager = makeManager(backend, git, tempDir.resolve("cwd"), tempDir.resolve("workspace"));
            manager.enter("dirty-slug", null, null);

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.exit("remove")
            );

            assertTrue(error.getMessage().contains("uncommitted files"));
        }

        @Test
        @Tag("level1")
        void testExitRemoveWithChangesDiscard(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            FakeGitProbe git = new FakeGitProbe(tempDir.resolve("repo").toString(), "main");
            git.statusLines = List.of("M file.py");
            git.commitCount = 0;
            WorktreeManager manager = makeManager(backend, git, tempDir.resolve("cwd"), tempDir.resolve("workspace"));
            manager.enter("discard-slug", null, null);

            Map<String, String> result = manager.exit("remove", true);

            assertEquals("remove", result.get("action"));
            assertEquals(1, backend.removeCalls.get());
        }
    }

    @Nested
    class TestCreateAgentWorktree {
        @Test
        @Tag("level1")
        void testDoesNotModifyContextVar(@TempDir Path tempDir) {
            FakeBackend backend = new FakeBackend();
            WorktreeManager manager = makeManager(backend, new FakeGitProbe(tempDir.resolve("repo").toString(), "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            WorktreeModels.WorktreeCreateResult result = manager.createAgentWorktree("agent-slug");

            assertEquals(tempDir.resolve("workspace").resolve(".worktrees").resolve("agent-slug").toString(),
                    result.getWorktreePath());
            assertNull(WorktreeSessionHolder.getCurrentSession());
        }

        @Test
        @Tag("level1")
        void testInvalidSlugRaises(@TempDir Path tempDir) {
            WorktreeManager manager = makeManager(new FakeBackend(), new FakeGitProbe("/repo", "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.createAgentWorktree("../../bad")
            );

            assertTrue(error.getMessage().contains("Invalid worktree name"));
        }
    }

    @Nested
    class TestMemberSlug {
        @Test
        @Tag("level1")
        void testFormat(@TempDir Path tempDir) {
            WorktreeManager manager = makeManager(new FakeBackend(), new FakeGitProbe("/repo", "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            assertEquals("teammate-abcdef12", manager.memberSlug("abcdef1234567890"));
        }

        @Test
        @Tag("level1")
        void testShortId(@TempDir Path tempDir) {
            WorktreeManager manager = makeManager(new FakeBackend(), new FakeGitProbe("/repo", "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            assertEquals("teammate-abc", manager.memberSlug("abc"));
        }
    }

    @Nested
    class TestResolvePolicy {
        @Test
        @Tag("level1")
        void testAutoResolvesToEphemeral(@TempDir Path tempDir) {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(true);
            config.setLifecyclePolicy(WorktreeLifecyclePolicy.AUTO);
            WorktreeManager manager = makeManager(config, new FakeBackend(), new FakeGitProbe("/repo", "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            assertEquals(WorktreeLifecyclePolicy.EPHEMERAL, manager.resolvePolicy());
        }

        @Test
        @Tag("level1")
        void testExplicitDurable(@TempDir Path tempDir) {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(true);
            config.setLifecyclePolicy(WorktreeLifecyclePolicy.DURABLE);
            WorktreeManager manager = makeManager(config, new FakeBackend(), new FakeGitProbe("/repo", "main"),
                    tempDir.resolve("cwd"), tempDir.resolve("workspace"));

            assertEquals(WorktreeLifecyclePolicy.DURABLE, manager.resolvePolicy());
        }
    }

    @Nested
    class TestFireRail {
        @Test
        @Tag("level1")
        void testCallsAllRails(@TempDir Path tempDir) {
            RecordingRail railA = new RecordingRail("a");
            RecordingRail railB = new RecordingRail("b");
            WorktreeManager manager = makeManager(new WorktreeConfig(), new FakeBackend(),
                    new FakeGitProbe("/repo", "main"), tempDir.resolve("cwd"), tempDir.resolve("workspace"),
                    List.of(railA, railB));

            Object result = manager.fireRail("onEnter", "arg1");

            assertEquals(List.of("arg1"), railA.calls);
            assertEquals(List.of("arg1"), railB.calls);
            assertEquals("b", result);
        }

        @Test
        @Tag("level1")
        void testSkipsRailsWithoutMethod(@TempDir Path tempDir) {
            WorktreeManager manager = makeManager(new WorktreeConfig(), new FakeBackend(),
                    new FakeGitProbe("/repo", "main"), tempDir.resolve("cwd"), tempDir.resolve("workspace"),
                    List.of(new Object()));

            Object result = manager.fireRail("onEnter");

            assertNull(result);
        }
    }

    private static WorktreeManager makeManager(
            FakeBackend backend,
            FakeGitProbe git,
            Path cwd,
            Path workspace
    ) {
        return makeManager(new WorktreeConfig(), backend, git, cwd, workspace);
    }

    private static WorktreeManager makeManager(
            WorktreeConfig config,
            FakeBackend backend,
            FakeGitProbe git,
            Path cwd,
            Path workspace
    ) {
        return makeManager(config, backend, git, cwd, workspace, List.of());
    }

    private static WorktreeManager makeManager(
            WorktreeConfig config,
            FakeBackend backend,
            FakeGitProbe git,
            Path cwd,
            Path workspace,
            List<?> rails
    ) {
        WorktreeManager manager = new WorktreeManager(config, backend, null, rails, workspace.toString(), git);
        manager.setCurrentCwd(cwd.toString());
        return manager;
    }

    private static final class FakeBackend implements WorktreeManager.WorktreeBackend {
        private String createdSlug;
        private String createdRepoRoot;
        private String createdTarget;
        private String removedPath;
        private String removedRepoRoot;
        private final AtomicInteger removeCalls = new AtomicInteger();

        @Override
        public WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot, String targetPath) {
            createdSlug = slug;
            createdRepoRoot = repoRoot;
            createdTarget = targetPath;
            return WorktreeModels.WorktreeCreateResult.success(targetPath, "worktree-" + slug, "abc123");
        }

        @Override
        public boolean remove(String worktreePath, boolean force) {
            removeCalls.incrementAndGet();
            removedPath = worktreePath;
            return true;
        }

        @Override
        public boolean remove(String worktreePath, String repoRoot) {
            removeCalls.incrementAndGet();
            removedPath = worktreePath;
            removedRepoRoot = repoRoot;
            return true;
        }

        @Override
        public WorktreeChangeSummary detectChanges(String worktreePath) {
            return new WorktreeChangeSummary(0, 0);
        }
    }

    private static final class FakeGitProbe implements WorktreeManager.GitProbe {
        private final String repoRoot;
        private final String branch;
        private List<String> statusLines = List.of();
        private Integer commitCount = 0;

        private FakeGitProbe(String repoRoot, String branch) {
            this.repoRoot = repoRoot;
            this.branch = branch;
        }

        @Override
        public String findCanonicalGitRoot(String cwd) {
            return repoRoot;
        }

        @Override
        public String getCurrentBranch(String cwd) {
            return branch;
        }

        @Override
        public List<String> statusPorcelain(String cwd) {
            return statusLines;
        }

        @Override
        public Integer countCommitsSince(String baseCommit, String cwd) {
            return commitCount;
        }

        @Override
        public void worktreePrune(String repoRoot) {
            // Not needed for these tests.
        }
    }

    public static final class RecordingRail {
        private final String value;
        private final List<String> calls = new ArrayList<>();

        RecordingRail(String value) {
            this.value = value;
        }

        public String onEnter(String arg) {
            calls.add(arg);
            return value;
        }
    }
}
