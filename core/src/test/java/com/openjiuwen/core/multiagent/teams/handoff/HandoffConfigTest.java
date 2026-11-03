/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for handoff configuration classes.
 *
 * <p>Mirrors Python's {@code HandoffRoute}, {@code HandoffConfig}, and {@code HandoffTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.builtin_teams.handoff.test_handoff_config} in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_config.py}.</p>
 */
class HandoffConfigTest {

    @Test
    void routeSourceAndTargetAreStored() {
        HandoffRoute route = new HandoffRoute("a", "b");

        assertThat(route.getSource()).isEqualTo("a");
        assertThat(route.getTarget()).isEqualTo("b");
    }

    @Test
    void routeSourceIsImmutable() {
        assertThat(hasMethodNamed(HandoffRoute.class, "setSource")).isFalse();
    }

    @Test
    void routeTargetIsImmutable() {
        assertThat(hasMethodNamed(HandoffRoute.class, "setTarget")).isFalse();
    }

    @Test
    void routeEqualityIsBasedOnValues() {
        HandoffRoute first = new HandoffRoute("a", "b");
        HandoffRoute second = new HandoffRoute("a", "b");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void routeInequalityCanUseDifferentSource() {
        HandoffRoute first = new HandoffRoute("a", "b");
        HandoffRoute second = new HandoffRoute("x", "b");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void routeInequalityCanUseDifferentTarget() {
        HandoffRoute first = new HandoffRoute("a", "b");
        HandoffRoute second = new HandoffRoute("a", "z");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void routeIsHashableAndUsableInSet() {
        HandoffRoute route = new HandoffRoute("a", "b");

        assertThat(Set.of(route)).contains(route);
    }

    @Test
    void defaultMaxHandoffsIsTen() {
        assertThat(new HandoffConfig().getMaxHandoffs()).isEqualTo(10);
    }

    @Test
    void defaultRoutesIsEmptyList() {
        assertThat(new HandoffConfig().getRoutes()).isEmpty();
    }

    @Test
    void defaultStartAgentIsNull() {
        assertThat(new HandoffConfig().getStartAgent()).isNull();
    }

    @Test
    void defaultTerminationConditionIsNull() {
        assertThat(new HandoffConfig().getTerminationCondition()).isNull();
    }

    @Test
    void customMaxHandoffsIsStored() {
        HandoffConfig config = new HandoffConfig();
        config.setMaxHandoffs(5);

        assertThat(config.getMaxHandoffs()).isEqualTo(5);
    }

    @Test
    void customStartAgentIsStoredByIdentity() {
        AgentCard startAgent = agentCard("start");
        HandoffConfig config = new HandoffConfig();
        config.setStartAgent(startAgent);

        assertThat(config.getStartAgent()).isSameAs(startAgent);
    }

    @Test
    void customRoutesAreStored() {
        List<HandoffRoute> routes = List.of(new HandoffRoute("a", "b"), new HandoffRoute("b", "c"));
        HandoffConfig config = new HandoffConfig();
        config.setRoutes(routes);

        assertThat(config.getRoutes()).containsExactlyElementsOf(routes);
    }

    @Test
    void customTerminationConditionIsStoredByIdentity() {
        HandoffTerminationCondition condition = ignored -> true;
        HandoffConfig config = new HandoffConfig();
        config.setTerminationCondition(condition);

        assertThat(config.getTerminationCondition()).isSameAs(condition);
    }

    @Test
    void maxHandoffsAllowsZero() {
        HandoffConfig config = new HandoffConfig();
        config.setMaxHandoffs(0);

        assertThat(config.getMaxHandoffs()).isZero();
    }

    @Test
    void routesListIsIndependentPerInstance() {
        HandoffConfig first = new HandoffConfig();
        HandoffConfig second = new HandoffConfig();
        first.getRoutes().add(new HandoffRoute("a", "b"));

        assertThat(second.getRoutes()).isEmpty();
    }

    @Test
    void startAgentIdIsAccessible() {
        AgentCard startAgent = agentCard("entry");
        HandoffConfig config = new HandoffConfig();
        config.setStartAgent(startAgent);

        assertThat(config.getStartAgent().getId()).isEqualTo("entry");
    }

    @Test
    void teamConfigInheritsBaseTeamConfig() {
        assertThat(new HandoffTeamConfig()).isInstanceOf(TeamConfig.class);
    }

    @Test
    void defaultHandoffIsHandoffConfigInstance() {
        assertThat(new HandoffTeamConfig().getHandoff()).isInstanceOf(HandoffConfig.class);
    }

    @Test
    void defaultHandoffKeepsDefaultMaxHandoffs() {
        assertThat(new HandoffTeamConfig().getHandoff().getMaxHandoffs()).isEqualTo(10);
    }

    @Test
    void customHandoffConfigIsStored() {
        HandoffConfig handoff = new HandoffConfig();
        handoff.setMaxHandoffs(3);
        HandoffTeamConfig config = new HandoffTeamConfig(handoff);

        assertThat(config.getHandoff().getMaxHandoffs()).isEqualTo(3);
    }

    @Test
    void teamConfigDefaultsArePreserved() {
        HandoffTeamConfig config = new HandoffTeamConfig();

        assertThat(config.getMaxAgents()).isEqualTo(10);
        assertThat(config.getMaxConcurrentMessages()).isEqualTo(100);
        assertThat(config.getMessageTimeout()).isEqualTo(30.0);
    }

    @Test
    void arbitraryTerminationCallableTypeIsAllowed() {
        HandoffTerminationCondition condition = ignored -> false;
        HandoffConfig handoff = new HandoffConfig();
        handoff.setTerminationCondition(condition);
        HandoffTeamConfig config = new HandoffTeamConfig(handoff);

        assertThat(config.getHandoff().getTerminationCondition()).isSameAs(condition);
    }

    @Test
    void configureMaxAgentsReturnsSameConfig() {
        HandoffTeamConfig config = new HandoffTeamConfig();
        TeamConfig result = config.configureMaxAgents(5);

        assertThat(result).isSameAs(config);
        assertThat(config.getMaxAgents()).isEqualTo(5);
    }

    @Test
    void configureTimeoutReturnsSameConfig() {
        HandoffTeamConfig config = new HandoffTeamConfig();
        TeamConfig result = config.configureTimeout(60.0);

        assertThat(result).isSameAs(config);
        assertThat(config.getMessageTimeout()).isEqualTo(60.0);
    }

    @Test
    void configureConcurrencyReturnsSameConfig() {
        HandoffTeamConfig config = new HandoffTeamConfig();
        TeamConfig result = config.configureConcurrency(50);

        assertThat(result).isSameAs(config);
        assertThat(config.getMaxConcurrentMessages()).isEqualTo(50);
    }

    @Test
    void handoffConfigWithRoutesIsAccessibleThroughTeamConfig() {
        HandoffConfig handoff = new HandoffConfig();
        handoff.setRoutes(List.of(new HandoffRoute("a", "b")));
        HandoffTeamConfig config = new HandoffTeamConfig(handoff);

        assertThat(config.getHandoff().getRoutes()).containsExactly(new HandoffRoute("a", "b"));
    }

    @Test
    void extraFieldsAreAllowed() {
        HandoffTeamConfig config = new HandoffTeamConfig();
        config.putExtraField("custom_extra", "value");

        assertThat(config.getExtraFields()).containsEntry("custom_extra", "value");
    }

    private static AgentCard agentCard(String id) {
        return new AgentCard(id, id, "agent " + id);
    }

    private static boolean hasMethodNamed(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
