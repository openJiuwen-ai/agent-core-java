/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for hierarchical team configuration.
 *
 * <p>Mirrors Python's {@code HierarchicalTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/hierarchical_config.py}.</p>
 */
class HierarchicalTeamConfigTest {

    @Test
    void exposesPythonDefaultsAndBaseTeamConfig() {
        HierarchicalTeamConfig config = new HierarchicalTeamConfig();

        assertThat(config.getSupervisorAgent()).isNull();
        assertThat(config.getTimeout()).isEqualTo(1800.0);
        assertThat(config.getMaxAgents()).isEqualTo(10);
        assertThat(config.getMessageTimeout()).isEqualTo(30.0);
    }

    @Test
    void storesRequiredSupervisorAgentAndOptionalTimeout() {
        AgentCard supervisor = new AgentCard("supervisor", "Supervisor", "Top-level supervisor");
        HierarchicalTeamConfig config = new HierarchicalTeamConfig(supervisor);

        assertThat(config.getSupervisorAgent()).isSameAs(supervisor);
        assertThat(config.getTimeout()).isEqualTo(1800.0);

        config.setTimeout(null);
        assertThat(config.getTimeout()).isNull();

        config.setTimeout(15.5);
        assertThat(config.getTimeout()).isEqualTo(15.5);
    }

    @Test
    void supportsInheritedExtraFields() {
        HierarchicalTeamConfig config = new HierarchicalTeamConfig(
                new AgentCard("supervisor", "Supervisor", ""),
                42.0
        );

        config.putExtraField("custom_mode", "debug");

        assertThat(config.getTimeout()).isEqualTo(42.0);
        assertThat(config.getExtraFields()).containsEntry("custom_mode", "debug");
    }
}
