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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff config.
 *
 * <p>Mirrors Python's {@code test_handoff_config.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffConfig {

    private static AgentCard agentCard(String id) {
        return AgentCard.builder().id(id).name(id).description("agent " + id).build();
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
        void testFrozenPreventsSourceMutation() throws NoSuchFieldException {
            Field field = HandoffRoute.class.getDeclaredField("source");
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }

        @Test
        void testFrozenPreventsTargetMutation() throws NoSuchFieldException {
            Field field = HandoffRoute.class.getDeclaredField("target");
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }

        @Test
        void testEqualityBasedOnValues() {
            assertEquals(new HandoffRoute("a", "b"), new HandoffRoute("a", "b"));
        }

        @Test
        void testInequalityDifferentSource() {
            assertNotEquals(new HandoffRoute("a", "b"), new HandoffRoute("x", "b"));
        }

        @Test
        void testInequalityDifferentTarget() {
            assertNotEquals(new HandoffRoute("a", "b"), new HandoffRoute("a", "z"));
        }

        @Test
        void testHashableUsableInSet() {
            HandoffRoute route = new HandoffRoute("a", "b");
            assertTrue(Set.of(route).contains(new HandoffRoute("a", "b")));
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
        void testDefaultStartAgentIsNone() {
            assertTrue(new HandoffConfig().getStartAgent().isEmpty());
        }

        @Test
        void testDefaultTerminationConditionIsNone() {
            assertNull(new HandoffConfig().getTerminationCondition());
        }

        @Test
        void testCustomMaxHandoffs() {
            assertEquals(5, HandoffConfig.builder().maxHandoffs(5).build().getMaxHandoffs());
        }

        @Test
        void testCustomStartAgent() {
            AgentCard card = agentCard("start");
            assertSame(card, HandoffConfig.builder().startAgent(card).build().getStartAgent().orElseThrow());
        }

        @Test
        void testCustomRoutes() {
            List<HandoffRoute> routes = List.of(new HandoffRoute("a", "b"), new HandoffRoute("b", "c"));
            assertEquals(routes, HandoffConfig.builder().routes(routes).build().getRoutes());
        }

        @Test
        void testCustomTerminationCondition() {
            Function<Object, Object> condition = ignored -> true;
            assertSame(condition, HandoffConfig.builder().terminationCondition(condition).build()
                    .getTerminationCondition());
        }

        @Test
        void testMaxHandoffsZeroAllowed() {
            assertEquals(0, HandoffConfig.builder().maxHandoffs(0).build().getMaxHandoffs());
        }

        @Test
        void testRoutesListIsIndependentPerInstance() {
            HandoffConfig cfg1 = new HandoffConfig();
            HandoffConfig cfg2 = new HandoffConfig();
            cfg1.getRoutes().add(new HandoffRoute("a", "b"));
            assertEquals(List.of(), cfg2.getRoutes());
        }

        @Test
        void testStartAgentIdAccessible() {
            assertEquals("entry", HandoffConfig.builder().startAgent(agentCard("entry")).build()
                    .getStartAgent().orElseThrow().getId());
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
            assertInstanceOf(HandoffConfig.class, new HandoffTeamConfig().getHandoff());
        }

        @Test
        void testDefaultHandoffMaxHandoffs() {
            assertEquals(10, new HandoffTeamConfig().getHandoff().getMaxHandoffs());
        }

        @Test
        void testCustomHandoffConfig() {
            HandoffConfig handoff = HandoffConfig.builder().maxHandoffs(3).build();
            HandoffTeamConfig config = new HandoffTeamConfig(handoff);
            assertEquals(3, config.getHandoff().getMaxHandoffs());
        }

        @Test
        void testTeamConfigDefaultsPreserved() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertEquals(10, config.getMaxAgents());
            assertEquals(100, config.getMaxConcurrentMessages());
            assertEquals(30.0, config.getMessageTimeout());
        }

        @Test
        void testArbitraryTypesAllowedForCallable() {
            Function<Object, Object> condition = ignored -> false;
            HandoffTeamConfig config = new HandoffTeamConfig(HandoffConfig.builder()
                    .terminationCondition(condition)
                    .build());
            assertSame(condition, config.getHandoff().getTerminationCondition());
        }

        @Test
        void testConfigureMaxAgentsChaining() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertSame(config, config.configureMaxAgents(5));
            assertEquals(5, config.getMaxAgents());
        }

        @Test
        void testConfigureTimeoutChaining() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertSame(config, config.configureTimeout(60.0));
            assertEquals(60.0, config.getMessageTimeout());
        }

        @Test
        void testConfigureConcurrencyChaining() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertSame(config, config.configureConcurrency(50));
            assertEquals(50, config.getMaxConcurrentMessages());
        }

        @Test
        void testHandoffConfigWithRoutes() {
            HandoffTeamConfig config = new HandoffTeamConfig(HandoffConfig.builder()
                    .routes(List.of(new HandoffRoute("a", "b")))
                    .build());
            assertEquals(1, config.getHandoff().getRoutes().size());
            assertEquals("a", config.getHandoff().getRoutes().get(0).getSource());
        }

        @Test
        void testExtraFieldsAllowed() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            config.getProperties().put("custom_extra", "value");
            assertEquals("value", config.getProperties().get("custom_extra"));
        }
    }
}
