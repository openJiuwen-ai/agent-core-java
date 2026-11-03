/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

/**
 * TeamAgent-level lifecycle effects that span multiple managers.
 *
 * <p>Mirrors Python {@code TeamLifecycleController} protocol
 * ({@code agent/coordination/dispatcher.py:94-111}). {@code shutdownSelf} is
 * invoked when the team has been dissolved and a non-leader member must
 * abandon its loop. {@code concludeCompletedRound} ends a completed persistent
 * team's leader stream so the Runner finally can pause it. Both coordinate
 * across stream / session / member state, so they belong here rather than on
 * the round controller.
 *
 * @since 2026/7/9
 */
public interface TeamLifecycleController {
    /**
     * Force-shutdown this agent in response to team dissolution.
     */
    void shutdownSelf();

    /**
     * Emit a team-completed marker chunk, then close the leader stream.
     *
     * @param memberCount team member count at completion
     * @param taskCount team task count at completion
     */
    void concludeCompletedRound(int memberCount, int taskCount);
}
