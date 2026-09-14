/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import com.openjiuwen.agent_teams.agent.TeamAgent;

import java.util.Objects;

/**
 * In-memory team-agent runtime currently held by {@link TeamRuntimePool}.
 *
 * <p>Mirrors Python's {@code ActiveTeam} in
 * {@code openjiuwen/agent_teams/runtime/pool.py}.</p>
 */
public class ActiveTeam {

    private final String teamName;
    private final TeamAgent agent;
    private final String currentSessionId;
    private RuntimeState state;
    private final InteractGate interactGate;

    public ActiveTeam(String teamName, TeamAgent agent, String currentSessionId) {
        this(teamName, agent, currentSessionId, RuntimeState.RUNNING, new InteractGate());
    }

    public ActiveTeam(
            String teamName,
            TeamAgent agent,
            String currentSessionId,
            RuntimeState state,
            InteractGate interactGate
    ) {
        this.teamName = Objects.requireNonNull(teamName, "teamName");
        this.agent = Objects.requireNonNull(agent, "agent");
        this.currentSessionId = Objects.requireNonNull(currentSessionId, "currentSessionId");
        this.state = state == null ? RuntimeState.RUNNING : state;
        this.interactGate = interactGate == null ? new InteractGate() : interactGate;
    }

    public String teamName() {
        return teamName;
    }

    public TeamAgent agent() {
        return agent;
    }

    public String currentSessionId() {
        return currentSessionId;
    }

    public RuntimeState state() {
        return state;
    }

    public void setState(RuntimeState state) {
        this.state = state == null ? RuntimeState.RUNNING : state;
    }

    public InteractGate interactGate() {
        return interactGate;
    }
}
