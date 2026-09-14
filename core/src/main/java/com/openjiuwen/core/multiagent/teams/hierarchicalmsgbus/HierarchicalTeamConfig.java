/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Camelcase package compatibility facade for hierarchical message-bus team configuration.
 *
 * <p>Mirrors Python's {@code HierarchicalTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/hierarchical_config.py}.</p>
 */
public class HierarchicalTeamConfig
        extends com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.HierarchicalTeamConfig {

    public HierarchicalTeamConfig() {
        super();
    }

    public HierarchicalTeamConfig(AgentCard supervisorAgent) {
        super(supervisorAgent);
    }

    public HierarchicalTeamConfig(AgentCard supervisorAgent, Double timeout) {
        super(supervisorAgent, timeout);
    }
}
