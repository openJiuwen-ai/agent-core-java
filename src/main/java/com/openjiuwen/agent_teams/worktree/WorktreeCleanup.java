/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.models.GitBackend;
import com.openjiuwen.agent_teams.worktree.models.WorktreeBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Stale worktree cleanup.
 * <p>
 * Identifies and removes expired ephemeral worktrees using fail-closed
 * safety checks: only removes worktrees matching ephemeral naming patterns,
 * with no uncommitted changes and no unpushed commits.
 * <p>
 * Mirrors Python's {@code cleanup} module in
 * {@code openjiuwen.agent_teams.worktree.cleanup}.
 */
public class WorktreeCleanup {

    private static final Logger logger = Logger.getLogger(WorktreeCleanup.class.getName());

    private static final Pattern[] EPHEMERAL_PATTERNS = {
        Pattern.compile("^teammate-[0-9a-f]{8}$"),
        Pattern.compile("^agent-[0-9a-f]{7}$")
    };

    private final GitProbe gitProbe;

    public WorktreeCleanup() {
        this(new DefaultGitProbe());
    }

    public WorktreeCleanup(GitProbe gitProbe) {
        this.gitProbe = gitProbe != null ? gitProbe : new DefaultGitProbe();
    }

    /**
     * Injectable facade for git safety checks.
     */
    public interface GitProbe {
        CompletableFuture<String> findCanonicalGitRoot(String cwd);

        CompletableFuture<List<String>> statusPorcelain(String cwd);

        CompletableFuture<Boolean> hasUnpushedCommits(String cwd);

        CompletableFuture<Void> worktreePrune(String repoRoot);
    }

    /**
     * Check if a slug matches ephemeral worktree naming patterns.
     *
     * @param slug Worktree slug to check
     * @return true if the slug matches any ephemeral pattern
     */
    public static boolean isEphemeralSlug(String slug) {
        if (slug == null) {
            return false;
        }
        for (Pattern pattern : EPHEMERAL_PATTERNS) {
            if (pattern.matcher(slug).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compatibility entry point for existing Java callers.
     *
     * @param config Worktree configuration with cleanupAfterDays
     * @param currentWorktreePath Path of the active worktree to skip
     * @return Number of worktrees removed
     */
    public CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            String currentWorktreePath) {
        if (config == null) {
            return CompletableFuture.completedFuture(0);
        }
        return cleanupStaleWorktrees(
                config,
                new GitBackend(config),
                currentWorktreePath,
                System.getProperty("user.dir"),
                null);
    }

    /**
     * Clean up expired ephemeral worktrees.
     *
     * @param config Worktree configuration with cleanupAfterDays
     * @param backend Backend used to remove worktrees
     * @return Number of worktrees removed
     */
    public CompletableFuture<Integer> cleanupStaleWorktrees(WorktreeConfig config, WorktreeBackend backend) {
        return cleanupStaleWorktrees(config, backend, null);
    }

    /**
     * Clean up expired ephemeral worktrees.
     *
     * @param config Worktree configuration with cleanupAfterDays
     * @param backend Backend used to remove worktrees
     * @param currentWorktreePath Path of the active worktree to skip
     * @return Number of worktrees removed
     */
    public CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            WorktreeBackend backend,
            String currentWorktreePath) {
        return cleanupStaleWorktrees(
                config,
                backend,
                currentWorktreePath,
                System.getProperty("user.dir"),
                null);
    }

    /**
     * Clean up expired ephemeral worktrees.
     * <p>
     * Safety strategy (fail-closed):
     * 1. Only clean worktrees matching ephemeral patterns
     * 2. Skip the current session's worktree
     * 3. Check for uncommitted changes (git status)
     * 4. Check for unpushed commits (git rev-list)
     * 5. Skip on any check failure
     *
     * @param config Worktree configuration with cleanupAfterDays
     * @param backend Backend used to remove worktrees
     * @param currentWorktreePath Path of the active worktree to skip
     * @param cwd Current working directory used to locate the canonical git root
     * @param workspaceRoot Agent workspace root containing the .worktrees directory
     * @return Number of worktrees removed
     */
    public CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            WorktreeBackend backend,
            String currentWorktreePath,
            String cwd,
            String workspaceRoot) {
        if (config == null || backend == null) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> cleanupStaleWorktreesSync(
                config,
                backend,
                currentWorktreePath,
                cwd,
                workspaceRoot));
    }

    private int cleanupStaleWorktreesSync(
            WorktreeConfig config,
            WorktreeBackend backend,
            String currentWorktreePath,
            String cwd,
            String workspaceRoot) {
        String repoRoot = gitProbe.findCanonicalGitRoot(cwd).join();
        if (repoRoot == null || repoRoot.isBlank()) {
            return 0;
        }
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return 0;
        }

        Path worktreesDir = Path.of(SlugUtils.worktreesDir(workspaceRoot));
        if (!Files.exists(worktreesDir)) {
            return 0;
        }

        int removed = 0;
        long cutoffMs = System.currentTimeMillis() - Duration.ofDays(config.getCleanupAfterDays()).toMillis();
        try (var stream = Files.list(worktreesDir)) {
            for (Path entry : stream.toList()) {
                String slug = entry.getFileName().toString();
                if (!isEphemeralSlug(slug)) {
                    continue;
                }

                String worktreePath = entry.toString();
                if (isSamePath(worktreePath, currentWorktreePath)) {
                    continue;
                }

                long lastModified = Files.getLastModifiedTime(entry).toMillis();
                if (lastModified >= cutoffMs) {
                    continue;
                }

                if (!isSafeToRemove(worktreePath)) {
                    logger.info("Skipping worktree " + slug + " - safety check failed");
                    continue;
                }

                if (Boolean.TRUE.equals(backend.remove(worktreePath, repoRoot).join())) {
                    removed++;
                    logger.info("Cleaned up stale worktree: " + slug);
                }
            }
        } catch (Exception e) {
            logger.warning("Cleanup failed: " + e.getMessage());
        }

        if (removed > 0) {
            gitProbe.worktreePrune(repoRoot).join();
        }
        return removed;
    }

    private boolean isSafeToRemove(String worktreePath) {
        try {
            CompletableFuture<List<String>> changesFuture = gitProbe.statusPorcelain(worktreePath);
            CompletableFuture<Boolean> unpushedFuture = gitProbe.hasUnpushedCommits(worktreePath);
            CompletableFuture.allOf(changesFuture, unpushedFuture).join();

            List<String> changes = changesFuture.join();
            Boolean unpushed = unpushedFuture.join();
            return changes != null && changes.isEmpty() && Boolean.FALSE.equals(unpushed);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSamePath(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            return Objects.equals(
                    Path.of(left).toAbsolutePath().normalize(),
                    Path.of(right).toAbsolutePath().normalize());
        } catch (Exception e) {
            return left.equals(right);
        }
    }

    private static class DefaultGitProbe implements GitProbe {
        @Override
        public CompletableFuture<String> findCanonicalGitRoot(String cwd) {
            return Git.findCanonicalGitRoot(cwd);
        }

        @Override
        public CompletableFuture<List<String>> statusPorcelain(String cwd) {
            return Git.statusPorcelain(cwd);
        }

        @Override
        public CompletableFuture<Boolean> hasUnpushedCommits(String cwd) {
            return Git.hasUnpushedCommits(cwd);
        }

        @Override
        public CompletableFuture<Void> worktreePrune(String repoRoot) {
            return Git.worktreePrune(repoRoot);
        }
    }
}
