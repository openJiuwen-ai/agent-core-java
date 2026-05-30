/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.util.Objects;

/**
 * Minimal change summary for a worktree.
 *
 * <p>Mirrors Python's {@code WorktreeChangeSummary} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeChangeSummary {

    private int changedFiles;
    private int commits;
    private String branch;

    public WorktreeChangeSummary() {
    }

    public WorktreeChangeSummary(int changedFiles, int commits) {
        this.changedFiles = changedFiles;
        this.commits = commits;
    }

    public WorktreeChangeSummary(boolean hasChanges, int aheadCount, String branch) {
        this.changedFiles = hasChanges ? 1 : 0;
        this.commits = aheadCount;
        this.branch = branch;
    }

    public boolean isHasChanges() {
        return changedFiles > 0;
    }

    public int getAheadCount() {
        return commits;
    }

    public int getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(int changedFiles) {
        this.changedFiles = changedFiles;
    }

    public int getCommits() {
        return commits;
    }

    public void setCommits(int commits) {
        this.commits = commits;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorktreeChangeSummary that)) {
            return false;
        }
        return changedFiles == that.changedFiles
                && commits == that.commits
                && Objects.equals(branch, that.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changedFiles, commits, branch);
    }
}
