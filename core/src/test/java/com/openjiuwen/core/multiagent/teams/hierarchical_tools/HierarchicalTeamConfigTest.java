/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for hierarchical tools team configuration.
 *
 * <p>Mirrors Python's {@code HierarchicalTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/hierarchical_config.py}.</p>
 */
class HierarchicalTeamConfigTest {

    @Test
    void exposesPythonRequiredRootAgentFieldAndBaseDefaults() {
        HierarchicalTeamConfig config = new HierarchicalTeamConfig();

        assertThat(config.getRootAgent()).isNull();
        assertThat(config.getMaxAgents()).isEqualTo(10);
        assertThat(config.getMessageTimeout()).isEqualTo(30.0);
    }

    @Test
    void storesRootAgent() {
        AgentCard rootAgent = new AgentCard("root", "Root", "Top-level entry agent");
        HierarchicalTeamConfig config = new HierarchicalTeamConfig(rootAgent);

        assertThat(config.getRootAgent()).isSameAs(rootAgent);

        AgentCard replacement = new AgentCard("replacement", "Replacement", "");
        config.setRootAgent(replacement);

        assertThat(config.getRootAgent()).isSameAs(replacement);
    }

    @Test
    void supportsInheritedExtraFields() {
        HierarchicalTeamConfig config = new HierarchicalTeamConfig(
                new AgentCard("root", "Root", "")
        );

        config.putExtraField("custom_mode", "debug");

        assertThat(config.getExtraFields()).containsEntry("custom_mode", "debug");
    }
}
