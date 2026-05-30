/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.Objects;

/**
 * Minimal runtime session for an active worktree.
 *
 * <p>Mirrors Python's {@code WorktreeSession} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeSession {

    private String originalCwd;
    private String workspaceRoot;
    private String worktreePath;
    private String worktreeName;
    private String worktreeBranch;
    private String originalBranch;
    private String originalHeadCommit;
    private String memberName;
    private String teamName;
    private boolean hookBased;
    private WorktreeLifecyclePolicy lifecyclePolicy = WorktreeLifecyclePolicy.AUTO;
    private String teamLifecycle;
    private Double creationDurationMs;
    private boolean usedSparsePaths;

    public WorktreeSession() {
    }

    public WorktreeSession(
            String originalCwd,
            String workspaceRoot,
            String teamName,
            String memberName,
            String slug,
            String worktreePath,
            String branchName
    ) {
        this.originalCwd = originalCwd;
        this.workspaceRoot = workspaceRoot;
        this.teamName = teamName;
        this.memberName = memberName;
        this.worktreeName = slug;
        this.worktreePath = worktreePath;
        this.worktreeBranch = branchName;
    }

    public WorktreeSession(String originalCwd, String worktreePath, String worktreeName) {
        this.originalCwd = originalCwd;
        this.worktreePath = worktreePath;
        this.worktreeName = worktreeName;
    }

    public WorktreeSession(
            String originalCwd,
            String worktreePath,
            String worktreeName,
            String worktreeBranch,
            String originalBranch,
            String originalHeadCommit,
            String memberName,
            String teamName,
            boolean hookBased,
            WorktreeLifecyclePolicy lifecyclePolicy,
            String teamLifecycle,
            Double creationDurationMs,
            boolean usedSparsePaths
    ) {
        this.originalCwd = originalCwd;
        this.worktreePath = worktreePath;
        this.worktreeName = worktreeName;
        this.worktreeBranch = worktreeBranch;
        this.originalBranch = originalBranch;
        this.originalHeadCommit = originalHeadCommit;
        this.memberName = memberName;
        this.teamName = teamName;
        this.hookBased = hookBased;
        this.lifecyclePolicy = lifecyclePolicy != null ? lifecyclePolicy : WorktreeLifecyclePolicy.AUTO;
        this.teamLifecycle = teamLifecycle;
        this.creationDurationMs = creationDurationMs;
        this.usedSparsePaths = usedSparsePaths;
    }

    public String getOriginalCwd() {
        return originalCwd;
    }

    public void setOriginalCwd(String originalCwd) {
        this.originalCwd = originalCwd;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getSlug() {
        return worktreeName;
    }

    public void setSlug(String slug) {
        this.worktreeName = slug;
    }

    public String getWorktreePath() {
        return worktreePath;
    }

    public void setWorktreePath(String worktreePath) {
        this.worktreePath = worktreePath;
    }

    public String getWorktreeName() {
        return worktreeName;
    }

    public void setWorktreeName(String worktreeName) {
        this.worktreeName = worktreeName;
    }

    public String getWorktreeBranch() {
        return worktreeBranch;
    }

    public void setWorktreeBranch(String worktreeBranch) {
        this.worktreeBranch = worktreeBranch;
    }

    public String getBranchName() {
        return worktreeBranch;
    }

    public void setBranchName(String branchName) {
        this.worktreeBranch = branchName;
    }

    public String getOriginalBranch() {
        return originalBranch;
    }

    public void setOriginalBranch(String originalBranch) {
        this.originalBranch = originalBranch;
    }

    public String getOriginalHeadCommit() {
        return originalHeadCommit;
    }

    public void setOriginalHeadCommit(String originalHeadCommit) {
        this.originalHeadCommit = originalHeadCommit;
    }

    public boolean isHookBased() {
        return hookBased;
    }

    public void setHookBased(boolean hookBased) {
        this.hookBased = hookBased;
    }

    public WorktreeLifecyclePolicy getLifecyclePolicy() {
        return lifecyclePolicy;
    }

    public void setLifecyclePolicy(WorktreeLifecyclePolicy lifecyclePolicy) {
        this.lifecyclePolicy = lifecyclePolicy != null ? lifecyclePolicy : WorktreeLifecyclePolicy.AUTO;
    }

    public String getTeamLifecycle() {
        return teamLifecycle;
    }

    public void setTeamLifecycle(String teamLifecycle) {
        this.teamLifecycle = teamLifecycle;
    }

    public Double getCreationDurationMs() {
        return creationDurationMs;
    }

    public void setCreationDurationMs(Double creationDurationMs) {
        this.creationDurationMs = creationDurationMs;
    }

    public boolean isUsedSparsePaths() {
        return usedSparsePaths;
    }

    public void setUsedSparsePaths(boolean usedSparsePaths) {
        this.usedSparsePaths = usedSparsePaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorktreeSession that)) {
            return false;
        }
        return hookBased == that.hookBased
                && usedSparsePaths == that.usedSparsePaths
                && Objects.equals(originalCwd, that.originalCwd)
                && Objects.equals(workspaceRoot, that.workspaceRoot)
                && Objects.equals(worktreePath, that.worktreePath)
                && Objects.equals(worktreeName, that.worktreeName)
                && Objects.equals(worktreeBranch, that.worktreeBranch)
                && Objects.equals(originalBranch, that.originalBranch)
                && Objects.equals(originalHeadCommit, that.originalHeadCommit)
                && Objects.equals(memberName, that.memberName)
                && Objects.equals(teamName, that.teamName)
                && lifecyclePolicy == that.lifecyclePolicy
                && Objects.equals(teamLifecycle, that.teamLifecycle)
                && Objects.equals(creationDurationMs, that.creationDurationMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                originalCwd,
                workspaceRoot,
                worktreePath,
                worktreeName,
                worktreeBranch,
                originalBranch,
                originalHeadCommit,
                memberName,
                teamName,
                hookBased,
                lifecyclePolicy,
                teamLifecycle,
                creationDurationMs,
                usedSparsePaths
        );
    }
}
