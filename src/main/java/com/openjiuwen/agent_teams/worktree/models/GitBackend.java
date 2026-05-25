/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree.models;

import com.openjiuwen.agent_teams.worktree.WorktreeConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Git backend for worktree operations.
 * <p>
 * Mirrors Python's {@code GitBackend} in agent_teams.worktree.models.
 * Provides git worktree create/remove operations.
 */
public class GitBackend {

    private final WorktreeConfig config;

    public GitBackend(WorktreeConfig config) {
        this.config = config;
    }

    public WorktreeConfig getConfig() {
        return config;
    }

    /**
     * Create a new git worktree.
     *
     * @param branch Branch name for the worktree
     * @param baseCommit Base commit to start from
     * @return CompletableFuture with the worktree path
     */
    public CompletableFuture<String> create(String branch, String baseCommit) {
        // Stub implementation - actual git operations would be implemented here
        return CompletableFuture.completedFuture("/mock/worktree/" + branch);
    }

    /**
     * Remove a git worktree.
     *
     * @param worktreePath Path to the worktree to remove
     * @param repoPath Path to the main repository
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> remove(String worktreePath, String repoPath) {
        // Stub implementation
        return CompletableFuture.completedFuture(true);
    }

    /**
     * List all worktrees.
     *
     * @param repoPath Path to the repository
     * @return CompletableFuture with list of worktree paths
     */
    public CompletableFuture<java.util.List<String>> list(String repoPath) {
        // Stub implementation
        return CompletableFuture.completedFuture(java.util.List.of());
    }

    /**
     * Prune stale worktree references.
     *
     * @param repoPath Path to the repository
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> prune(String repoPath) {
        // Stub implementation
        return CompletableFuture.completedFuture(true);
    }
}