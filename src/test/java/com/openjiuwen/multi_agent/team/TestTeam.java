/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.RuntimeConfig;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BaseTeam config, initialization, add/remove agent behavior.
 *
 * <p>Mirrors Python's {@code test_team.py} in
 * {@code tests.unit_tests.multi_agent.team}.
 */
class TestTeam {

    static class ConcreteTeam extends BaseTeam {
        ConcreteTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
            super(card, config, runtime);
        }

        ConcreteTeam(TeamCard card) {
            super(card);
        }

        @Override
        public CompletableFuture<Object> invoke(Object input) {
            return CompletableFuture.completedFuture(Map.of("result", "ok", "message", input));
        }

        @Override
        public Stream<Object> stream(Object input) {
            return Stream.of(Map.of("chunk", input));
        }
    }

    private static TeamCard teamCard(String teamId) {
        return TeamCard.builder().id(teamId).name(teamId).description("test team").build();
    }

    private static AgentCard agentCard(String agentId) {
        return AgentCard.builder().id(agentId).name(agentId).description("test agent").build();
    }

    private static ConcreteTeam buildTeam() {
        return buildTeam("test_team", null, null);
    }

    private static ConcreteTeam buildTeam(String teamId, TeamConfig config, TeamRuntime runtime) {
        return new ConcreteTeam(teamCard(teamId), config, runtime);
    }

    private static AgentCard addAgent(BaseTeam team, String agentId) {
        AgentCard card = agentCard(agentId);
        team.addAgent(card, () -> (Function<Object, Object>) message -> message);
        return card;
    }

    @Nested
    class TestTeamConfig {
        @Test
        void testDefaultValues() {
            TeamConfig cfg = new TeamConfig();

            assertEquals(10, cfg.getMaxAgents());
            assertEquals(100, cfg.getMaxConcurrentMessages());
            assertEquals(30.0, cfg.getMessageTimeout());
        }

        @Test
        void testConfigureMaxAgentsChaining() {
            TeamConfig cfg = new TeamConfig();

            TeamConfig result = cfg.configureMaxAgents(5);

            assertSame(cfg, result);
            assertEquals(5, cfg.getMaxAgents());
        }

        @Test
        void testConfigureTimeoutChaining() {
            TeamConfig cfg = new TeamConfig();

            TeamConfig result = cfg.configureTimeout(60.0);

            assertSame(cfg, result);
            assertEquals(60.0, cfg.getMessageTimeout());
        }

        @Test
        void testConfigureConcurrencyChaining() {
            TeamConfig cfg = new TeamConfig();

            TeamConfig result = cfg.configureConcurrency(50);

            assertSame(cfg, result);
            assertEquals(50, cfg.getMaxConcurrentMessages());
        }
    }

    @Nested
    class TestBaseTeamInit {
        @Test
        void testCardStored() {
            TeamCard card = teamCard("g1");
            ConcreteTeam team = new ConcreteTeam(card);

            assertSame(card, team.getCard());
        }

        @Test
        void testTeamIdTakenFromCardName() {
            TeamCard card = teamCard("g1");
            ConcreteTeam team = new ConcreteTeam(card);

            assertEquals(card.getName(), team.getTeamId());
        }

        @Test
        void testDefaultConfigCreatedWhenNotProvided() {
            assertInstanceOf(TeamConfig.class, buildTeam().getConfig());
        }

        @Test
        void testCustomConfigStored() {
            TeamConfig cfg = TeamConfig.builder().maxAgents(3).build();
            ConcreteTeam team = buildTeam("test_team", cfg, null);

            assertEquals(3, team.getConfig().getMaxAgents());
        }

        @Test
        void testDefaultRuntimeCreatedWhenNotProvided() {
            assertInstanceOf(TeamRuntime.class, buildTeam().getRuntime());
        }

        @Test
        void testCustomRuntimeStored() {
            TeamRuntime runtime = new TeamRuntime(RuntimeConfig.builder().teamId("custom_rt").build());
            ConcreteTeam team = buildTeam("test_team", null, runtime);

            assertSame(runtime, team.getRuntime());
        }

        @Test
        void testConfigureReturnsSelf() {
            ConcreteTeam team = buildTeam();
            TeamConfig cfg = TeamConfig.builder().maxAgents(7).build();

            BaseTeam result = team.configure(cfg);

            assertSame(team, result);
            assertEquals(7, team.getConfig().getMaxAgents());
        }

        @Test
        void testBaseTeamIsAbstract() throws NoSuchMethodException {
            Constructor<BaseTeam> constructor = BaseTeam.class.getDeclaredConstructor(TeamCard.class);

            assertThrows(InstantiationException.class, () -> constructor.newInstance(teamCard("g1")));
            assertTrue(Modifier.isAbstract(BaseTeam.class.getModifiers()));
        }
    }

    @Nested
    class TestBaseTeamAddAgent {
        @Test
        void testAddAgentRegistersInRuntime() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            assertTrue(team.getRuntime().hasAgent("agent_a"));
        }

        @Test
        void testAddAgentAppendsCardToTeamCard() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            assertTrue(team.getCard().getAgentCards().stream().map(AgentCard::getId).toList().contains("agent_a"));
        }

        @Test
        void testAddAgentReturnsSelfForChaining() {
            ConcreteTeam team = buildTeam();
            AgentCard card = agentCard("agent_a");

            BaseTeam result = team.addAgent(card, () -> (Function<Object, Object>) message -> message);

            assertSame(team, result);
        }

        @Test
        void testAddAgentIncrementsCount() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "a1");
            addAgent(team, "a2");

            assertEquals(2, team.getAgentCount());
        }

        @Test
        void testAddDuplicateAgentReturnsSelfWithWarning() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            BaseTeam result = team.addAgent(agentCard("agent_a"), () -> (Function<Object, Object>) message -> message);

            assertSame(team, result);
            assertEquals(1, team.getAgentCount());
        }

        @Test
        void testAddAgentBeyondMaxRaises() {
            TeamConfig cfg = TeamConfig.builder().maxAgents(2).build();
            ConcreteTeam team = buildTeam("test_team", cfg, null);
            addAgent(team, "a1");
            addAgent(team, "a2");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> team.addAgent(agentCard("a3"), () -> (Function<Object, Object>) message -> message));

            assertEquals("Agent count exceeds max_agents (2)", exception.getMessage());
        }
    }

    @Nested
    class TestBaseTeamRemoveAgent {
        @Test
        void testRemoveAgentByIdUnregistersFromRuntime() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");
            team.removeAgent("agent_a");

            assertFalse(team.getRuntime().hasAgent("agent_a"));
        }

        @Test
        void testRemoveAgentByIdRemovesFromCardList() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");
            team.removeAgent("agent_a");

            assertFalse(team.getCard().getAgentCards().stream().map(AgentCard::getId).toList().contains("agent_a"));
        }

        @Test
        void testRemoveAgentReturnsSelf() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            assertSame(team, team.removeAgent("agent_a"));
        }

        @Test
        void testRemoveNonexistentAgentIsSafe() {
            ConcreteTeam team = buildTeam();

            assertSame(team, team.removeAgent("ghost"));
        }

        @Test
        void testRemoveAgentByCard() {
            ConcreteTeam team = buildTeam();
            AgentCard card = addAgent(team, "agent_a");

            BaseTeam result = team.removeAgent(card);

            assertSame(team, result);
            assertFalse(team.getRuntime().hasAgent("agent_a"));
            assertFalse(team.getCard().getAgentCards().stream().map(AgentCard::getId).toList().contains("agent_a"));
        }

        @Test
        void testRemoveAgentByCardNonexistentIsSafe() {
            ConcreteTeam team = buildTeam();

            assertSame(team, team.removeAgent(agentCard("ghost")));
        }
    }
}
