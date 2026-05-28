/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Distributed worktree backend for remote nodes.
 * <p>
 * Enables worktree isolation across machines: the leader sends worktree
 * lifecycle requests via Messager; each remote node maintains its own
 * shallow clone and creates local worktrees within it.
 * <p>
 * Mirrors Python's {@code remote} module in
 * {@code openjiuwen.agent_teams.worktree.remote}.
 */
public class WorktreeRemote {

    private static final Logger logger = Logger.getLogger(WorktreeRemote.class.getName());

    // ── Request / Response models ───────────────────────────────────────

    /**
     * Request sent to a remote node to manage a worktree.
     */
    public static class WorktreeRemoteRequest {
        private String action;  // "create", "remove", or "exists"
        private String slug;
        private String repoUrl;
        private String baseBranch;
        private String worktreePath;
        private WorktreeModels.WorktreeConfig config;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getRepoUrl() { return repoUrl; }
        public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
        public String getBaseBranch() { return baseBranch; }
        public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
        public String getWorktreePath() { return worktreePath; }
        public void setWorktreePath(String worktreePath) { this.worktreePath = worktreePath; }
        public WorktreeModels.WorktreeConfig getConfig() { return config; }
        public void setConfig(WorktreeModels.WorktreeConfig config) { this.config = config; }
    }

    /**
     * Response from a remote node after a worktree operation.
     */
    public static class WorktreeRemoteResponse {
        private boolean success;
        private String worktreePath;
        private String worktreeBranch;
        private String headCommit;
        private boolean existed;
        private boolean exists;
        private String error;

        public static WorktreeRemoteResponse success(String path, String branch, String commit) {
            WorktreeRemoteResponse r = new WorktreeRemoteResponse();
            r.success = true;
            r.worktreePath = path;
            r.worktreeBranch = branch;
            r.headCommit = commit;
            return r;
        }

        public static WorktreeRemoteResponse failure(String error) {
            WorktreeRemoteResponse r = new WorktreeRemoteResponse();
            r.success = false;
            r.error = error;
            return r;
        }

        public static WorktreeRemoteResponse existed(String path) {
            WorktreeRemoteResponse r = new WorktreeRemoteResponse();
            r.existed = true;
            r.worktreePath = path;
            return r;
        }

        public boolean isSuccess() { return success; }
        public String getWorktreePath() { return worktreePath; }
        public String getWorktreeBranch() { return worktreeBranch; }
        public String getHeadCommit() { return headCommit; }
        public boolean getExisted() { return existed; }
        public boolean getExists() { return exists; }
        public String getError() { return error; }
    }

    // ── Remote backend operations ───────────────────────────────────────

    /**
     * Create a worktree on a remote node.
     *
     * @param request Creation request with slug and repo URL
     * @return Response with worktree path and branch
     */
    public CompletableFuture<WorktreeRemoteResponse> createRemoteWorktree(WorktreeRemoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure repo is cloned
                String cloneDir = ensureClone(request.getRepoUrl());
                
                // Create worktree in clone
                String branch = request.getBaseBranch() != null ? request.getBaseBranch() : "main";
                String worktreePath = createWorktreeInClone(cloneDir, request.getSlug(), branch);
                
                logger.info("Created remote worktree: " + worktreePath);
                return WorktreeRemoteResponse.success(worktreePath, branch, getHeadCommit(worktreePath));
            } catch (Exception e) {
                logger.warning("Failed to create remote worktree: " + e.getMessage());
                return WorktreeRemoteResponse.failure(e.getMessage());
            }
        });
    }

    /**
     * Remove a worktree on a remote node.
     *
     * @param request Removal request with worktree path
     * @return Response indicating success or failure
     */
    public CompletableFuture<WorktreeRemoteResponse> removeRemoteWorktree(WorktreeRemoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                removeWorktree(request.getWorktreePath());
                logger.info("Removed remote worktree: " + request.getWorktreePath());
                return WorktreeRemoteResponse.success(null, null, null);
            } catch (Exception e) {
                return WorktreeRemoteResponse.failure(e.getMessage());
            }
        });
    }

    /**
     * Check if a worktree exists on a remote node.
     *
     * @param request Query request with worktree path
     * @return Response indicating whether it exists
     */
    public WorktreeRemoteResponse checkWorktreeExists(WorktreeRemoteRequest request) {
        boolean exists = java.nio.file.Files.exists(java.nio.file.Path.of(request.getWorktreePath()));
        WorktreeRemoteResponse r = new WorktreeRemoteResponse();
        r.exists = exists;
        return r;
    }

    // ── Git helper stubs ───────────────────────────────────────

    private String ensureClone(String repoUrl) {
        // Placeholder: git clone if not exists
        return null;
    }

    private String createWorktreeInClone(String cloneDir, String slug, String branch) {
        // Placeholder: git worktree add
        return null;
    }

    private String getHeadCommit(String worktreePath) {
        // Placeholder: git rev-parse HEAD
        return null;
    }

    private void removeWorktree(String worktreePath) {
        // Placeholder: git worktree remove
    }
}