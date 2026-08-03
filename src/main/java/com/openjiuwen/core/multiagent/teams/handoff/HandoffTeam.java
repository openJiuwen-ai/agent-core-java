/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.concurrent.CompletableFuture;

/**
 * Camelcase package compatibility facade for handoff teams.
 *
 * <p>Mirrors Python's {@code HandoffTeam} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_team.py}.</p>
 */
public class HandoffTeam extends com.openjiuwen.core.multi_agent.teams.handoff.HandoffTeam {

    public HandoffTeam(TeamCard card) {
        super(card);
    }

    public HandoffTeam(TeamCard card, HandoffTeamConfig config) {
        super(card, config);
    }

    @Override
    public CompletableFuture<Object> invoke(Object message) {
        return invoke(message, (AgentSessionApi) null);
    }

    @Override
    public CompletableFuture<Object> invoke(Object message, AgentSessionApi session) {
        return super.invoke(message, session).toCompletableFuture();
    }
}
