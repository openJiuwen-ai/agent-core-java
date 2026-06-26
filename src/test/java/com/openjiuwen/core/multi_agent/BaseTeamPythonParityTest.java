/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent;

import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.RuntimeConfig;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code test_team} in
 * {@code tests/unit_tests/multi_agent/team/test_team.py}.
 */
@DisplayName("Python parity for BaseTeam core behavior")
class BaseTeamPythonParityTest {

    @Nested
    @DisplayName("TeamConfig")
    class TeamConfigTests {

        @Test
        void testDefaultValues() {
            TeamConfig config = new TeamConfig();

            assertThat(config.getMaxAgents()).isEqualTo(10);
            assertThat(config.getMaxConcurrentMessages()).isEqualTo(100);
            assertThat(config.getMessageTimeout()).isEqualTo(30.0);
        }

        @Test
        void testConfigureMaxAgentsChaining() {
            TeamConfig config = new TeamConfig();

            TeamConfig result = config.configureMaxAgents(5);

            assertThat(result).isSameAs(config);
            assertThat(config.getMaxAgents()).isEqualTo(5);
        }

        @Test
        void testConfigureTimeoutChaining() {
            TeamConfig config = new TeamConfig();

            TeamConfig result = config.configureTimeout(60.0);

            assertThat(result).isSameAs(config);
            assertThat(config.getMessageTimeout()).isEqualTo(60.0);
        }

        @Test
        void testConfigureConcurrencyChaining() {
            TeamConfig config = new TeamConfig();

            TeamConfig result = config.configureConcurrency(50);

            assertThat(result).isSameAs(config);
            assertThat(config.getMaxConcurrentMessages()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("BaseTeam init")
    class BaseTeamInitTests {

        @Test
        void testCardStored() {
            TeamCard card = makeTeamCard("g1");

            ConcreteTeam team = new ConcreteTeam(card);

            assertThat(team.getCard()).isSameAs(card);
        }

        @Test
        void testTeamIdTakenFromCardName() {
            TeamCard card = makeTeamCard("g1");

            ConcreteTeam team = new ConcreteTeam(card);

            assertThat(team.getTeamId()).isEqualTo(card.getName());
        }

        @Test
        void testDefaultConfigCreatedWhenNotProvided() {
            ConcreteTeam team = buildTeam();

            assertThat(team.getConfig()).isInstanceOf(TeamConfig.class);
        }

        @Test
        void testCustomConfigStored() {
            TeamConfig config = new TeamConfig();
            config.setMaxAgents(3);

            ConcreteTeam team = buildTeam("test_team", config, null);

            assertThat(team.getConfig().getMaxAgents()).isEqualTo(3);
        }

        @Test
        void testDefaultRuntimeCreatedWhenNotProvided() {
            ConcreteTeam team = buildTeam();

            assertThat(team.getRuntime()).isInstanceOf(TeamRuntime.class);
        }

        @Test
        void testCustomRuntimeStored() {
            RuntimeConfig runtimeConfig = new RuntimeConfig();
            runtimeConfig.setTeamId("custom_rt");
            TeamRuntime runtime = new TeamRuntime(runtimeConfig);

            ConcreteTeam team = buildTeam("test_team", null, runtime);

            assertThat(team.getRuntime()).isSameAs(runtime);
        }

        @Test
        void testConfigureReturnsSelf() {
            ConcreteTeam team = buildTeam();
            TeamConfig config = new TeamConfig();
            config.setMaxAgents(7);

            BaseTeam result = team.configure(config);

            assertThat(result).isSameAs(team);
            assertThat(team.getConfig().getMaxAgents()).isEqualTo(7);
        }

        @Test
        void testBaseTeamIsAbstract() {
            assertThat(Modifier.isAbstract(BaseTeam.class.getModifiers())).isTrue();
        }
    }

    @Nested
    @DisplayName("BaseTeam addAgent")
    class BaseTeamAddAgentTests {

        @Test
        void testAddAgentRegistersInRuntime() {
            ConcreteTeam team = buildTeam();

            addAgent(team, "agent_a");

            assertThat(team.getRuntime().hasAgent("agent_a")).isTrue();
        }

        @Test
        void testAddAgentAppendsCardToTeamCard() {
            ConcreteTeam team = buildTeam();

            addAgent(team, "agent_a");

            assertThat(team.getCard().getAgentCards())
                    .extracting(AgentCard::getId)
                    .contains("agent_a");
        }

        @Test
        void testAddAgentReturnsSelfForChaining() {
            ConcreteTeam team = buildTeam();
            AgentCard card = makeAgentCard("agent_a");

            BaseTeam result = team.addAgent(card, ignored -> new Object());

            assertThat(result).isSameAs(team);
        }

        @Test
        void testAddAgentIncrementsCount() {
            ConcreteTeam team = buildTeam();

            addAgent(team, "a1");
            addAgent(team, "a2");

            assertThat(team.getAgentCount()).isEqualTo(2);
        }

        @Test
        void testAddDuplicateAgentReturnsSelfWithWarning() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");
            AgentCard duplicate = makeAgentCard("agent_a");

            BaseTeam result = team.addAgent(duplicate, ignored -> new Object());

            assertThat(result).isSameAs(team);
            assertThat(team.getAgentCount()).isEqualTo(1);
        }

        @Test
        void testAddAgentBeyondMaxRaises() {
            TeamConfig config = new TeamConfig();
            config.setMaxAgents(2);
            ConcreteTeam team = buildTeam("test_team", config, null);
            addAgent(team, "a1");
            addAgent(team, "a2");
            AgentCard card = makeAgentCard("a3");

            assertThatThrownBy(() -> team.addAgent(card, ignored -> new Object()))
                    .hasMessageContaining("Agent count exceeds max_agents (2)");
        }
    }

    @Nested
    @DisplayName("BaseTeam removeAgent")
    class BaseTeamRemoveAgentTests {

        @Test
        void testRemoveAgentByIdUnregistersFromRuntime() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            team.removeAgent("agent_a");

            assertThat(team.getRuntime().hasAgent("agent_a")).isFalse();
        }

        @Test
        void testRemoveAgentByIdRemovesFromCardList() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            team.removeAgent("agent_a");

            assertThat(team.getCard().getAgentCards())
                    .extracting(AgentCard::getId)
                    .doesNotContain("agent_a");
        }

        @Test
        void testRemoveAgentReturnsSelf() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            BaseTeam result = team.removeAgent("agent_a");

            assertThat(result).isSameAs(team);
        }

        @Test
        void testRemoveNonexistentAgentIsSafe() {
            ConcreteTeam team = buildTeam();

            BaseTeam result = team.removeAgent("ghost");

            assertThat(result).isSameAs(team);
        }

        @Test
        void testRemoveAgentByCard() {
            ConcreteTeam team = buildTeam();
            AgentCard card = addAgent(team, "agent_a");

            BaseTeam result = team.removeAgent(card);

            assertThat(result).isSameAs(team);
            assertThat(team.getRuntime().hasAgent("agent_a")).isFalse();
            assertThat(team.getCard().getAgentCards())
                    .extracting(AgentCard::getId)
                    .doesNotContain("agent_a");
        }

        @Test
        void testRemoveAgentByCardNonexistentIsSafe() {
            ConcreteTeam team = buildTeam();
            AgentCard card = makeAgentCard("ghost");

            BaseTeam result = team.removeAgent(card);

            assertThat(result).isSameAs(team);
        }
    }

    private static ConcreteTeam buildTeam() {
        return buildTeam("test_team", null, null);
    }

    private static ConcreteTeam buildTeam(String teamId, TeamConfig config, TeamRuntime runtime) {
        return new ConcreteTeam(makeTeamCard(teamId), config, runtime);
    }

    private static TeamCard makeTeamCard(String teamId) {
        return new TeamCard(teamId, teamId, "test team");
    }

    private static AgentCard makeAgentCard(String agentId) {
        return new AgentCard(agentId, agentId, "test agent");
    }

    private static AgentCard addAgent(BaseTeam team, String agentId) {
        AgentCard card = makeAgentCard(agentId);
        team.addAgent(card, ignored -> new Object());
        return card;
    }

    /**
     * Mirrors Python's {@code ConcreteTeam} in
     * {@code tests/unit_tests/multi_agent/team/test_team.py}.
     */
    private static final class ConcreteTeam extends BaseTeam {

        private ConcreteTeam(TeamCard card) {
            super(card);
        }

        private ConcreteTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
            super(card, config, runtime);
        }

        @Override
        public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
            return CompletableFuture.completedFuture(Map.of("result", "ok", "message", message));
        }

        @Override
        public Stream<Object> stream(Object message, AgentSessionApi session) {
            return Stream.of(Map.of("chunk", message));
        }
    }
}
