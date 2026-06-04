/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.random.RandomGenerator;

/**
 * Worktree tools for entering and exiting git worktree sessions.
 *
 * <p>Provides enter/exit tool adapters that delegate lifecycle operations to
 * {@link WorktreeManager} and share session state through
 * {@link WorktreeSessionHolder}.</p>
 *
 * <p>Mirrors Python's {@code tools} module in
 * {@code openjiuwen.agent_teams.worktree.tools}.</p>
 */
public class WorktreeTools {

    private final WorktreeManager manager;

    public WorktreeTools(WorktreeManager manager) {
        this.manager = manager;
    }

    /**
     * Create or enter an isolated git worktree.
     */
    public CompletableFuture<ToolOutput> enterWorktree(Map<String, Object> inputs, String memberName, String teamName) {
        WorktreeSession existing = getCurrentSession();
        if (existing != null) {
            return CompletableFuture.completedFuture(new ToolOutput(false,
                "Already in worktree '" + existing.getSlug() + "'. Exit first with exit_worktree.",
                null
            ));
        }

        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
        String slug = readString(safeInputs, "name");
        if (slug == null || slug.isBlank()) {
            slug = generateRandomSlug();
        }

        try {
            SlugUtils.validateSlug(slug);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(new ToolOutput(false, e.getMessage(), null));
        }

        try {
            WorktreeSession session = manager.enter(slug, memberName, teamName);
            setCurrentSession(session);
            return CompletableFuture.completedFuture(new ToolOutput(true, null, dataOf(
                "worktree_path", session.getWorktreePath(),
                "worktree_branch", session.getBranchName(),
                "message", "Created worktree at " + session.getWorktreePath()
                    + " on branch " + session.getBranchName() + ". CWD switched to worktree."
            )));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(
                new ToolOutput(false, "Failed to create worktree: " + e.getMessage(), null)
            );
        }
    }

    /**
     * Exit and optionally remove a git worktree.
     */
    public CompletableFuture<ToolOutput> exitWorktree(Map<String, Object> inputs) {
        WorktreeSession session = getCurrentSession();
        if (session == null) {
            return CompletableFuture.completedFuture(new ToolOutput(false, "No active worktree session to exit.", null));
        }

        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
        String action = readString(safeInputs, "action");
        boolean cleanup = readBoolean(safeInputs, "cleanup");
        if (action == null || action.isBlank()) {
            action = cleanup ? "remove" : "keep";
        }
        if (!"keep".equals(action) && !"remove".equals(action)) {
            return CompletableFuture.completedFuture(new ToolOutput(false, "'action' must be 'keep' or 'remove'.", null));
        }

        boolean discard = readBoolean(safeInputs, "discard_changes")
            || readBoolean(safeInputs, "discardChanges")
            || cleanup;

        try {
            WorktreeChangeSummary summary = null;
            if ("remove".equals(action)) {
                summary = manager.summarizeChanges();
                if (!discard && summary != null && (summary.isHasChanges() || summary.getAheadCount() > 0)) {
                    return CompletableFuture.completedFuture(new ToolOutput(false,
                        "Worktree has changes. Set discard_changes=true to proceed.",
                        null
                    ));
                }
                if (!removeWorktree(discard)) {
                    return CompletableFuture.completedFuture(new ToolOutput(false, "Failed to remove worktree.", null));
                }
            } else {
                clearCurrentSession();
            }

            restoreOriginalCwd(session.getOriginalCwd());
            String branch = session.getBranchName() != null ? session.getBranchName() : "unknown";
            String message = "keep".equals(action)
                ? "Kept worktree (branch " + branch + "). Returned to " + session.getOriginalCwd()
                : "Removed worktree (branch " + branch + "). Returned to " + session.getOriginalCwd();
            Map<String, Object> data = dataOf(
                "action", action,
                "original_cwd", session.getOriginalCwd(),
                "worktree_path", session.getWorktreePath(),
                "worktree_branch", session.getBranchName(),
                "message", message
            );
            if (summary != null && discard) {
                data.put("discarded_files", summary.isHasChanges() ? 1 : 0);
                data.put("discarded_commits", summary.getAheadCount());
            }
            return CompletableFuture.completedFuture(new ToolOutput(true, null, data));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(
                new ToolOutput(false, "Failed to exit worktree: " + e.getMessage(), null)
            );
        }
    }

    private String generateRandomSlug() {
        String[] adjectives = {"swift", "bright", "calm", "keen", "bold"};
        String[] nouns = {"fox", "owl", "elm", "oak", "ray"};
        RandomGenerator rand = RandomGenerator.getDefault();
        String adj = adjectives[rand.nextInt(adjectives.length)];
        String noun = nouns[rand.nextInt(nouns.length)];
        String suffix = String.format("%04x", rand.nextInt(0x10000));
        return adj + "-" + noun + "-" + suffix;
    }

    private WorktreeSession getCurrentSession() {
        return WorktreeSessionHolder.getCurrentSession();
    }

    private void setCurrentSession(WorktreeSession session) {
        WorktreeSessionHolder.setCurrentSession(session);
    }

    private boolean removeWorktree(boolean discardChanges) {
        return manager.removeCurrent(discardChanges);
    }

    private void clearCurrentSession() {
        WorktreeSessionHolder.setCurrentSession(null);
    }

    private void restoreOriginalCwd(String cwd) {
        // Java has no process-local chdir. The restored cwd is reported in ToolOutput,
        // and callers with CwdState should update that context from the returned data.
    }

    private static String readString(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean readBoolean(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> dataOf(Object... keysAndValues) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                data.put(String.valueOf(keysAndValues[i]), value);
            }
        }
        return data;
    }

    /**
     * Tool output.
     */
    public static class ToolOutput {
        private final boolean success;
        private final String error;
        private final Map<String, Object> data;

        public ToolOutput(boolean success, String error, Map<String, Object> data) {
            this.success = success;
            this.error = error;
            this.data = data != null ? data : new HashMap<>();
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}
