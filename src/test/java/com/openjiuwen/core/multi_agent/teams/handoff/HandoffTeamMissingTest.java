/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Missing parity tests for the handoff team public and protected behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.builtin_teams.handoff.test_handoff_team} in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_team.py}.</p>
 */
class HandoffTeamMissingTest {

    @Test
    void cardStored() {
        TeamCard card = teamCard("t1");
        HandoffTeam team = new HandoffTeam(card);

        assertThat(team.getCard()).isSameAs(card);
    }

    @Test
    void defaultConfigIsHandoffTeamConfig() {
        assertThat(new HandoffTeam(teamCard()).getConfig()).isInstanceOf(HandoffTeamConfig.class);
    }

    @Test
    void customConfigStored() {
        HandoffTeamConfig config = new HandoffTeamConfig();
        HandoffTeam team = new HandoffTeam(teamCard(), config);

        assertThat(team.getConfig()).isSameAs(config);
    }

    @Test
    void runtimeCreated() {
        assertThat(new HandoffTeam(teamCard()).getRuntime()).isInstanceOf(TeamRuntime.class);
    }

    @Test
    void internalAgentsNotReadyInitially() {
        assertThat(new TestableHandoffTeam(teamCard()).isInternalAgentsReady()).isFalse();
    }

    @Test
    void coordinatorRegistryEmptyInitially() {
        assertThat(new TestableHandoffTeam(teamCard()).coordinatorRegistry()).isEmpty();
    }

    @Test
    void addAgentRegistersAgentInRuntime() {
        HandoffTeam team = new HandoffTeam(teamCard());

        team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));

        assertThat(team.getRuntime().hasAgent("a")).isTrue();
    }

    @Test
    void addAgentReturnsSelf() {
        HandoffTeam team = new HandoffTeam(teamCard());

        HandoffTeam result = team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));

        assertThat(result).isSameAs(team);
    }

    @Test
    void duplicateAgentNoError() {
        HandoffTeam team = new HandoffTeam(teamCard());

        team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));
        team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));

        assertThat(team.getAgentCount()).isEqualTo(1);
    }

    @Test
    void addMultipleAgentsIncrementsCount() {
        HandoffTeam team = new HandoffTeam(teamCard());

        team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));
        team.addAgent(agentCard("b"), () -> new StubAgent(agentCard("b")));

        assertThat(team.getAgentCount()).isEqualTo(2);
    }

    @Test
    void agentCardAppearsInTeamCard() {
        HandoffTeam team = new HandoffTeam(teamCard());

        team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));

        assertThat(team.getCard().getAgentCards()).extracting(AgentCard::getId).contains("a");
    }

    @Test
    void addAgentResetsInternalReadyFlag() {
        TestableHandoffTeam team = new TestableHandoffTeam(teamCard());
        team.setInternalAgentsReady(true);

        team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")));

        assertThat(team.isInternalAgentsReady()).isFalse();
    }

    @Test
    void addAgentSupportsMethodChaining() {
        HandoffTeam team = new HandoffTeam(teamCard());

        HandoffTeam result = team.addAgent(agentCard("a"), () -> new StubAgent(agentCard("a")))
                .addAgent(agentCard("b"), () -> new StubAgent(agentCard("b")));

        assertThat(result).isSameAs(team);
        assertThat(team.getAgentCount()).isEqualTo(2);
    }

    @Test
    void usesConfiguredStartAgent() {
        AgentCard cardA = agentCard("a");
        HandoffConfig handoffConfig = new HandoffConfig();
        handoffConfig.setStartAgent(cardA);
        HandoffTeamConfig config = new HandoffTeamConfig(handoffConfig);
        TestableHandoffTeam team = new TestableHandoffTeam(teamCard(), config);

        team.addAgent(cardA, () -> new StubAgent(cardA));
        team.addAgent(agentCard("b"), () -> new StubAgent(agentCard("b")));

        assertThat(team.startAgentId()).isEqualTo("a");
    }

    @Test
    void defaultsToFirstAddedAgent() {
        TestableHandoffTeam team = new TestableHandoffTeam(teamCard());

        team.addAgent(agentCard("x"), () -> new StubAgent(agentCard("x")));
        team.addAgent(agentCard("y"), () -> new StubAgent(agentCard("y")));

        assertThat(team.startAgentId()).isEqualTo("x");
    }

    @Test
    void ensureInternalAgentsSetsReadyFlag() {
        TestableHandoffTeam team = makeTeam("a", "b");

        team.ensureInternalAgentsNow();

        assertThat(team.isInternalAgentsReady()).isTrue();
    }

    @Test
    void ensureInternalAgentsIsIdempotent() {
        TestableHandoffTeam team = makeTeam("a", "b");

        team.ensureInternalAgentsNow();
        team.ensureInternalAgentsNow();

        assertThat(team.isInternalAgentsReady()).isTrue();
    }

    @Test
    void ensureInternalAgentsRegistersEndpointAgents() {
        TestableHandoffTeam team = makeTeam("a", "b");

        team.ensureInternalAgentsNow();

        assertThat(team.getRuntime().hasAgent("__handoff_ep_team1_a")).isTrue();
        assertThat(team.getRuntime().hasAgent("__handoff_ep_team1_b")).isTrue();
    }

    @Test
    void invokeDelegatesToRunChain() {
        TestableHandoffTeam team = makeTeam("a");
        team.setRunChainResult(Map.of("ok", true));

        Object result = team.invoke("hello").toCompletableFuture().join();

        assertThat(team.getRunChainCallCount()).isEqualTo(1);
        assertThat(team.getLastRunChainMessage()).isEqualTo("hello");
        assertThat(result).isEqualTo(Map.of("ok", true));
    }

    @Test
    void invokeReturnsRunChainResult() {
        TestableHandoffTeam team = makeTeam("a");
        team.setRunChainResult(Map.of("answer", 42));

        Object result = team.invoke("q").toCompletableFuture().join();

        assertThat(result).isEqualTo(Map.of("answer", 42));
    }

    @Test
    void invokeWithMapMessage() {
        TestableHandoffTeam team = makeTeam("a");
        team.setRunChainResult("ok");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("query", "hello");

        Object result = team.invoke(message).toCompletableFuture().join();

        assertThat(team.getLastRunChainMessage()).isSameAs(message);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void streamCompletesWithoutError() {
        TestableHandoffTeam team = makeTeam("a");
        team.setRunChainResult(Map.of("out", "c"));

        List<Object> chunks = team.stream(Map.of("q", "hi")).toList();

        assertThat(chunks).isInstanceOf(List.class);
    }

    @Test
    void streamWithStringMessage() {
        TestableHandoffTeam team = makeTeam("a");
        team.setRunChainResult("done");

        List<Object> chunks = team.stream("plain").toList();

        assertThat(chunks).isInstanceOf(List.class);
    }

    private static TestableHandoffTeam makeTeam(String... agentIds) {
        TestableHandoffTeam team = new TestableHandoffTeam(teamCard());
        for (String agentId : agentIds) {
            team.addAgent(agentCard(agentId), () -> new StubAgent(agentCard(agentId)));
        }
        return team;
    }

    private static TeamCard teamCard() {
        return teamCard("team1");
    }

    private static TeamCard teamCard(String id) {
        return new TeamCard(id, id, "handoff team");
    }

    private static AgentCard agentCard(String id) {
        return new AgentCard(id, id, "agent " + id);
    }

    private static final class TestableHandoffTeam extends HandoffTeam {
        private Object runChainResult = Map.of();
        private Object lastRunChainMessage;
        private int runChainCallCount;

        private TestableHandoffTeam(TeamCard card) {
            super(card);
        }

        private TestableHandoffTeam(TeamCard card, HandoffTeamConfig config) {
            super(card, config);
        }

        private boolean isInternalAgentsReady() {
            return (boolean) fieldValue("internalAgentsReady");
        }

        private void setInternalAgentsReady(boolean value) {
            setFieldValue("internalAgentsReady", value);
        }

        private Map<?, ?> coordinatorRegistry() {
            return (Map<?, ?>) fieldValue("coordinatorRegistry");
        }

        private String startAgentId() {
            return getStartAgentId();
        }

        private void ensureInternalAgentsNow() {
            ensureInternalAgents().join();
        }

        private void setRunChainResult(Object result) {
            this.runChainResult = result;
        }

        private Object getLastRunChainMessage() {
            return lastRunChainMessage;
        }

        private int getRunChainCallCount() {
            return runChainCallCount;
        }

        @Override
        protected CompletionStage<Object> runChain(Object message, AgentTeamSession session) {
            runChainCallCount++;
            lastRunChainMessage = message;
            return CompletableFuture.completedFuture(runChainResult);
        }

        private Object fieldValue(String fieldName) {
            try {
                Field field = HandoffTeam.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(this);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read HandoffTeam field: " + fieldName, exception);
            }
        }

        private void setFieldValue(String fieldName, Object value) {
            try {
                Field field = HandoffTeam.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(this, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to update HandoffTeam field: " + fieldName, exception);
            }
        }
    }

    private static final class StubAgent extends BaseAgent {
        private StubAgent(AgentCard card) {
            super(card);
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            return CompletableFuture.completedFuture(Map.of("agent", getCard().getId(), "input", inputs));
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            Object event = Map.of("agent", getCard().getId(), "input", inputs);
            return Stream.of(event).iterator();
        }
    }
}
