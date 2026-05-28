/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Worktree rail base class with lifecycle hooks.
 * <p>
 * Provides hooks for worktree lifecycle events:
 * create, exit, file write, commit, and sync phases.
 * <p>
 * Mirrors Python's {@code rails} module in
 * {@code openjiuwen.agent_teams.worktree.rails}.
 */
public class WorktreeRails {

    private static final Logger logger = Logger.getLogger(WorktreeRails.class.getName());

    /**
     * Called before worktree creation.
     * <p>
     * Can return a modified slug, or null to proceed unchanged.
     * Raise to abort creation.
     *
     * @param slug      Proposed worktree slug
     * @param repoRoot  Absolute path to the repository root
     * @return Modified slug string, or null to keep original
     */
    public String beforeWorktreeCreate(String slug, String repoRoot) {
        return null;  // Default: no modification
    }

    /**
     * Called after worktree creation and post-setup.
     * <p>
     * Use for: dependency installation, setup scripts,
     * environment validation, workspace initialization.
     *
     * @param session The newly created worktree session
     */
    public CompletableFuture<Void> afterWorktreeCreate(WorktreeModels.WorktreeSession session) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Called before worktree exit.
     * <p>
     * Can return false to abort exit.
     *
     * @param session Current worktree session
     * @return true to proceed with exit, false to abort
     */
    public boolean beforeWorktreeExit(WorktreeModels.WorktreeSession session) {
        return true;  // Default: proceed
    }

    /**
     * Called after worktree exit and cleanup.
     *
     * @param slug The slug of the removed worktree
     */
    public void afterWorktreeExit(String slug) {
        // Default: no action
    }

    /**
     * Called before a file is written in the worktree.
     * <p>
     * Use for: access control, write logging, pre-write transforms.
     *
     * @param filePath  Path being written (relative to worktree root)
     * @param content   Content being written
     * @return Modified content, or original to proceed unchanged
     */
    public String beforeFileWrite(String filePath, String content) {
        return content;  // Default: no modification
    }

    /**
     * Called after a file is written in the worktree.
     *
     * @param filePath Path that was written
     */
    public void afterFileWrite(String filePath) {
        // Default: no action
    }

    /**
     * Called before git commit in the worktree.
     * <p>
     * Use for: commit message linting, CI triggers.
     *
     * @param message Proposed commit message
     * @return Modified message, or original to proceed unchanged
     */
    public String beforeCommit(String message) {
        return message;  // Default: no modification
    }

    /**
     * Called after git commit in the worktree.
     *
     * @param message Commit message that was used
     */
    public void afterCommit(String message) {
        // Default: no action
    }

    /**
     * Called during worktree-workspace sync for each file.
     * <p>
     * Use for: file filtering, content transforms.
     *
     * @param relativePath Path relative to workspace root
     * @param content      File content
     * @return true to include in sync, false to skip
     */
    public boolean filterSyncFile(String relativePath, String content) {
        return true;  // Default: include all
    }
}