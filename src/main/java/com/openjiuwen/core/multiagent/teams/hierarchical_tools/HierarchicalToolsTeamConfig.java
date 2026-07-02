/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
/**
 * Public class HierarchicalToolsTeamConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = true)
public class HierarchicalToolsTeamConfig extends TeamConfig {
    private AgentCard rootAgent;

    /**
     * Mapping of agentId -> parent_agent_id, mirroring Python's
     * {@code parent_agent_id} argument to {@code HierarchicalTeam.add_agent}.
     *
     * <p>Populated by
     * {@link HierarchicalToolsTeam#addAgent(AgentCard,
     * com.openjiuwen.core.runner.base.AgentProvider, String)} so that
     * {@code invoke} can register each child's {@code AgentCard} into its
     * parent's {@code AbilityManager} exactly like Python's
     * {@code _setup_hierarchy}.</p>
     */
    private Map<String, String> parentByAgent = new LinkedHashMap<>();

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
}
