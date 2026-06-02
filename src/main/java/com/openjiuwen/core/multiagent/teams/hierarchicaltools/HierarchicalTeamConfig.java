/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicaltools;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Configuration for HierarchicalTeam (agents-as-tools mode).
 * <p>
 * Mirrors Python's {@code HierarchicalTeamConfig} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_tools.hierarchical_config}.
 * <p>
 * Extends TeamConfig with root agent setting.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HierarchicalTeamConfig extends TeamConfig {
    
    /** Top-level entry agent AgentCard (root, required). */
    private AgentCard rootAgent;

    public HierarchicalTeamConfig(AgentCard rootAgent) {
        super();
        this.rootAgent = rootAgent;
    }
}
