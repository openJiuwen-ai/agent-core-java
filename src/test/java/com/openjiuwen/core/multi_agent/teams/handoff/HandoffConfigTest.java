/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for handoff configuration classes.
 *
 * <p>Mirrors Python's {@code HandoffRoute}, {@code HandoffConfig}, and {@code HandoffTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
class HandoffConfigTest {

    @Test
    void routeIsImmutableValueObject() {
        HandoffRoute route = new HandoffRoute("agent_a", "agent_b");

        assertThat(route.getSource()).isEqualTo("agent_a");
        assertThat(route.getTarget()).isEqualTo("agent_b");
        assertThat(route).isEqualTo(new HandoffRoute("agent_a", "agent_b"));
        assertThat(route.toString()).isEqualTo("HandoffRoute(source=agent_a, target=agent_b)");
    }

    @Test
    void handoffConfigExposesPythonDefaults() {
        HandoffConfig config = new HandoffConfig();

        assertThat(config.getStartAgent()).isNull();
        assertThat(config.getMaxHandoffs()).isEqualTo(10);
        assertThat(config.getRoutes()).isEmpty();
        assertThat(config.getTerminationCondition()).isNull();
    }

    @Test
    void handoffConfigKeepsMutableRouteListAndTerminationCallable() {
        AgentCard startAgent = new AgentCard("agent_a", "Agent A", "Starts the handoff");
        HandoffConfig config = new HandoffConfig();

        config.setStartAgent(startAgent);
        config.setMaxHandoffs(2);
        config.addRoute(new HandoffRoute("agent_a", "agent_b"));
        config.setTerminationCondition(orchestrator -> true);

        assertThat(config.getStartAgent()).isSameAs(startAgent);
        assertThat(config.getMaxHandoffs()).isEqualTo(2);
        assertThat(config.getRoutes()).containsExactly(new HandoffRoute("agent_a", "agent_b"));
        assertThat(config.getTerminationCondition().shouldTerminate(new Object())).isTrue();
    }

    @Test
    void teamConfigExtendsBaseConfigAndAllowsExtraFields() {
        HandoffConfig handoff = new HandoffConfig();
        handoff.setRoutes(List.of(new HandoffRoute("a", "b")));
        HandoffTeamConfig config = new HandoffTeamConfig(handoff);

        config.putExtraField("custom_mode", "debug");

        assertThat(config.getHandoff()).isSameAs(handoff);
        assertThat(config.getMaxAgents()).isEqualTo(10);
        assertThat(config.getExtraFields()).containsEntry("custom_mode", "debug");
    }
}
