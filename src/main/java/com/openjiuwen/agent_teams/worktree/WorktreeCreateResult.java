/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.Objects;

/**
 * Minimal worktree creation result.
 *
 * <p>Mirrors Python's {@code WorktreeCreateResult} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeCreateResult {

    private String worktreePath;
    private String worktreeBranch;
    private String headCommit;
    private String baseBranch;
    private boolean existed;
    private boolean hookBased;

    public WorktreeCreateResult() {
    }

    public WorktreeCreateResult(String worktreePath, String worktreeBranch, String headCommit, boolean existed) {
        this.worktreePath = worktreePath;
        this.worktreeBranch = worktreeBranch;
        this.headCommit = headCommit;
        this.existed = existed;
    }

    public WorktreeCreateResult(
            String worktreePath,
            String worktreeBranch,
            String headCommit,
            String baseBranch,
            boolean existed,
            boolean hookBased
    ) {
        this.worktreePath = worktreePath;
        this.worktreeBranch = worktreeBranch;
        this.headCommit = headCommit;
        this.baseBranch = baseBranch;
        this.existed = existed;
        this.hookBased = hookBased;
    }

    public String getWorktreePath() {
        return worktreePath;
    }

    public void setWorktreePath(String worktreePath) {
        this.worktreePath = worktreePath;
    }

    public String getWorktreeBranch() {
        return worktreeBranch;
    }

    public void setWorktreeBranch(String worktreeBranch) {
        this.worktreeBranch = worktreeBranch;
    }

    public String getHeadCommit() {
        return headCommit;
    }

    public void setHeadCommit(String headCommit) {
        this.headCommit = headCommit;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public boolean isExisted() {
        return existed;
    }

    public void setExisted(boolean existed) {
        this.existed = existed;
    }

    public boolean isHookBased() {
        return hookBased;
    }

    public void setHookBased(boolean hookBased) {
        this.hookBased = hookBased;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorktreeCreateResult that)) {
            return false;
        }
        return existed == that.existed
                && hookBased == that.hookBased
                && Objects.equals(worktreePath, that.worktreePath)
                && Objects.equals(worktreeBranch, that.worktreeBranch)
                && Objects.equals(headCommit, that.headCommit)
                && Objects.equals(baseBranch, that.baseBranch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worktreePath, worktreeBranch, headCommit, baseBranch, existed, hookBased);
    }
}
