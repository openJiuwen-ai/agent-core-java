/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Configuration for hierarchical message-bus teams.
 *
 * <p>Mirrors Python's {@code HierarchicalTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/hierarchical_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HierarchicalTeamConfig extends TeamConfig {

    @JsonProperty("supervisor_agent")
    private AgentCard supervisorAgent;

    @JsonProperty("timeout")
    private Double timeout = 1800.0;

    public HierarchicalTeamConfig() {
        super();
    }

    public HierarchicalTeamConfig(AgentCard supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    public HierarchicalTeamConfig(AgentCard supervisorAgent, Double timeout) {
        this.supervisorAgent = supervisorAgent;
        this.timeout = timeout;
    }

    public AgentCard getSupervisorAgent() {
        return supervisorAgent;
    }

    public void setSupervisorAgent(AgentCard supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    public Double getTimeout() {
        return timeout;
    }

    public void setTimeout(Double timeout) {
        this.timeout = timeout;
    }
}
