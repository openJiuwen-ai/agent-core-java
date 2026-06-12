/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.AgentRoundController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TeamLifecycleController;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for scenario-scoped coordination event handlers.
 *
 * <p>Mirrors Python's {@code BaseCoordinationHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/base.py}.</p>
 */
public abstract class BaseCoordinationHandler {

    protected final AgentRoundController round;
    protected final TeamLifecycleController lifecycle;
    protected final PollController poll;
    protected final TeamAgentBlueprint blueprint;
    protected final TeamInfra infra;

    protected BaseCoordinationHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController
    ) {
        this.round = Objects.requireNonNull(host, "host");
        this.lifecycle = host;
        this.poll = Objects.requireNonNull(pollController, "pollController");
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.infra = Objects.requireNonNull(infra, "infra");
    }

    public abstract Map<String, String> getEventMethodMap();

    protected abstract EventCallback resolveCallback(String methodName);

    public Map<String, EventCallback> getCallbacks() {
        Map<String, EventCallback> callbacks = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : getEventMethodMap().entrySet()) {
            callbacks.put(entry.getKey(), resolveCallback(entry.getValue()));
        }
        return callbacks;
    }

    public TeamAgentBlueprint getBlueprint() {
        return blueprint;
    }

    public TeamInfra getInfra() {
        return infra;
    }

    public PollController getPoll() {
        return poll;
    }

    public AgentRoundController getRound() {
        return round;
    }

    public TeamLifecycleController getLifecycle() {
        return lifecycle;
    }
}
