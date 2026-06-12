/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.Objects;

/**
 * Counts captured when a team satisfies completion conditions.
 *
 * <p>Mirrors Python's {@code TeamCompletionSnapshot} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
public final class TeamCompletionSnapshot {

    private final int memberCount;
    private final int taskCount;

    public TeamCompletionSnapshot(int memberCount, int taskCount) {
        this.memberCount = memberCount;
        this.taskCount = taskCount;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public int getTaskCount() {
        return taskCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TeamCompletionSnapshot that)) {
            return false;
        }
        return memberCount == that.memberCount && taskCount == that.taskCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberCount, taskCount);
    }
}
