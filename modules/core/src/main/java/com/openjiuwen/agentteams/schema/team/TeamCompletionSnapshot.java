/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.team;

/**
 * Immutable snapshot of team-completion counts.
 *
 * <p>Mirrors Python {@code schema/team.py:TeamCompletionSnapshot}. Returned by
 * {@code TeamBackend.isTeamCompleted()} when all three completion conditions
 * hold (every task terminal, every member settled, no unread messages), or
 * when the team row has been deleted (terminal snapshot that short-circuits
 * the normal three-condition check).
 *
 * @param memberCount number of members on the team (including leader)
 * @param taskCount number of tasks on the board
 * @param isTeamDeleted {@code true} when the team DB row is gone; the snapshot
 *     is a terminal marker, not a real completion state. Consumers should
 *     emit {@code TEAM_COMPLETED} once and let the leader stream close.
 *
 * @since 2026/7/9
 */
public record TeamCompletionSnapshot(int memberCount, int taskCount, boolean isTeamDeleted) {

    /**
     * Convenience constructor for the normal three-condition completion path.
     *
     * @param memberCount number of members on the team (including leader)
     * @param taskCount number of tasks on the board
     */
    public TeamCompletionSnapshot(int memberCount, int taskCount) {
        this(memberCount, taskCount, false);
    }
}
