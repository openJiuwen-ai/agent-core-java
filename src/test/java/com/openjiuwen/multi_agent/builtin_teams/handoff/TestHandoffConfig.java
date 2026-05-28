/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffRoute;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff config.
 *
 * <p>Mirrors Python's {@code test_handoff_config.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffConfig {

    private static AgentCard agentCard(String id) {
        AgentCard card = new AgentCard();
        card.setId(id);
        card.setName(id);
        card.setDescription("agent " + id);
        return card;
    }

    @Nested
    class TestHandoffRoute {

        @Test
        void testSourceAndTargetStored() {
            HandoffRoute route = new HandoffRoute("a", "b");
            assertEquals("a", route.getSource());
            assertEquals("b", route.getTarget());
        }

        @Test
        void testEqualityBasedOnValues() {
            assertEquals(new HandoffRoute("a", "b"), new HandoffRoute("a", "b"));
        }

        @Test
        void testHashableUsableInSet() {
            HandoffRoute route = new HandoffRoute("a", "b");
            assertTrue(java.util.Set.of(route).contains(route));
        }
    }

    @Nested
    class TestHandoffConfigData {

        @Test
        void testDefaultMaxHandoffs() {
            assertEquals(10, new HandoffConfig().getMaxHandoffs());
        }

        @Test
        void testDefaultRoutesEmptyList() {
            assertEquals(List.of(), new HandoffConfig().getRoutes());
        }

        @Test
        void testDefaultStartAgentIsEmptyOptional() {
            assertTrue(new HandoffConfig().getStartAgent().isEmpty());
        }

        @Test
        void testDefaultTerminationConditionIsNull() {
            assertNull(new HandoffConfig().getTerminationCondition());
        }

        @Test
        void testCustomValues() {
            AgentCard start = agentCard("entry");
            Predicate<Object> condition = ignored -> true;
            HandoffRoute route = new HandoffRoute("a", "b");
            HandoffConfig config = HandoffConfig.builder()
                    .startAgent(start)
                    .maxHandoffs(5)
                    .routes(List.of(route))
                    .terminationCondition(condition)
                    .build();

            assertSame(start, config.getStartAgent().orElseThrow());
            assertEquals(5, config.getMaxHandoffs());
            assertEquals(List.of(route), config.getRoutes());
            assertSame(condition, config.getTerminationCondition());
        }
    }

    @Nested
    class TestHandoffTeamConfigData {

        @Test
        void testInheritsTeamConfig() {
            assertInstanceOf(TeamConfig.class, new HandoffTeamConfig());
        }

        @Test
        void testDefaultHandoffIsHandoffConfigInstance() {
            assertNotNull(new HandoffTeamConfig().getHandoff());
        }

        @Test
        void testDefaultHandoffMaxHandoffs() {
            assertEquals(10, new HandoffTeamConfig().getHandoff().getMaxHandoffs());
        }

        @Test
        void testTeamConfigDefaultsPreserved() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertEquals(10, config.getMaxAgents());
            assertEquals(100, config.getMaxConcurrentMessages());
            assertEquals(30.0, config.getMessageTimeout());
        }

        @Test
        void testConfigureChaining() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertSame(config, config.configureMaxAgents(5));
            assertSame(config, config.configureTimeout(60.0));
            assertSame(config, config.configureConcurrency(50));
            assertEquals(5, config.getMaxAgents());
            assertEquals(60.0, config.getMessageTimeout());
            assertEquals(50, config.getMaxConcurrentMessages());
        }
    }
}
