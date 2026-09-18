/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestration parameters for a handoff team.
 *
 * <p>Mirrors Python's {@code HandoffConfig} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandoffConfig {

    @JsonProperty("start_agent")
    private AgentCard startAgent;

    @JsonProperty("max_handoffs")
    private int maxHandoffs = 10;

    @JsonProperty("routes")
    private List<HandoffRoute> routes = new ArrayList<>();

    @JsonIgnore
    private HandoffTerminationCondition terminationCondition;

    public AgentCard getStartAgent() {
        return startAgent;
    }

    public void setStartAgent(AgentCard startAgent) {
        this.startAgent = startAgent;
    }

    public int getMaxHandoffs() {
        return maxHandoffs;
    }

    public void setMaxHandoffs(int maxHandoffs) {
        this.maxHandoffs = maxHandoffs;
    }

    public List<HandoffRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<HandoffRoute> routes) {
        this.routes = routes == null ? new ArrayList<>() : new ArrayList<>(routes);
    }

    public HandoffConfig addRoute(HandoffRoute route) {
        routes.add(route);
        return this;
    }

    @JsonIgnore
    public HandoffTerminationCondition getTerminationCondition() {
        return terminationCondition;
    }

    public void setTerminationCondition(HandoffTerminationCondition terminationCondition) {
        this.terminationCondition = terminationCondition;
    }
}
