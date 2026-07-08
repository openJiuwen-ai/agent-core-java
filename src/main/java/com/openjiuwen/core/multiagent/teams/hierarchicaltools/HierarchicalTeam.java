/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicaltools;

import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Camelcase package compatibility facade for hierarchical tools teams.
 *
 * <p>Mirrors Python's {@code HierarchicalTeam} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/hierarchical_team.py}.</p>
 */
public class HierarchicalTeam extends com.openjiuwen.core.multi_agent.teams.hierarchical_tools.HierarchicalTeam {

    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config) {
        super(card, config);
    }

    public HierarchicalTeam addAgent(AgentCard agentCard, Supplier<?> provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        super.addAgent(agentCard, ignored -> provider.get());
        return this;
    }

    public HierarchicalTeam addAgent(AgentCard agentCard, Supplier<?> provider, String parentAgentId) {
        Objects.requireNonNull(provider, "provider must not be null");
        super.addAgent(agentCard, ignored -> provider.get(), parentAgentId);
        return this;
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
