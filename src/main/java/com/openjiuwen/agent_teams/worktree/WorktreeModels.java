/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.List;

/**
 * Worktree data models.
 * <p>
 * Models for worktree configuration, session state, creation results,
 * and change summaries.
 * <p>
 * Mirrors Python's {@code models} module in
 * {@code openjiuwen.agent_teams.worktree.models}.
 */
public class WorktreeModels {

    /**
     * How worktree lifecycle binds to team lifecycle.
     */
    public enum WorktreeLifecyclePolicy {
        /** Infer from TeamLifecycle (temporary -> ephemeral, persistent -> durable) */
        AUTO("auto"),
        /** Always auto-cleanup on member shutdown */
        EPHEMERAL("ephemeral"),
        /** Always preserve across sessions */
        DURABLE("durable");

        private final String value;
        WorktreeLifecyclePolicy(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    /**
     * Worktree configuration declared in TeamAgentSpec.
     */
    public static class WorktreeConfig {
        private boolean enabled;
        private String baseDir;
        private List<String> sparsePaths;
        private List<String> symlinkDirectories;
        private List<String> includePatterns;
        private int cleanupAfterDays;
        private boolean autoCleanupOnShutdown;
        private WorktreeLifecyclePolicy lifecyclePolicy;

        public WorktreeConfig() {
            this.enabled = false;
            this.cleanupAfterDays = 30;
            this.autoCleanupOnShutdown = true;
            this.lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;
        }

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseDir() { return baseDir; }
        public void setBaseDir(String baseDir) { this.baseDir = baseDir; }
        public List<String> getSparsePaths() { return sparsePaths; }
        public void setSparsePaths(List<String> sparsePaths) { this.sparsePaths = sparsePaths; }
        public List<String> getSymlinkDirectories() { return symlinkDirectories; }
        public void setSymlinkDirectories(List<String> symlinkDirectories) { this.symlinkDirectories = symlinkDirectories; }
        public List<String> getIncludePatterns() { return includePatterns; }
        public void setIncludePatterns(List<String> includePatterns) { this.includePatterns = includePatterns; }
        public int getCleanupAfterDays() { return cleanupAfterDays; }
        public void setCleanupAfterDays(int cleanupAfterDays) { this.cleanupAfterDays = cleanupAfterDays; }
        public boolean isAutoCleanupOnShutdown() { return autoCleanupOnShutdown; }
        public void setAutoCleanupOnShutdown(boolean autoCleanupOnShutdown) { this.autoCleanupOnShutdown = autoCleanupOnShutdown; }
        public WorktreeLifecyclePolicy getLifecyclePolicy() { return lifecyclePolicy; }
        public void setLifecyclePolicy(WorktreeLifecyclePolicy lifecyclePolicy) { this.lifecyclePolicy = lifecyclePolicy; }
    }

    /**
     * Runtime state of an active worktree.
     */
    public static class WorktreeSession {
        private String originalCwd;
        private String worktreeCwd;
        private String slug;
        private String branchName;
        private String headCommit;
        private long createdAt;

        public WorktreeSession() {}

        public WorktreeSession(String originalCwd, String worktreeCwd, String slug, String branchName, String headCommit) {
            this.originalCwd = originalCwd;
            this.worktreeCwd = worktreeCwd;
            this.slug = slug;
            this.branchName = branchName;
            this.headCommit = headCommit;
            this.createdAt = System.currentTimeMillis();
        }

        // Getters and setters
        public String getOriginalCwd() { return originalCwd; }
        public void setOriginalCwd(String originalCwd) { this.originalCwd = originalCwd; }
        public String getWorktreeCwd() { return worktreeCwd; }
        public void setWorktreeCwd(String worktreeCwd) { this.worktreeCwd = worktreeCwd; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getBranchName() { return branchName; }
        public void setBranchName(String branchName) { this.branchName = branchName; }
        public String getHeadCommit() { return headCommit; }
        public void setHeadCommit(String headCommit) { this.headCommit = headCommit; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * Result of worktree creation.
     */
    public static class WorktreeCreateResult {
        private boolean success;
        private String worktreePath;
        private String branchName;
        private String headCommit;
        private String error;

        public WorktreeCreateResult() {}

        public WorktreeCreateResult(boolean success, String worktreePath, String branchName, String headCommit) {
            this.success = success;
            this.worktreePath = worktreePath;
            this.branchName = branchName;
            this.headCommit = headCommit;
        }

        public static WorktreeCreateResult success(String path, String branch, String commit) {
            return new WorktreeCreateResult(true, path, branch, commit);
        }

        public static WorktreeCreateResult failure(String error) {
            WorktreeCreateResult r = new WorktreeCreateResult();
            r.success = false;
            r.error = error;
            return r;
        }

        public boolean isSuccess() { return success; }
        public String getWorktreePath() { return worktreePath; }
        public String getBranchName() { return branchName; }
        public String getHeadCommit() { return headCommit; }
        public String getError() { return error; }
    }
}