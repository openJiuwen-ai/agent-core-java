/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

/**
 * Minimal change summary for a worktree.
 *
 * <p>Mirrors Python's {@code WorktreeChangeSummary} in
 * {@code openjiuwen.agent_teams.worktree.models}.</p>
 */
public class WorktreeChangeSummary {

    private final boolean hasChanges;
    private final int aheadCount;
    private final String branch;

    public WorktreeChangeSummary(boolean hasChanges, int aheadCount, String branch) {
        this.hasChanges = hasChanges;
        this.aheadCount = aheadCount;
        this.branch = branch;
    }

    public boolean isHasChanges() {
        return hasChanges;
    }

    public int getAheadCount() {
        return aheadCount;
    }

    public String getBranch() {
        return branch;
    }
}
