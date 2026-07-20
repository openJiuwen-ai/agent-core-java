
package com.openjiuwen.agentteams.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.core.testsupport.OsTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class WorktreeCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void worktreeConfigShouldExposeExpectedDefaults() {
        WorktreeConfig config = WorktreeConfig.builder().build();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getCleanupAfterDays()).isEqualTo(30);
        assertThat(config.getLifecyclePolicy()).isEqualTo(WorktreeLifecyclePolicy.AUTO);
    }

    @Test
    void worktreeSessionStateShouldSetClearAndRequireCurrentSession() {
        WorktreeSessionState.setCurrentSession(null);
        assertThat(WorktreeSessionState.getCurrentSession()).isNull();
        assertThatThrownBy(WorktreeSessionState::requireCurrentSession).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not in a worktree session");

        WorktreeSession session = WorktreeSession.builder().originalCwd("/repo")
                .worktreePath("/workspace/.worktrees/test").worktreeName("test").build();
        WorktreeSessionState.setCurrentSession(session);
        assertThat(WorktreeSessionState.getCurrentSession()).isSameAs(session);
        assertThat(WorktreeSessionState.requireCurrentSession()).isSameAs(session);

        WorktreeSessionState.setCurrentSession(null);
        assertThat(WorktreeSessionState.getCurrentSession()).isNull();
    }

    @Test
    void worktreeManagerShouldFireRailsAndUseLastNonNullResult() {
        List<String> calls = new ArrayList<>();
        WorktreeRail railA = new WorktreeRail() {
            @Override
            public String beforeWorktreeCreate(String slug, String repoRoot) {
                calls.add("a:" + slug);
                return "from-a";
            }
        };
        WorktreeRail railB = new WorktreeRail() {
            @Override
            public String beforeWorktreeCreate(String slug, String repoRoot) {
                calls.add("b:" + slug);
                return "from-b";
            }
        };
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build(), List.of(railA, railB));

        Object result = manager.fireRail("beforeWorktreeCreate", new Object[]{"original", "/repo"});

        assertThat(calls).containsExactly("a:original", "b:original");
        assertThat(result).isEqualTo("from-b");
    }

    @Test
    void worktreeManagerShouldApplyCreateRailsAroundEnter() throws Exception {
        Path repoRoot = createGitRepo();
        List<String> calls = new ArrayList<>();
        WorktreeRail rail = new WorktreeRail() {
            @Override
            public String beforeWorktreeCreate(String slug, String repoRoot) {
                calls.add("before:" + slug);
                return "rail-slug";
            }

            @Override
            public void afterWorktreeCreate(WorktreeSession session) {
                calls.add("after:" + session.getWorktreeName());
            }
        };
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build(), List.of(rail));

        WorktreeSession session = manager.enter("initial", repoRoot.toString(), "member1", "teamA");

        assertThat(session.getWorktreeName()).isEqualTo("rail-slug");
        assertThat(calls).containsExactly("before:initial", "after:rail-slug");
    }

    @Test
    void worktreeRailShouldDispatchAllPythonLifecycleHooksAndReturnLastNonNull() {
        WorktreeSession session = WorktreeSession.builder().originalCwd("/repo").worktreePath("/repo/.worktrees/member")
                .worktreeName("member").build();
        List<String> calls = new ArrayList<>();
        WorktreeRail railA = new WorktreeRail() {
            @Override
            public String beforeWorktreeExit(WorktreeSession session, String action) {
                calls.add("before-exit-a:" + action);
                return "keep";
            }

            @Override
            public boolean onWorktreeFileWrite(WorktreeSession session, String filePath) {
                calls.add("file-a:" + filePath);
                return false;
            }

            @Override
            public String beforeWorktreeCommit(WorktreeSession session, String message, List<String> files) {
                calls.add("commit-a:" + message + ":" + files.size());
                return "message-a";
            }

            @Override
            public List<String> onWorktreeSync(WorktreeSession session, String direction, List<String> files) {
                calls.add("sync-a:" + direction);
                return List.of("a.txt");
            }
        };
        WorktreeRail railB = new WorktreeRail() {
            @Override
            public String beforeWorktreeExit(WorktreeSession session, String action) {
                calls.add("before-exit-b:" + action);
                return "remove";
            }

            @Override
            public void afterWorktreeExit(WorktreeSession session, String action) {
                calls.add("after-exit-b:" + action);
            }

            @Override
            public boolean onWorktreeFileWrite(WorktreeSession session, String filePath) {
                calls.add("file-b:" + filePath);
                return true;
            }

            @Override
            public String beforeWorktreeCommit(WorktreeSession session, String message, List<String> files) {
                calls.add("commit-b:" + message + ":" + files.size());
                return "message-b";
            }

            @Override
            public void afterWorktreeCommit(WorktreeSession session, String commitSha) {
                calls.add("after-commit-b:" + commitSha);
            }

            @Override
            public List<String> onWorktreeSync(WorktreeSession session, String direction, List<String> files) {
                calls.add("sync-b:" + direction);
                return List.of("b.txt");
            }
        };
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build(), List.of(railA, railB));

        assertThat(manager.fireRail("beforeWorktreeExit", new Object[]{session, "keep"})).isEqualTo("remove");
        manager.fireRail("afterWorktreeExit", new Object[]{session, "remove"});
        assertThat(manager.fireRail("onWorktreeFileWrite", new Object[]{session, "/repo/file.txt"})).isEqualTo(true);
        assertThat(manager.fireRail("beforeWorktreeCommit", new Object[]{session, "msg", List.of("a.txt", "b.txt")}))
                .isEqualTo("message-b");
        manager.fireRail("afterWorktreeCommit", new Object[]{session, "abc123"});
        assertThat(manager.fireRail("onWorktreeSync", new Object[]{session, "push", List.of("x.txt")}))
                .isEqualTo(List.of("b.txt"));

        assertThat(calls).containsExactly("before-exit-a:keep", "before-exit-b:keep", "after-exit-b:remove",
                "file-a:/repo/file.txt", "file-b:/repo/file.txt", "commit-a:msg:2", "commit-b:msg:2",
                "after-commit-b:abc123", "sync-a:push", "sync-b:push");
    }

    @Test
    void worktreeManagerShouldApplyExitRailsAroundExit() throws Exception {
        Path repoRoot = createGitRepo();
        List<String> calls = new ArrayList<>();
        WorktreeRail rail = new WorktreeRail() {
            @Override
            public String beforeWorktreeExit(WorktreeSession session, String action) {
                calls.add("before:" + action + ":" + session.getWorktreeName());
                return "keep";
            }

            @Override
            public void afterWorktreeExit(WorktreeSession session, String action) {
                calls.add("after:" + action + ":" + session.getWorktreeName());
            }
        };
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build(), List.of(rail));
        WorktreeSession session = manager.enter("exit-rail", repoRoot.toString(), "member1", "teamA");

        manager.exit("remove", false);

        assertThat(manager.getCurrentSession()).isNull();
        assertThat(Files.exists(Path.of(session.getWorktreePath()))).isTrue();
        assertThat(calls).containsExactly("before:remove:exit-rail", "after:keep:exit-rail");
    }

    @Test
    void remoteHandlerShouldDispatchExistsRemoveAndUnknownActions() throws Exception {
        Path repoRoot = createGitRepo();
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build());
        WorktreeSession session = manager.enter("remote-test", repoRoot.toString(), "member1", "teamA");
        manager.exit("keep", false);
        WorktreeRemoteHandler handler = new WorktreeRemoteHandler(manager);

        WorktreeRemoteResponse exists = handler.handle(
                WorktreeRemoteRequest.builder().action("exists").worktreePath(session.getWorktreePath()).build());
        WorktreeRemoteResponse unknown = handler.handle(WorktreeRemoteRequest.builder().action("unknown").build());
        WorktreeRemoteResponse removed = handler.handle(
                WorktreeRemoteRequest.builder().action("remove").worktreePath(session.getWorktreePath()).build());

        assertThat(exists.isExists()).isTrue();
        assertThat(unknown.isSuccess()).isFalse();
        assertThat(unknown.getError()).contains("Unknown action");
        assertThat(removed.isSuccess()).isTrue();
        assertThat(Files.exists(Path.of(session.getWorktreePath()))).isFalse();
    }

    @Test
    void ephemeralSlugShouldMatchPythonCleanupPatterns() {
        assertThat(WorktreeManager.isEphemeralSlug("teammate-a1b2c3d4")).isTrue();
        assertThat(WorktreeManager.isEphemeralSlug("agent-1234567")).isTrue();
        assertThat(WorktreeManager.isEphemeralSlug("teammate-abc")).isFalse();
        assertThat(WorktreeManager.isEphemeralSlug("teammate-A1B2C3D4")).isFalse();
        assertThat(WorktreeManager.isEphemeralSlug("feature-auth")).isFalse();
        assertThat(WorktreeManager.memberSlug("abcdef1234567890")).isEqualTo("teammate-abcdef12");
        assertThat(WorktreeManager.memberSlug("abc")).isEqualTo("teammate-abc");
    }

    @Test
    void managerShouldEnterCountChangesAndExit() throws Exception {
        Path repoRoot = createGitRepo();
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build());

        WorktreeSession session = manager.enter("feature123", repoRoot.toString(), "member1", "teamA");
        assertThat(session.getWorktreeName()).isEqualTo("feature123");
        assertThat(session.getWorktreeBranch()).isEqualTo("worktree-feature123");
        assertThat(session.getOriginalHeadCommit()).hasSize(40);
        assertThat(Files.isDirectory(Path.of(session.getWorktreePath()))).isTrue();
        assertThat(Files.isRegularFile(Path.of(session.getWorktreePath()).resolve(".git"))).isTrue();

        Files.writeString(Path.of(session.getWorktreePath()).resolve("note.txt"), "hello");
        WorktreeChangeSummary summary = manager.countChanges();
        assertThat(summary.getChangedFiles()).isEqualTo(1);
        assertThat(summary.getCommits()).isEqualTo(0);

        manager.exit("keep", false);
        assertThat(manager.getCurrentSession()).isNull();
        assertThat(Files.exists(Path.of(session.getWorktreePath()))).isTrue();
    }

    @Test
    void managerShouldRemoveGitWorktreeAndDeleteBranch() throws Exception {
        Path repoRoot = createGitRepo();
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build());

        WorktreeSession session = manager.enter("remove123", repoRoot.toString(), "member1", "teamA");

        manager.exit("remove", false);

        assertThat(manager.getCurrentSession()).isNull();
        assertThat(Files.exists(Path.of(session.getWorktreePath()))).isFalse();
        assertThat(runGit(repoRoot, "show-ref", "--verify", "refs/heads/worktree-remove123").code()).isNotZero();
    }

    @Test
    void managerShouldRejectInvalidSlug() {
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build());
        assertThatThrownBy(() -> manager.enter("../bad", tempDir.toString(), "m", "t"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void managerShouldRejectNonGitRoot() throws Exception {
        OsTestSupport.assumeGitAvailable();
        Path nonRepo = tempDir.resolve("not-repo");
        Files.createDirectories(nonRepo);
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().build());

        assertThatThrownBy(() -> manager.enter("valid-slug", nonRepo.toString(), "m", "t"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not in a git repository");
    }

    @Test
    void cleanupStaleWorktreesShouldRemoveOnlyExpiredCleanEphemeralWorktrees() throws Exception {
        Path repoRoot = createGitRepoWithOrigin();
        Path wtBase = tempDir.resolve("cleanup-worktrees");
        WorktreeManager manager =
            new WorktreeManager(WorktreeConfig.builder().baseDir(wtBase.toString()).cleanupAfterDays(30).build());
        WorktreeSession expired = manager.enter("teammate-a1b2c3d4", repoRoot.toString(), "m1", "team");
        manager.exit("keep", false);
        WorktreeSession recent = manager.enter("teammate-b2c3d4e5", repoRoot.toString(), "m2", "team");
        manager.exit("keep", false);
        WorktreeSession named = manager.enter("feature-auth", repoRoot.toString(), "m3", "team");
        manager.exit("keep", false);
        Files.setLastModifiedTime(Path.of(expired.getWorktreePath()),
                FileTime.from(Instant.now().minus(Duration.ofDays(60))));
        Files.setLastModifiedTime(Path.of(named.getWorktreePath()),
                FileTime.from(Instant.now().minus(Duration.ofDays(60))));

        int removed = manager.cleanupStaleWorktrees(repoRoot.toString(), wtBase.toString(), null);

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(Path.of(expired.getWorktreePath()))).isFalse();
        assertThat(Files.exists(Path.of(recent.getWorktreePath()))).isTrue();
        assertThat(Files.exists(Path.of(named.getWorktreePath()))).isTrue();
    }

    @Test
    void cleanupStaleWorktreesShouldSkipDirtyAndCurrentWorktrees() throws Exception {
        Path repoRoot = createGitRepoWithOrigin();
        Path wtBase = tempDir.resolve("cleanup-skip-worktrees");
        WorktreeManager manager =
            new WorktreeManager(WorktreeConfig.builder().baseDir(wtBase.toString()).cleanupAfterDays(30).build());
        WorktreeSession dirty = manager.enter("teammate-c3d4e5f6", repoRoot.toString(), "m1", "team");
        manager.exit("keep", false);
        WorktreeSession current = manager.enter("agent-1234567", repoRoot.toString(), "m2", "team");
        manager.exit("keep", false);
        Files.writeString(Path.of(dirty.getWorktreePath()).resolve("dirty.txt"), "dirty");
        Files.setLastModifiedTime(Path.of(dirty.getWorktreePath()),
                FileTime.from(Instant.now().minus(Duration.ofDays(60))));
        Files.setLastModifiedTime(Path.of(current.getWorktreePath()),
                FileTime.from(Instant.now().minus(Duration.ofDays(60))));

        int removed = manager.cleanupStaleWorktrees(repoRoot.toString(), wtBase.toString(), current.getWorktreePath());

        assertThat(removed).isZero();
        assertThat(Files.exists(Path.of(dirty.getWorktreePath()))).isTrue();
        assertThat(Files.exists(Path.of(current.getWorktreePath()))).isTrue();
    }

    @Test
    void recoverWorktreeForMemberShouldRestoreExistingPersistentSession() throws Exception {
        Path repoRoot = createGitRepo();
        Path wtBase = tempDir.resolve("recover-worktrees");
        WorktreeManager manager = new WorktreeManager(WorktreeConfig.builder().baseDir(wtBase.toString())
                .lifecyclePolicy(WorktreeLifecyclePolicy.DURABLE).build());
        WorktreeSession created = manager.enter("teammate-abcdef12", repoRoot.toString(), "abcdef1234567890", "team-a");
        manager.exit("keep", false);

        WorktreeSession recovered = manager.recoverWorktreeForMember("abcdef1234567890", "team-a", repoRoot.toString());

        assertThat(recovered).isNotNull();
        assertThat(recovered.getOriginalCwd()).isEqualTo(repoRoot.toRealPath().toString());
        assertThat(recovered.getWorktreePath()).isEqualTo(created.getWorktreePath());
        assertThat(recovered.getWorktreeName()).isEqualTo("teammate-abcdef12");
        assertThat(recovered.getWorktreeBranch()).isEqualTo("worktree-teammate-abcdef12");
        assertThat(recovered.getOriginalHeadCommit()).hasSize(40);
        assertThat(recovered.getMemberName()).isEqualTo("abcdef1234567890");
        assertThat(recovered.getTeamName()).isEqualTo("team-a");
        assertThat(recovered.getLifecyclePolicy()).isEqualTo(WorktreeLifecyclePolicy.DURABLE);
    }

    @Test
    void recoverWorktreeForMemberShouldReturnNullWhenMissingOrOutsideGitRepo() throws Exception {
        Path repoRoot = createGitRepo();
        WorktreeManager manager = new WorktreeManager(
                WorktreeConfig.builder().baseDir(tempDir.resolve("missing-worktrees").toString()).build());

        assertThat(manager.recoverWorktreeForMember("abcdef1234567890", "team-a", repoRoot.toString())).isNull();

        Path nonRepo = tempDir.resolve("non-repo-recover");
        Files.createDirectories(nonRepo);
        assertThat(manager.recoverWorktreeForMember("abcdef1234567890", "team-a", nonRepo.toString())).isNull();
    }

    private Path createGitRepo() throws Exception {
        Path repoRoot = tempDir.resolve("repo-" + System.nanoTime());
        Files.createDirectories(repoRoot);
        // Avoid `git init -b` (requires Git >= 2.28); set branch name before first commit.
        runGitOrThrow(repoRoot, "init");
        runGitOrThrow(repoRoot, "symbolic-ref", "HEAD", "refs/heads/main");
        runGitOrThrow(repoRoot, "config", "user.email", "test@example.com");
        runGitOrThrow(repoRoot, "config", "user.name", "Test User");
        Files.writeString(repoRoot.resolve("README.md"), "hello\n");
        runGitOrThrow(repoRoot, "add", "README.md");
        runGitOrThrow(repoRoot, "commit", "-m", "initial");
        return repoRoot;
    }

    private Path createGitRepoWithOrigin() throws Exception {
        Path repoRoot = createGitRepo();
        Path remote = tempDir.resolve("remote-" + System.nanoTime() + ".git");
        runGitOrThrow(tempDir, "init", "--bare", remote.toString());
        runGitOrThrow(repoRoot, "remote", "add", "origin", remote.toString());
        runGitOrThrow(repoRoot, "push", "-u", "origin", "main");
        return repoRoot;
    }

    private static void runGitOrThrow(Path cwd, String... args) throws Exception {
        GitResult result = runGit(cwd, args);
        if (result.code() != 0) {
            throw new IllegalStateException(result.output());
        }
    }

    private static GitResult runGit(Path cwd, String... args) throws Exception {
        OsTestSupport.assumeGitAvailable();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        return new GitResult(code, new String(output, java.nio.charset.StandardCharsets.UTF_8));
    }

    private record GitResult(int code, String output) {
    }
}
