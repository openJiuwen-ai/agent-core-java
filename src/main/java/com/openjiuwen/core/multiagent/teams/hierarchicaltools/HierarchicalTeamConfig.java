/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicaltools;

import com.openjiuwen.core.singleagent.schema.AgentCard;

/**
 * Camelcase package compatibility facade for hierarchical tools team configuration.
 *
 * <p>Mirrors Python's {@code HierarchicalTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/hierarchical_config.py}.</p>
 */
public class HierarchicalTeamConfig
        extends com.openjiuwen.core.multiagent.teams.hierarchical_tools.HierarchicalTeamConfig {

    public HierarchicalTeamConfig() {
        super();
    }

    public HierarchicalTeamConfig(AgentCard rootAgent) {
        super(rootAgent);
    }
}
