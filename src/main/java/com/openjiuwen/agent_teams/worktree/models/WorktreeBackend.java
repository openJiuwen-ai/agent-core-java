/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree.models;

import com.openjiuwen.agent_teams.worktree.WorktreeCreateResult;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for worktree backend operations.
 * <p>
 * Mirrors Python's {@code WorktreeBackend} interface in agent_teams.worktree.models.
 */
public interface WorktreeBackend {

    /**
     * Create a new worktree.
     *
     * @param slug Unique identifier/slug for the worktree
     * @param repoPath Path to the repository
     * @param target Target branch or commit
     * @return CompletableFuture with creation result
     */
    CompletableFuture<WorktreeCreateResult> create(String slug, String repoPath, String target);

    /**
     * Remove an existing worktree.
     *
     * @param worktreePath Path to the worktree
     * @param repoPath Path to the repository
     * @return CompletableFuture indicating success
     */
    CompletableFuture<Boolean> remove(String worktreePath, String repoPath);

    /**
     * Check if a worktree exists.
     *
     * @param worktreePath Path to the worktree
     * @return CompletableFuture indicating existence
     */
    CompletableFuture<Boolean> exists(String worktreePath);
}