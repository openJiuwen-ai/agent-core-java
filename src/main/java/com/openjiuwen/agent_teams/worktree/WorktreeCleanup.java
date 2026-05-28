/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

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

    // Ephemeral worktree naming patterns
    private static final Pattern[] EPHEMERAL_PATTERNS = {
        Pattern.compile("^teammate-[0-9a-f]{8}$"),
        Pattern.compile("^agent-[0-9a-f]{7}$")
    };

    /**
     * Check if a slug matches ephemeral worktree naming patterns.
     *
     * @param slug Worktree slug to check
     * @return true if the slug matches any ephemeral pattern
     */
    public static boolean isEphemeralSlug(String slug) {
        if (slug == null) return false;
        for (Pattern p : EPHEMERAL_PATTERNS) {
            if (p.matcher(slug).matches()) {
                return true;
            }
        }
        return false;
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
     * @param config             Worktree configuration with cleanup_after_days
     * @param currentWorktreePath Path of the active worktree to skip
     * @return Number of worktrees removed
     */
    public CompletableFuture<Integer> cleanupStaleWorktrees(
            WorktreeConfig config,
            String currentWorktreePath) {
        return CompletableFuture.supplyAsync(() -> {
            int removed = 0;
            try {
                // Find canonical git root
                String repoRoot = findCanonicalGitRoot();
                if (repoRoot == null) {
                    return 0;
                }

                // Get worktrees directory
                String worktreesDir = getWorktreesDir();
                if (worktreesDir == null) {
                    return 0;
                }

                // List worktree entries
                java.nio.file.Path wtDirPath = java.nio.file.Path.of(worktreesDir);
                if (!java.nio.file.Files.exists(wtDirPath)) {
                    return 0;
                }

                // Calculate cutoff time
                long cutoffMs = System.currentTimeMillis() - 
                    (config.getCleanupAfterDays() * 24L * 60 * 60 * 1000);

                // Iterate and cleanup
                try (var stream = java.nio.file.Files.list(wtDirPath)) {
                    for (java.nio.file.Path entry : stream.toList()) {
                        String slug = entry.getFileName().toString();
                        
                        // Check ephemeral pattern
                        if (!isEphemeralSlug(slug)) {
                            continue;
                        }
                        
                        // Skip current worktree
                        String wtPath = entry.toString();
                        if (currentWorktreePath != null && wtPath.equals(currentWorktreePath)) {
                            continue;
                        }
                        
                        // Check age
                        long lastModified = java.nio.file.Files.getLastModifiedTime(entry).toMillis();
                        if (lastModified > cutoffMs) {
                            continue;  // Not old enough
                        }
                        
                        // Safety checks: no uncommitted changes, no unpushed commits
                        if (hasUncommittedChanges(wtPath) || hasUnpushedCommits(wtPath, repoRoot)) {
                            logger.info("Skipping worktree " + slug + " - safety check failed");
                            continue;
                        }
                        
                        // Remove worktree
                        worktreePrune(wtPath);
                        removed++;
                        logger.info("Cleaned up stale worktree: " + slug);
                    }
                }
            } catch (Exception e) {
                logger.warning("Cleanup failed: " + e.getMessage());
            }
            return removed;
        });
    }

    // ── Git helper stubs ───────────────────────────────────────

    private String findCanonicalGitRoot() {
        // Placeholder: git rev-parse --show-toplevel
        return null;
    }

    private String getWorktreesDir() {
        // Placeholder: <workspace>/.agent_teams/worktrees
        return null;
    }

    private boolean hasUncommittedChanges(String worktreePath) {
        // Placeholder: git status --porcelain
        return false;
    }

    private boolean hasUnpushedCommits(String worktreePath, String repoRoot) {
        // Placeholder: git rev-list @{u}..HEAD
        return false;
    }

    private void worktreePrune(String worktreePath) {
        // Placeholder: git worktree prune
    }
}