/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Worktree lifecycle manager.
 * <p>
 * Coordinates worktree creation, removal, session state, post-creation
 * setup, event publishing, and rail dispatch. This is the single business
 * logic entry point -- tools and spawn code delegate here.
 * <p>
 * Mirrors Python's {@code WorktreeManager} in
 * {@code openjiuwen.agent_teams.worktree.manager}.
 */
public class WorktreeManager {

    private static final Logger logger = Logger.getLogger(WorktreeManager.class.getName());

    private final WorktreeConfig config;
    private final Messager messager;
    private final String workspaceRoot;
    private final WorktreeBackend backend;
    private final List<WorktreeRails> rails;
    private Consumer<WorktreeEvent> eventHandler;

    public WorktreeManager(WorktreeConfig config, Messager messager, String workspaceRoot) {
        this.config = config != null ? config : new WorktreeConfig();
        this.messager = messager;
        this.workspaceRoot = workspaceRoot;
        this.backend = new GitBackend();
        this.rails = new ArrayList<>();
    }

    public WorktreeManager(WorktreeConfig config, Messager messager, String workspaceRoot, 
                           WorktreeBackend backend, List<WorktreeRails> rails) {
        this.config = config != null ? config : new WorktreeConfig();
        this.messager = messager;
        this.workspaceRoot = workspaceRoot;
        this.backend = backend != null ? backend : new GitBackend();
        this.rails = rails != null ? new ArrayList<>(rails) : new ArrayList<>();
    }

    /**
     * Set an event handler for worktree events.
     *
     * @param handler Callback to receive worktree events
     */
    public void setEventHandler(Consumer<WorktreeEvent> handler) {
        this.eventHandler = handler;
    }

    // ── Session-level Worktree Operations ───────────────────────────────────────

    /**
     * Create or recover a worktree and enter it (synchronous).
     * <p>
     * Sets the ThreadLocal session. Called by EnterWorktreeTool.
     *
     * @param slug       Worktree name (validated for safety)
     * @param memberName Team member this worktree belongs to
     * @param teamName   Team this worktree belongs to
     * @return The active WorktreeSession
     */
    public WorktreeSession enter(String slug, String memberName, String teamName) {
        validateSlug(slug);

        String root = workspaceRoot != null ? workspaceRoot : System.getProperty("user.dir");
        String worktreeRoot = resolveWorktreeRoot(root);
        String worktreePath = Path.of(worktreeRoot, slug).toString();
        String branchName = "worktree-" + slug;

        long start = System.currentTimeMillis();
        WorktreeModels.WorktreeCreateResult result = backend.create(slug, root, worktreePath);
        long durationMs = System.currentTimeMillis() - start;

        WorktreeSession session = new WorktreeSession(
                root,
                worktreePath,
                teamName,
                memberName,
                slug,
                branchName,
                result.getHeadCommit()
        );
        // Note: creation duration and existed status could be stored in session if needed

        WorktreeSessionHolder.setCurrentSession(session);

        logger.info(String.format("Entered worktree '%s' at %s (created, %.0fms)",
                slug, worktreePath, (double) durationMs));

        publishEvent("worktree_created", teamName, Map.of(
                "worktree_name", slug,
                "worktree_path", worktreePath,
                "duration_ms", durationMs
        ));

        // Run post-creation hooks
        runAfterCreateHooks(session);

        return session;
    }

    /**
     * Create or recover a worktree and enter it (asynchronous).
     *
     * @param slug       Worktree name
     * @param memberName Team member name
     * @param teamName   Team name
     * @return CompletableFuture containing the WorktreeSession
     */
    public CompletableFuture<WorktreeSession> enterAsync(String slug, String memberName, String teamName) {
        return CompletableFuture.supplyAsync(() -> enter(slug, memberName, teamName));
    }

    /**
     * Remove the current worktree session (synchronous).
     *
     * @param force Whether to force removal even with uncommitted changes
     * @return true if removal succeeded
     */
    public boolean removeCurrent(boolean force) {
        WorktreeSession session = WorktreeSessionHolder.getCurrentSession();
        if (session == null) {
            return false;
        }

        // Run pre-exit hooks
        if (!runBeforeExitHooks(session)) {
            logger.info("Worktree removal cancelled by hook for: " + session.getSlug());
            return false;
        }

        // Detect changes before removal
        WorktreeChangeSummary changes = summarizeChanges();

        // Perform removal
        boolean removed = backend.remove(session.getWorktreePath(), force);

        if (removed) {
            publishEvent("worktree_removed", session.getTeamName(), Map.of(
                    "worktree_name", session.getSlug(),
                    "worktree_path", session.getWorktreePath(),
                    "force", force,
                    "had_changes", changes.isHasChanges()
            ));

            // Run post-exit hooks
            runAfterExitHooks(session.getSlug());

            WorktreeSessionHolder.setCurrentSession(null);
            logger.info("Removed worktree: " + session.getSlug());
        }

        return removed;
    }

    /**
     * Remove the current worktree session (asynchronous).
     *
     * @param force Whether to force removal
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> removeCurrentAsync(boolean force) {
        return CompletableFuture.supplyAsync(() -> removeCurrent(force));
    }

    /**
     * Create a worktree without entering it.
     *
     * @param slug   Worktree name
     * @param repoRoot Repository root path
     * @return WorktreeModels.WorktreeCreateResult
     */
    public WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot) {
        validateSlug(slug);
        String worktreeRoot = resolveWorktreeRoot(repoRoot);
        String worktreePath = Path.of(worktreeRoot, slug).toString();
        return backend.create(slug, repoRoot, worktreePath);
    }

    /**
     * Remove a worktree by path.
     *
     * @param worktreePath Path to the worktree
     * @param force        Whether to force removal
     * @return true if removal succeeded
     */
    public boolean remove(String worktreePath, boolean force) {
        return backend.remove(worktreePath, force);
    }

    // ── Change Detection ───────────────────────────────────────

    /**
     * Summarize changes in the current worktree.
     *
     * @return WorktreeChangeSummary
     */
    public WorktreeChangeSummary summarizeChanges() {
        WorktreeSession session = WorktreeSessionHolder.getCurrentSession();
        if (session == null) {
            return new WorktreeChangeSummary(false, 0, null);
        }

        // In a full implementation, this would check:
        // - Uncommitted changes (git status)
        // - Unpushed commits
        // - Changed files count
        return backend.detectChanges(session.getWorktreePath());
    }

    // ── Rail Hooks ───────────────────────────────────────

    /**
     * Add a rail for worktree lifecycle hooks.
     *
     * @param rail The rail to add
     */
    public void addRail(WorktreeRails rail) {
        rails.add(rail);
    }

    private void runAfterCreateHooks(WorktreeSession session) {
        // Convert to WorktreeModels.WorktreeSession for rail hooks
        WorktreeModels.WorktreeSession railSession = new WorktreeModels.WorktreeSession(
                session.getOriginalCwd(),
                session.getWorktreePath(),
                session.getSlug(),
                session.getBranchName(),
                null  // head commit not available in WorktreeSession
        );
        for (WorktreeRails rail : rails) {
            try {
                rail.afterWorktreeCreate(railSession);
            } catch (Exception e) {
                logger.warning("Rail hook afterCreate failed: " + e.getMessage());
            }
        }
    }

    private boolean runBeforeExitHooks(WorktreeSession session) {
        // Convert to WorktreeModels.WorktreeSession for rail hooks
        WorktreeModels.WorktreeSession railSession = new WorktreeModels.WorktreeSession(
                session.getOriginalCwd(),
                session.getWorktreePath(),
                session.getSlug(),
                session.getBranchName(),
                null
        );
        for (WorktreeRails rail : rails) {
            try {
                if (!rail.beforeWorktreeExit(railSession)) {
                    return false;
                }
            } catch (Exception e) {
                logger.warning("Rail hook beforeExit failed: " + e.getMessage());
            }
        }
        return true;
    }

    private void runAfterExitHooks(String slug) {
        for (WorktreeRails rail : rails) {
            try {
                rail.afterWorktreeExit(slug);
            } catch (Exception e) {
                logger.warning("Rail hook afterExit failed: " + e.getMessage());
            }
        }
    }

    // ── Helper Methods ───────────────────────────────────────

    /**
     * Validate a worktree slug for safety.
     *
     * @param slug The slug to validate
     * @throws IllegalArgumentException if slug is invalid
     */
    public static void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Worktree slug cannot be null or empty");
        }
        // Only allow alphanumeric, dash, underscore
        if (!slug.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid worktree slug: " + slug);
        }
        // Prevent path traversal
        if (slug.contains("..") || slug.contains("/") || slug.contains("\\")) {
            throw new IllegalArgumentException("Worktree slug contains invalid characters: " + slug);
        }
    }

    private String resolveWorktreeRoot(String repoRoot) {
        if (config.getBaseDir() != null && !config.getBaseDir().isBlank()) {
            return config.getBaseDir();
        }
        return Path.of(repoRoot, ".agent_teams", "worktrees").toString();
    }

    private void publishEvent(String eventType, String teamName, Map<String, Object> payload) {
        if (messager != null && teamName != null) {
            try {
                messager.publish(TeamTopic.TEAM.build("shared", teamName), new EventMessage(eventType, payload));
            } catch (Exception e) {
                logger.warning("Failed to publish event: " + e.getMessage());
            }
        }
        if (eventHandler != null) {
            eventHandler.accept(new WorktreeEvent(eventType, teamName, payload));
        }
    }

    // ── Accessors ───────────────────────────────────────

    public WorktreeConfig getConfig() {
        return config;
    }

    public WorktreeBackend getBackend() {
        return backend;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    // ── Worktree Event Class ───────────────────────────────────────

    /**
     * Simple worktree event class for event publishing.
     */
    public static class WorktreeEvent {
        private final String type;
        private final String teamName;
        private final Map<String, Object> payload;

        public WorktreeEvent(String type, String teamName, Map<String, Object> payload) {
            this.type = type;
            this.teamName = teamName;
            this.payload = payload;
        }

        public String getType() { return type; }
        public String getTeamName() { return teamName; }
        public Map<String, Object> getPayload() { return payload; }
    }

    // ── Backend Interface ───────────────────────────────────────

    /**
     * Backend interface for worktree operations.
     */
    public interface WorktreeBackend {
        WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot, String targetPath);
        boolean remove(String worktreePath, boolean force);
        WorktreeChangeSummary detectChanges(String worktreePath);
    }

    /**
     * Git-based worktree backend implementation.
     */
    public static class GitBackend implements WorktreeBackend {

        @Override
        public WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot, String targetPath) {
            // In a full implementation, this would:
            // 1. Run `git worktree add`
            // 2. Optionally set up sparse checkout
            // 3. Create symlinks for configured directories
            return WorktreeModels.WorktreeCreateResult.success(targetPath, "worktree-" + slug, null);
        }

        @Override
        public boolean remove(String worktreePath, boolean force) {
            // In a full implementation, this would:
            // 1. Run `git worktree remove` (or `git worktree prune`)
            // 2. Clean up the worktree directory
            return true;
        }

        @Override
        public WorktreeChangeSummary detectChanges(String worktreePath) {
            // In a full implementation, this would run `git status --porcelain`
            return new WorktreeChangeSummary(false, 0, null);
        }
    }
}
