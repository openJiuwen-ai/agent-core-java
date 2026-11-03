/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Configuration for hierarchical tools teams.
 *
 * <p>Mirrors Python's {@code HierarchicalTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/hierarchical_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HierarchicalTeamConfig extends TeamConfig {

    @JsonProperty("root_agent")
    private AgentCard rootAgent;

    public HierarchicalTeamConfig() {
        super();
    }

    public HierarchicalTeamConfig(AgentCard rootAgent) {
        this.rootAgent = rootAgent;
    }

    public AgentCard getRootAgent() {
        return rootAgent;
    }

    public void setRootAgent(AgentCard rootAgent) {
        this.rootAgent = rootAgent;
    }
}
