/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Camelcase package compatibility facade for multi-agent teams.
 *
 * <p>Mirrors Python's {@code BaseTeam} in
 * {@code openjiuwen/core/multi_agent/team.py}.</p>
 */
public abstract class BaseTeam extends com.openjiuwen.core.multi_agent.BaseTeam {

    public BaseTeam(TeamCard card) {
        super(card);
    }

    public BaseTeam(TeamCard card, TeamConfig config) {
        super(card, config);
    }

    public BaseTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
        super(card, config, runtime);
    }

    public BaseTeam addAgent(AgentCard agentCard, Supplier<?> provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        super.addAgent(agentCard, ignored -> provider.get());
        return this;
    }

    @Override
    public BaseTeam addAgent(AgentCard agentCard, Function<AgentCard, ?> provider) {
        super.addAgent(agentCard, provider);
        return this;
    }

    @Override
    public final CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        return invoke(message, (Session) null);
    }

    public CompletionStage<Object> invoke(Object message) {
        return invoke(message, (Session) null);
    }

    public abstract CompletionStage<Object> invoke(Object message, Session session);

    @Override
    public final Stream<Object> stream(Object message, AgentSessionApi session) {
        return stream(message, (Session) null);
    }

    public Stream<Object> stream(Object message) {
        return stream(message, (Session) null);
    }

    public abstract Stream<Object> stream(Object message, Session session);
}
