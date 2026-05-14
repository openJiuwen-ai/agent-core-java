/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

/**
 * Minimal runtime session for an active worktree.
 *
 * <p>Mirrors Python's {@code WorktreeSession} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeSession {

    private final String originalCwd;
    private final String workspaceRoot;
    private final String teamName;
    private final String memberName;
    private final String slug;
    private final String worktreePath;
    private final String branchName;

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
        this.slug = slug;
        this.worktreePath = worktreePath;
        this.branchName = branchName;
    }

    public String getOriginalCwd() {
        return originalCwd;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getSlug() {
        return slug;
    }

    public String getWorktreePath() {
        return worktreePath;
    }

    public String getBranchName() {
        return branchName;
    }
}
