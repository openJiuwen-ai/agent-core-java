/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTeam;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTeamConfig;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff team.
 *
 * <p>Mirrors Python's {@code test_handoff_team.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffTeam {

    private static final class TestableHandoffTeam extends HandoffTeam {
        TestableHandoffTeam(TeamCard card) {
            super(card);
        }

        TestableHandoffTeam(TeamCard card, HandoffTeamConfig config) {
            super(card, config);
        }

        void ensureInternalAgentsForTest() {
            ensureInternalAgents();
        }

        String startAgentId() {
            return getStartAgentId();
        }
    }

    private static final class StubAgent extends BaseAgent {
        private final Object result;

        StubAgent(AgentCard card, Object result) {
            super(card);
            this.result = result;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            return result instanceof java.util.function.Function<?, ?> function
                    ? invokeFunction(function, inputs)
                    : result;
        }

        @SuppressWarnings("unchecked")
        private Object invokeFunction(java.util.function.Function<?, ?> function, Object inputs) {
            return ((java.util.function.Function<Object, Object>) function).apply(inputs);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(result).iterator();
        }
    }

    private static AgentCard card(String aid) {
        return AgentCard.builder().id(aid).name(aid).description("agent " + aid).build();
    }

    private static TeamCard teamCard(String tid) {
        return TeamCard.builder().id(tid).name(tid).description("handoff team").build();
    }

    private static TestableHandoffTeam makeTeam(String... agents) {
        TestableHandoffTeam team = new TestableHandoffTeam(teamCard("team1"));
        for (String agent : agents) {
            team.addAgent(card(agent), () -> new StubAgent(card(agent), Map.of("agent", agent)));
        }
        return team;
    }

    @Nested
    class TestHandoffTeamInit {
        @Test
        void testCardStored() {
            TeamCard card = teamCard("t1");
            HandoffTeam team = new HandoffTeam(card);
            assertSame(card, team.getCard());
        }

        @Test
        void testDefaultConfigIsHandoffTeamConfig() {
            assertInstanceOf(HandoffTeamConfig.class, new HandoffTeam(teamCard("t")).getConfig());
        }

        @Test
        void testCustomConfigStored() {
            HandoffTeamConfig config = new HandoffTeamConfig();
            assertSame(config, new HandoffTeam(teamCard("t"), config).getConfig());
        }

        @Test
        void testRuntimeCreated() {
            assertInstanceOf(TeamRuntime.class, new HandoffTeam(teamCard("t")).getRuntime());
        }

        @Test
        void testInternalAgentsNotReadyInitially() {
            assertFalse(new HandoffTeam(teamCard("t")).isInternalAgentsReady());
        }

        @Test
        void testCoordinatorRegistryEmptyInitially() {
            assertTrue(new HandoffTeam(teamCard("t")).getCoordinatorRegistry().isEmpty());
        }
    }

    @Nested
    class TestHandoffTeamAddAgent {
        @Test
        void testRegistersAgentInRuntime() {
            HandoffTeam team = new HandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), "ok"));
            assertTrue(team.getRuntime().hasAgent("a"));
        }

        @Test
        void testReturnsSelf() {
            HandoffTeam team = new HandoffTeam(teamCard("t"));
            assertSame(team, team.addAgent(card("a"), () -> new StubAgent(card("a"), "ok")));
        }

        @Test
        void testDuplicateAgentNoError() {
            HandoffTeam team = new HandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), "ok"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), "again"));
            assertEquals(1, team.getAgentCount());
        }

        @Test
        void testAddMultipleAgentsIncrementsCount() {
            HandoffTeam team = new HandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), "ok"));
            team.addAgent(card("b"), () -> new StubAgent(card("b"), "ok"));
            assertEquals(2, team.getAgentCount());
        }

        @Test
        void testAgentCardAppearsInTeamCard() {
            HandoffTeam team = new HandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), "ok"));
            assertTrue(team.getCard().getAgentCards().stream().anyMatch(c -> "a".equals(c.getId())));
        }

        @Test
        void testAddAgentResetsInternalReadyFlag() {
            TestableHandoffTeam team = makeTeam("a");
            team.ensureInternalAgentsForTest();
            assertTrue(team.isInternalAgentsReady());
            team.addAgent(card("b"), () -> new StubAgent(card("b"), "ok"));
            assertFalse(team.isInternalAgentsReady());
        }

        @Test
        void testMethodChaining() {
            HandoffTeam team = new HandoffTeam(teamCard("t"));
            BaseTeam result = team.addAgent(card("a"), () -> new StubAgent(card("a"), "ok"))
                    .addAgent(card("b"), () -> new StubAgent(card("b"), "ok"));
            assertSame(team, result);
            assertEquals(2, team.getAgentCount());
        }
    }

    @Nested
    class TestGetStartAgentId {
        @Test
        void testUsesConfiguredStartAgent() {
            AgentCard start = card("a");
            HandoffTeamConfig config = new HandoffTeamConfig(HandoffConfig.builder().startAgent(start).build());
            TestableHandoffTeam team = new TestableHandoffTeam(teamCard("t"), config);
            team.addAgent(start, () -> new StubAgent(start, "ok"));
            team.addAgent(card("b"), () -> new StubAgent(card("b"), "ok"));
            assertEquals("a", team.startAgentId());
        }

        @Test
        void testDefaultsToFirstAddedAgent() {
            TestableHandoffTeam team = new TestableHandoffTeam(teamCard("t"));
            team.addAgent(card("x"), () -> new StubAgent(card("x"), "ok"));
            team.addAgent(card("y"), () -> new StubAgent(card("y"), "ok"));
            assertEquals("x", team.startAgentId());
        }
    }

    @Nested
    class TestEnsureInternalAgents {
        @Test
        void testSetsReadyFlag() {
            TestableHandoffTeam team = makeTeam("a", "b");
            team.ensureInternalAgentsForTest();
            assertTrue(team.isInternalAgentsReady());
        }

        @Test
        void testIdempotentSecondCallNoop() {
            TestableHandoffTeam team = makeTeam("a", "b");
            team.ensureInternalAgentsForTest();
            int count = team.getAgentCount();
            team.ensureInternalAgentsForTest();
            assertEquals(count, team.getAgentCount());
        }

        @Test
        void testRegistersEndpointAgents() {
            TestableHandoffTeam team = makeTeam("a", "b");
            team.ensureInternalAgentsForTest();
            assertTrue(team.getRuntime().hasAgent("__handoff_ep_team1_a"));
            assertTrue(team.getRuntime().hasAgent("__handoff_ep_team1_b"));
        }
    }

    @Nested
    class TestHandoffTeamInvoke {
        @Test
        void testDelegatesToRunChain() throws Exception {
            TestableHandoffTeam team = makeTeam("a");
            CompletableFuture<Object> result = team.invoke("hello");
            assertEquals(Map.of("agent", "a"), result.get());
        }

        @Test
        void testReturnsRunChainResult() throws Exception {
            TestableHandoffTeam team = new TestableHandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), Map.of("answer", 42)));
            assertEquals(Map.of("answer", 42), team.invoke("q").get());
        }

        @Test
        void testInvokeWithDictMessage() throws Exception {
            TestableHandoffTeam team = new TestableHandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), (java.util.function.Function<Object, Object>) input -> input));
            assertEquals(Map.of("query", "hello"), team.invoke(Map.of("query", "hello")).get());
        }
    }

    @Nested
    class TestHandoffTeamStream {
        @Test
        void testStreamCompletesWithoutError() {
            TestableHandoffTeam team = makeTeam("a");
            assertEquals(List.of(Map.of("agent", "a")), team.stream(Map.of("q", "hi")).toList());
        }

        @Test
        void testStreamWithStringMessage() {
            TestableHandoffTeam team = new TestableHandoffTeam(teamCard("t"));
            team.addAgent(card("a"), () -> new StubAgent(card("a"), "done"));
            assertEquals(List.of("done"), team.stream("plain").toList());
        }
    }
}
