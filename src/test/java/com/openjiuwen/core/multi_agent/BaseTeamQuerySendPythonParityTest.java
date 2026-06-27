/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent;

import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code test_team_query_send} in
 * {@code tests/unit_tests/multi_agent/team/test_team_query_send.py}.</p>
 */
class BaseTeamQuerySendPythonParityTest {

    @TestFactory
    Collection<DynamicTest> pythonBaseTeamQuerySendCases() {
        return List.of(
                dynamic("TestBaseTeamQuery::test_list_agents_empty", this::listAgentsEmpty),
                dynamic("TestBaseTeamQuery::test_list_agents_returns_all_ids", this::listAgentsReturnsAllIds),
                dynamic("TestBaseTeamQuery::test_get_agent_card_returns_card", this::getAgentCardReturnsCard),
                dynamic("TestBaseTeamQuery::test_get_agent_card_returns_none_for_unknown",
                        this::getAgentCardReturnsNoneForUnknown),
                dynamic("TestBaseTeamQuery::test_get_agent_count", this::getAgentCount),
                dynamic("TestBaseTeamSubscription::test_subscribe_delegates_to_runtime",
                        this::subscribeDelegatesToRuntime),
                dynamic("TestBaseTeamSubscription::test_unsubscribe_delegates_to_runtime",
                        this::unsubscribeDelegatesToRuntime),
                dynamic("TestBaseTeamSend::test_send_raises_when_sender_not_in_team",
                        this::sendRaisesWhenSenderNotInTeam),
                dynamic("TestBaseTeamSend::test_send_raises_when_recipient_not_in_team",
                        this::sendRaisesWhenRecipientNotInTeam),
                dynamic("TestBaseTeamSend::test_send_delegates_to_runtime", this::sendDelegatesToRuntime),
                dynamic("TestBaseTeamSend::test_send_passes_session_id_and_timeout",
                        this::sendPassesSessionIdAndTimeout),
                dynamic("TestBaseTeamPublish::test_publish_raises_when_sender_not_in_team",
                        this::publishRaisesWhenSenderNotInTeam),
                dynamic("TestBaseTeamPublish::test_publish_delegates_to_runtime", this::publishDelegatesToRuntime),
                dynamic("TestBaseTeamPublish::test_publish_passes_session_id", this::publishPassesSessionId),
                dynamic("TestBaseTeamInvokeStream::test_invoke_returns_result", this::invokeReturnsResult),
                dynamic("TestBaseTeamInvokeStream::test_stream_yields_chunks", this::streamYieldsChunks)
        );
    }

    private void listAgentsEmpty() {
        ConcreteTeam team = buildTeam();

        assertThat(team.listAgents()).isEmpty();
    }

    private void listAgentsReturnsAllIds() {
        ConcreteTeam team = buildTeam();
        addAgent(team, "a1");
        addAgent(team, "a2");

        assertThat(team.listAgents()).containsExactlyInAnyOrder("a1", "a2");
    }

    private void getAgentCardReturnsCard() {
        ConcreteTeam team = buildTeam();
        AgentCard card = addAgent(team, "a1");

        assertThat(team.getAgentCard("a1")).isSameAs(card);
    }

    private void getAgentCardReturnsNoneForUnknown() {
        ConcreteTeam team = buildTeam();

        assertThat(team.getAgentCard("ghost")).isNull();
    }

    private void getAgentCount() {
        ConcreteTeam team = buildTeam();

        assertThat(team.getAgentCount()).isZero();
        addAgent(team, "a1");

        assertThat(team.getAgentCount()).isEqualTo(1);
    }

    private void subscribeDelegatesToRuntime() {
        RecordingRuntime runtime = new RecordingRuntime();
        ConcreteTeam team = buildTeam(runtime);

        team.subscribe("agent_a", "events").toCompletableFuture().join();

        assertThat(runtime.subscribeCalls()).containsExactly(new TopicCall("agent_a", "events"));
    }

    private void unsubscribeDelegatesToRuntime() {
        RecordingRuntime runtime = new RecordingRuntime();
        ConcreteTeam team = buildTeam(runtime);

        team.unsubscribe("agent_a", "events").toCompletableFuture().join();

        assertThat(runtime.unsubscribeCalls()).containsExactly(new TopicCall("agent_a", "events"));
    }

    private void sendRaisesWhenSenderNotInTeam() {
        ConcreteTeam team = buildTeam();
        addAgent(team, "agent_b");

        assertThatThrownBy(() -> team.send("hello", "agent_b", "unknown_sender"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown_sender");
    }

    private void sendRaisesWhenRecipientNotInTeam() {
        ConcreteTeam team = buildTeam();
        addAgent(team, "agent_a");

        assertThatThrownBy(() -> team.send("hello", "unknown_recipient", "agent_a"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown_recipient");
    }

    private void sendDelegatesToRuntime() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.setSendResult("pong");
        ConcreteTeam team = buildTeam(runtime);
        addAgent(team, "agent_a");
        addAgent(team, "agent_b");

        Object result = team.send("ping", "agent_b", "agent_a").toCompletableFuture().join();

        assertThat(result).isEqualTo("pong");
        assertThat(runtime.sendCalls()).containsExactly(new SendCall("ping", "agent_b", "agent_a", null, null));
    }

    private void sendPassesSessionIdAndTimeout() {
        RecordingRuntime runtime = new RecordingRuntime();
        ConcreteTeam team = buildTeam(runtime);
        addAgent(team, "agent_a");
        addAgent(team, "agent_b");

        team.send("msg", "agent_b", "agent_a", "sess-1", 10.0d).toCompletableFuture().join();

        assertThat(runtime.sendCalls())
                .containsExactly(new SendCall("msg", "agent_b", "agent_a", "sess-1", 10.0d));
    }

    private void publishRaisesWhenSenderNotInTeam() {
        ConcreteTeam team = buildTeam();

        assertThatThrownBy(() -> team.publish("event", "events", "unknown_sender"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown_sender");
    }

    private void publishDelegatesToRuntime() {
        RecordingRuntime runtime = new RecordingRuntime();
        ConcreteTeam team = buildTeam(runtime);
        addAgent(team, "agent_a");

        team.publish("event", "code_events", "agent_a").toCompletableFuture().join();

        assertThat(runtime.publishCalls()).containsExactly(new PublishCall("event", "code_events", "agent_a", null));
    }

    private void publishPassesSessionId() {
        RecordingRuntime runtime = new RecordingRuntime();
        ConcreteTeam team = buildTeam(runtime);
        addAgent(team, "agent_a");

        team.publish("evt", "t", "agent_a", "sess-xyz").toCompletableFuture().join();

        assertThat(runtime.publishCalls()).containsExactly(new PublishCall("evt", "t", "agent_a", "sess-xyz"));
    }

    @SuppressWarnings("unchecked")
    private void invokeReturnsResult() {
        ConcreteTeam team = buildTeam();

        Map<String, Object> result = (Map<String, Object>) team.invoke("test_message").toCompletableFuture().join();

        assertThat(result).containsEntry("result", "ok").containsEntry("message", "test_message");
    }

    @SuppressWarnings("unchecked")
    private void streamYieldsChunks() {
        ConcreteTeam team = buildTeam();

        List<Object> chunks = team.stream("test_message").toList();

        assertThat(chunks).hasSize(1);
        assertThat((Map<String, Object>) chunks.get(0)).containsEntry("chunk", "test_message");
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static ConcreteTeam buildTeam() {
        return buildTeam(null);
    }

    private static ConcreteTeam buildTeam(TeamRuntime runtime) {
        return new ConcreteTeam(makeTeamCard("g"), null, runtime);
    }

    private static TeamCard makeTeamCard(String teamId) {
        return new TeamCard(teamId, teamId, "");
    }

    private static AgentCard makeAgentCard(String agentId) {
        return new AgentCard(agentId, agentId, "");
    }

    private static AgentCard addAgent(BaseTeam team, String agentId) {
        AgentCard card = makeAgentCard(agentId);
        team.addAgent(card, ignored -> new Object());
        return card;
    }

    /**
     * <p>Mirrors Python's {@code ConcreteTeam} in
     * {@code tests/unit_tests/multi_agent/team/test_team_query_send.py}.</p>
     */
    private static final class ConcreteTeam extends BaseTeam {

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

    /**
     * <p>Mirrors Python's runtime {@code AsyncMock} delegation checks in
     * {@code tests/unit_tests/multi_agent/team/test_team_query_send.py}.</p>
     */
    private static final class RecordingRuntime extends TeamRuntime {

        private final Map<String, AgentCard> cards = new LinkedHashMap<>();
        private final List<TopicCall> subscribeCalls = new java.util.ArrayList<>();
        private final List<TopicCall> unsubscribeCalls = new java.util.ArrayList<>();
        private final List<SendCall> sendCalls = new java.util.ArrayList<>();
        private final List<PublishCall> publishCalls = new java.util.ArrayList<>();
        private Object sendResult = "ok";

        @Override
        public void registerAgent(AgentCard card, Function<AgentCard, ?> provider) {
            cards.put(card.getId(), card);
        }

        @Override
        public void registerAgent(AgentCard card, Supplier<?> provider) {
            cards.put(card.getId(), card);
        }

        @Override
        public boolean hasAgent(String agentId) {
            return cards.containsKey(agentId);
        }

        @Override
        public AgentCard getAgentCard(String agentId) {
            return cards.get(agentId);
        }

        @Override
        public List<String> listAgents() {
            return List.copyOf(cards.keySet());
        }

        @Override
        public int getAgentCount() {
            return cards.size();
        }

        @Override
        public CompletableFuture<Void> subscribe(String agentId, String topic) {
            subscribeCalls.add(new TopicCall(agentId, topic));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> unsubscribe(String agentId, String topic) {
            unsubscribeCalls.add(new TopicCall(agentId, topic));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Object> send(Object message, String recipient, String sender, String sessionId,
                Double timeout) {
            sendCalls.add(new SendCall(message, recipient, sender, sessionId, timeout));
            return CompletableFuture.completedFuture(sendResult);
        }

        @Override
        public CompletableFuture<Void> publish(Object message, String topicId, String sender, String sessionId) {
            publishCalls.add(new PublishCall(message, topicId, sender, sessionId));
            return CompletableFuture.completedFuture(null);
        }

        private void setSendResult(Object sendResult) {
            this.sendResult = sendResult;
        }

        private List<TopicCall> subscribeCalls() {
            return subscribeCalls;
        }

        private List<TopicCall> unsubscribeCalls() {
            return unsubscribeCalls;
        }

        private List<SendCall> sendCalls() {
            return sendCalls;
        }

        private List<PublishCall> publishCalls() {
            return publishCalls;
        }
    }

    /**
     * <p>Mirrors Python's subscription mock call tuple in
     * {@code tests/unit_tests/multi_agent/team/test_team_query_send.py}.</p>
     */
    private record TopicCall(String agentId, String topic) {
    }

    /**
     * <p>Mirrors Python's {@code team.runtime.send.call_args} tuple in
     * {@code tests/unit_tests/multi_agent/team/test_team_query_send.py}.</p>
     */
    private record SendCall(Object message, String recipient, String sender, String sessionId, Double timeout) {
    }

    /**
     * <p>Mirrors Python's {@code team.runtime.publish.call_args} tuple in
     * {@code tests/unit_tests/multi_agent/team/test_team_query_send.py}.</p>
     */
    private record PublishCall(Object message, String topicId, String sender, String sessionId) {
    }
}
