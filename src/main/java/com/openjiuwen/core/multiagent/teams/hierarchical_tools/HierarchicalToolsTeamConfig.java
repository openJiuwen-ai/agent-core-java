/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config for {@link HierarchicalToolsTeam}.
 *
 * <p>Mirrors Python's hierarchical-tools team config ({@code root_agent},
 * {@code parent_agent_id} mapping).</p>
 */
public class HierarchicalToolsTeamConfig extends TeamConfig {

    private AgentCard rootAgent;

    /**
     * Mapping of agentId -> parent_agent_id, mirroring Python's
     * {@code parent_agent_id} argument to {@code HierarchicalTeam.add_agent}.
     */
    private Map<String, String> parentByAgent = new LinkedHashMap<>();

    public HierarchicalToolsTeamConfig() {
        this.parentByAgent = new LinkedHashMap<>();
    }

    /**
     * Convenience constructor mirroring Python's
     * {@code HierarchicalTeamConfig(root_agent=...)}.
     *
     * @param rootAgent the root agent card
     */
    public HierarchicalToolsTeamConfig(AgentCard rootAgent) {
        this.rootAgent = rootAgent;
        this.parentByAgent = new LinkedHashMap<>();
    }

    public AgentCard getRootAgent() {
        return rootAgent;
    }

    public void setRootAgent(AgentCard rootAgent) {
        this.rootAgent = rootAgent;
    }

    public Map<String, String> getParentByAgent() {
        return parentByAgent;
    }

    public void setParentByAgent(Map<String, String> parentByAgent) {
        this.parentByAgent = parentByAgent == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parentByAgent);
    }
}
