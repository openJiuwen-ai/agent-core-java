/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Worktree tools for entering and exiting git worktree sessions.
 * <p>
 * Provides EnterWorktreeTool and ExitWorktreeTool implementations.
 * Both delegate to WorktreeManager for actual worktree lifecycle operations.
 * <p>
 * Mirrors Python's {@code tools} module in
 * {@code openjiuwen.agent_teams.worktree.tools}.
 */
public class WorktreeTools {

    private static final Logger logger = Logger.getLogger(WorktreeTools.class.getName());

    private final WorktreeManager manager;

    public WorktreeTools(WorktreeManager manager) {
        this.manager = manager;
    }

    // ── EnterWorktreeTool ───────────────────────────────────────

    /**
     * Create or enter an isolated git worktree.
     */
    public CompletableFuture<ToolOutput> enterWorktree(Map<String, Object> inputs, String memberName, String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            // Check existing session
            WorktreeModels.WorktreeSession existing = getCurrentSession();
            if (existing != null) {
                return new ToolOutput(true, null, Map.of(
                    "worktree_path", existing.getWorktreeCwd(),
                    "worktree_branch", existing.getBranchName(),
                    "message", "Already in worktree: " + existing.getSlug()
                ));
            }

            // Get or generate slug
            String slug = (String) inputs.get("name");
            if (slug == null || slug.isEmpty()) {
                slug = generateRandomSlug();
            }

            // Validate slug
            if (!validateSlug(slug)) {
                return new ToolOutput(false, "Invalid worktree name: " + slug, null);
            }

            try {
                // Create worktree
                WorktreeModels.WorktreeCreateResult result = manager.createWorktree(slug, memberName, teamName);
                if (!result.isSuccess()) {
                    return new ToolOutput(false, result.getError(), null);
                }

                // Set session
                setCurrentSession(new WorktreeModels.WorktreeSession(
                    manager.getOriginalCwd(),
                    result.getWorktreePath(),
                    slug,
                    result.getBranchName(),
                    result.getHeadCommit()
                ));

                return new ToolOutput(true, null, Map.of(
                    "worktree_path", result.getWorktreePath(),
                    "worktree_branch", result.getBranchName(),
                    "message", "Entered worktree: " + slug
                ));
            } catch (Exception e) {
                return new ToolOutput(false, "Failed to enter worktree: " + e.getMessage(), null);
            }
        });
    }

    // ── ExitWorktreeTool ───────────────────────────────────────

    /**
     * Exit and optionally remove a git worktree.
     */
    public CompletableFuture<ToolOutput> exitWorktree(Map<String, Object> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            WorktreeModels.WorktreeSession session = getCurrentSession();
            if (session == null) {
                return new ToolOutput(false, "Not in a worktree", null);
            }

            String slug = session.getSlug();
            boolean cleanup = Boolean.TRUE.equals(inputs.get("cleanup"));

            try {
                // Restore original cwd
                manager.restoreOriginalCwd(session.getOriginalCwd());

                // Optionally cleanup worktree
                if (cleanup) {
                    manager.removeWorktree(slug);
                }

                // Clear session
                clearCurrentSession();

                return new ToolOutput(true, null, Map.of(
                    "message", "Exited worktree: " + slug + (cleanup ? " (removed)" : "")
                ));
            } catch (Exception e) {
                return new ToolOutput(false, "Failed to exit worktree: " + e.getMessage(), null);
            }
        });
    }

    // ── Helper methods ───────────────────────────────────────

    private String generateRandomSlug() {
        String[] adjectives = {"swift", "bright", "calm", "keen", "bold"};
        String[] nouns = {"fox", "owl", "elm", "oak", "ray"};
        RandomGenerator rand = RandomGenerator.getDefault();
        String adj = adjectives[rand.nextInt(adjectives.length)];
        String noun = nouns[rand.nextInt(nouns.length)];
        String suffix = Integer.toHexString(rand.nextInt(256));
        return adj + "-" + noun + "-" + suffix;
    }

    private boolean validateSlug(String slug) {
        if (slug == null || slug.isEmpty()) return false;
        // Must be alphanumeric with hyphens, max 32 chars
        return slug.matches("^[a-z0-9-]+$") && slug.length() <= 32;
    }

    private WorktreeModels.WorktreeSession getCurrentSession() {
        // Placeholder: get from context
        return null;
    }

    private void setCurrentSession(WorktreeModels.WorktreeSession session) {
        // Placeholder: set in context
    }

    private void clearCurrentSession() {
        // Placeholder: clear from context
    }

    // ── Tool output ───────────────────────────────────────

    public static class ToolOutput {
        private final boolean success;
        private final String error;
        private final Map<String, Object> data;

        public ToolOutput(boolean success, String error, Map<String, Object> data) {
            this.success = success;
            this.error = error;
            this.data = data != null ? data : new HashMap<>();
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public Map<String, Object> getData() { return data; }
    }

    // ── WorktreeManager stub ───────────────────────────────────────

    /**
     * Worktree manager interface (stub).
     */
    public static class WorktreeManager {
        public WorktreeModels.WorktreeCreateResult createWorktree(String slug, String memberName, String teamName) {
            // Placeholder
            return WorktreeModels.WorktreeCreateResult.failure("Not implemented");
        }
        public void removeWorktree(String slug) {
            // Placeholder
        }
        public String getOriginalCwd() {
            return System.getProperty("user.dir");
        }
        public void restoreOriginalCwd(String cwd) {
            // Placeholder
        }
    }
}