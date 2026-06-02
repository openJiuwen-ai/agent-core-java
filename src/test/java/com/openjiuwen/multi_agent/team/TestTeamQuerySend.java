/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BaseTeam query, subscription, send, publish, invoke and stream behavior.
 *
 * <p>Mirrors Python's {@code test_team_query_send.py} in
 * {@code tests.unit_tests.multi_agent.team}.</p>
 */
class TestTeamQuerySend {

    static class ConcreteTeam extends BaseTeam {
        ConcreteTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
            super(card, config, runtime);
        }

        ConcreteTeam(TeamCard card, TeamConfig config) {
            super(card, config);
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

    static class RecordingRuntime extends TeamRuntime {
        String subscribedAgentId;
        String subscribedTopic;
        String unsubscribedAgentId;
        String unsubscribedTopic;
        Object sentMessage;
        String sentRecipient;
        String sentSender;
        String sentSessionId;
        Double sentTimeout;
        Object sendResult = "pong";
        Object publishedMessage;
        String publishedTopic;
        String publishedSender;
        String publishedSessionId;

        @Override
        public void subscribe(String agentId, String topicPattern) {
            subscribedAgentId = agentId;
            subscribedTopic = topicPattern;
        }

        @Override
        public void unsubscribe(String agentId, String topicPattern) {
            unsubscribedAgentId = agentId;
            unsubscribedTopic = topicPattern;
        }

        @Override
        public CompletableFuture<Object> send(
                Object message,
                String recipient,
                String sender,
                String sessionId,
                Double timeout
        ) {
            sentMessage = message;
            sentRecipient = recipient;
            sentSender = sender;
            sentSessionId = sessionId;
            sentTimeout = timeout;
            return CompletableFuture.completedFuture(sendResult);
        }

        @Override
        public CompletableFuture<Void> publish(Object message, String topicId, String sender, String sessionId) {
            publishedMessage = message;
            publishedTopic = topicId;
            publishedSender = sender;
            publishedSessionId = sessionId;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static TeamCard teamCard(String teamId) {
        return TeamCard.builder().id(teamId).name(teamId).description("").build();
    }

    private static AgentCard agentCard(String agentId) {
        return AgentCard.builder().id(agentId).name(agentId).description("").build();
    }

    private static ConcreteTeam buildTeam() {
        return new ConcreteTeam(teamCard("g"), null);
    }

    private static ConcreteTeam buildTeam(TeamRuntime runtime) {
        return new ConcreteTeam(teamCard("g"), null, runtime);
    }

    private static AgentCard addAgent(BaseTeam team, String agentId) {
        AgentCard card = agentCard(agentId);
        team.addAgent(card, () -> (Function<Object, Object>) message -> message);
        return card;
    }

    @Nested
    class TestBaseTeamQuery {
        @Test
        void testListAgentsEmpty() {
            assertEquals(java.util.List.of(), buildTeam().listAgents());
        }

        @Test
        void testListAgentsReturnsAllIds() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "a1");
            addAgent(team, "a2");

            assertEquals(Set.of("a1", "a2"), Set.copyOf(team.listAgents()));
        }

        @Test
        void testGetAgentCardReturnsCard() {
            ConcreteTeam team = buildTeam();
            AgentCard card = addAgent(team, "a1");

            assertSame(card, team.getAgentCard("a1"));
        }

        @Test
        void testGetAgentCardReturnsNoneForUnknown() {
            assertNull(buildTeam().getAgentCard("ghost"));
        }

        @Test
        void testGetAgentCount() {
            ConcreteTeam team = buildTeam();

            assertEquals(0, team.getAgentCount());
            addAgent(team, "a1");
            assertEquals(1, team.getAgentCount());
        }
    }

    @Nested
    class TestBaseTeamSubscription {
        @Test
        void testSubscribeDelegatesToRuntime() {
            RecordingRuntime runtime = new RecordingRuntime();
            ConcreteTeam team = buildTeam(runtime);

            team.subscribe("agent_a", "events");

            assertEquals("agent_a", runtime.subscribedAgentId);
            assertEquals("events", runtime.subscribedTopic);
        }

        @Test
        void testUnsubscribeDelegatesToRuntime() {
            RecordingRuntime runtime = new RecordingRuntime();
            ConcreteTeam team = buildTeam(runtime);

            team.unsubscribe("agent_a", "events");

            assertEquals("agent_a", runtime.unsubscribedAgentId);
            assertEquals("events", runtime.unsubscribedTopic);
        }
    }

    @Nested
    class TestBaseTeamSend {
        @Test
        void testSendRaisesWhenSenderNotInTeam() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_b");

            assertThrows(Exception.class, () -> team.send("hello", "agent_b", "unknown_sender").join());
        }

        @Test
        void testSendRaisesWhenRecipientNotInTeam() {
            ConcreteTeam team = buildTeam();
            addAgent(team, "agent_a");

            assertThrows(Exception.class, () -> team.send("hello", "unknown_recipient", "agent_a").join());
        }

        @Test
        void testSendDelegatesToRuntime() {
            RecordingRuntime runtime = new RecordingRuntime();
            ConcreteTeam team = buildTeam(runtime);
            addAgent(team, "agent_a");
            addAgent(team, "agent_b");
            runtime.sendResult = "pong";

            Object result = team.send("ping", "agent_b", "agent_a").join();

            assertEquals("pong", result);
            assertEquals("ping", runtime.sentMessage);
            assertEquals("agent_b", runtime.sentRecipient);
            assertEquals("agent_a", runtime.sentSender);
        }

        @Test
        void testSendPassesSessionIdAndTimeout() {
            RecordingRuntime runtime = new RecordingRuntime();
            ConcreteTeam team = buildTeam(runtime);
            addAgent(team, "agent_a");
            addAgent(team, "agent_b");

            team.send("msg", "agent_b", "agent_a", "sess-1", 10.0).join();

            assertEquals("sess-1", runtime.sentSessionId);
            assertEquals(10.0, runtime.sentTimeout);
        }
    }

    @Nested
    class TestBaseTeamPublish {
        @Test
        void testPublishRaisesWhenSenderNotInTeam() {
            assertThrows(Exception.class, () -> buildTeam().publish("event", "events", "unknown_sender").join());
        }

        @Test
        void testPublishDelegatesToRuntime() {
            RecordingRuntime runtime = new RecordingRuntime();
            ConcreteTeam team = buildTeam(runtime);
            addAgent(team, "agent_a");

            team.publish("event", "code_events", "agent_a").join();

            assertEquals("event", runtime.publishedMessage);
            assertEquals("code_events", runtime.publishedTopic);
            assertEquals("agent_a", runtime.publishedSender);
        }

        @Test
        void testPublishPassesSessionId() {
            RecordingRuntime runtime = new RecordingRuntime();
            ConcreteTeam team = buildTeam(runtime);
            addAgent(team, "agent_a");

            team.publish("evt", "t", "agent_a", "sess-xyz").join();

            assertEquals("sess-xyz", runtime.publishedSessionId);
        }
    }

    @Nested
    class TestBaseTeamInvokeStream {
        @Test
        void testInvokeReturnsResult() {
            ConcreteTeam team = buildTeam();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) team.invoke("test_message").join();

            assertEquals("ok", result.get("result"));
            assertEquals("test_message", result.get("message"));
        }

        @Test
        void testStreamYieldsChunks() {
            ConcreteTeam team = buildTeam();

            java.util.List<Object> chunks = team.stream("test_message").toList();

            assertEquals(1, chunks.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> chunk = (Map<String, Object>) chunks.get(0);
            assertEquals("test_message", chunk.get("chunk"));
        }
    }
}
