/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

/**
 * Minimal worktree creation result.
 *
 * <p>Mirrors Python's {@code WorktreeCreateResult} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeCreateResult {

    private final String worktreePath;
    private final String worktreeBranch;
    private final String headCommit;
    private final boolean existed;

    public WorktreeCreateResult(String worktreePath, String worktreeBranch, String headCommit, boolean existed) {
        this.worktreePath = worktreePath;
        this.worktreeBranch = worktreeBranch;
        this.headCommit = headCommit;
        this.existed = existed;
    }

    public String getWorktreePath() {
        return worktreePath;
    }

    public String getWorktreeBranch() {
        return worktreeBranch;
    }

    public String getHeadCommit() {
        return headCommit;
    }

    public boolean isExisted() {
        return existed;
    }
}
