/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.team.test_team_runtime} in
 * {@code tests/unit_tests/multi_agent/team/test_team_runtime.py}.</p>
 */
class TeamRuntimePythonParityTest {

    @TestFactory
    Collection<DynamicTest> pythonTeamRuntimeCases() {
        return List.of(
                dynamic("TestRuntimeConfig::test_defaults", this::runtimeConfigDefaults),
                dynamic("TestRuntimeConfig::test_custom_team_id", this::runtimeConfigCustomTeamId),
                dynamic("TestRuntimeConfig::test_custom_message_bus", this::runtimeConfigCustomMessageBus),
                dynamic("TestTeamRuntimeLifecycle::test_start_sets_running", this::startSetsRunning),
                dynamic("TestTeamRuntimeLifecycle::test_stop_clears_running", this::stopClearsRunning),
                dynamic("TestTeamRuntimeLifecycle::test_start_is_idempotent", this::startIsIdempotent),
                dynamic("TestTeamRuntimeLifecycle::test_stop_when_not_running_is_safe",
                        this::stopWhenNotRunningIsSafe),
                dynamic("TestTeamRuntimeLifecycle::test_async_context_manager_starts_and_stops",
                        this::closeStartsAndStops),
                dynamic("TestTeamRuntimeAgentRegistration::test_has_agent_false_before_registration",
                        this::hasAgentFalseBeforeRegistration),
                dynamic("TestTeamRuntimeAgentRegistration::test_register_agent_stores_card",
                        this::registerAgentStoresCard),
                dynamic("TestTeamRuntimeAgentRegistration::test_get_agent_card_returns_the_registered_card",
                        this::getAgentCardReturnsRegisteredCard),
                dynamic("TestTeamRuntimeAgentRegistration::test_get_agent_card_returns_none_for_unknown",
                        this::getAgentCardReturnsNoneForUnknown),
                dynamic("TestTeamRuntimeAgentRegistration::test_get_agent_count_increments",
                        this::getAgentCountIncrements),
                dynamic("TestTeamRuntimeAgentRegistration::test_list_agents_returns_all_ids",
                        this::listAgentsReturnsAllIds),
                dynamic("TestTeamRuntimeAgentRegistration::test_unregister_agent_removes_card",
                        this::unregisterAgentRemovesCard),
                dynamic("TestTeamRuntimeAgentRegistration::test_unregister_unknown_agent_returns_none",
                        this::unregisterUnknownAgentReturnsNone),
                dynamic("TestTeamRuntimeAgentRegistration::test_wrap_provider_auto_binds_communicable_agent",
                        this::wrapProviderAutoBindsCommunicableAgent),
                dynamic("TestTeamRuntimeSubscriptions::test_subscribe_increments_count",
                        this::subscribeIncrementsCount),
                dynamic("TestTeamRuntimeSubscriptions::test_unsubscribe_decrements_count",
                        this::unsubscribeDecrementsCount),
                dynamic("TestTeamRuntimeSubscriptions::test_list_subscriptions_all", this::listSubscriptionsAll),
                dynamic("TestTeamRuntimeSubscriptions::test_list_subscriptions_filtered_by_agent",
                        this::listSubscriptionsFilteredByAgent),
                dynamic("TestTeamRuntimeSubscriptions::test_subscribe_empty_agent_id_raises",
                        this::subscribeEmptyAgentIdRaises),
                dynamic("TestTeamRuntimeSubscriptions::test_subscribe_empty_topic_raises",
                        this::subscribeEmptyTopicRaises),
                dynamic("TestTeamRuntimeSubscriptions::test_unsubscribe_empty_agent_id_raises",
                        this::unsubscribeEmptyAgentIdRaises),
                dynamic("TestTeamRuntimeSubscriptions::test_unsubscribe_empty_topic_raises",
                        this::unsubscribeEmptyTopicRaises),
                dynamic("TestTeamRuntimeSubscriptions::test_unregister_agent_clears_subscriptions",
                        this::unregisterAgentClearsSubscriptions),
                dynamic("TestTeamRuntimeSendPublishValidation::test_send_raises_when_sender_empty",
                        this::sendRaisesWhenSenderEmpty),
                dynamic("TestTeamRuntimeSendPublishValidation::test_send_raises_when_recipient_empty",
                        this::sendRaisesWhenRecipientEmpty),
                dynamic("TestTeamRuntimeSendPublishValidation::test_send_raises_when_recipient_not_registered",
                        this::sendRaisesWhenRecipientNotRegistered),
                dynamic("TestTeamRuntimeSendPublishValidation::test_publish_raises_when_sender_empty",
                        this::publishRaisesWhenSenderEmpty),
                dynamic("TestTeamRuntimeSendPublishValidation::test_publish_raises_when_topic_id_empty",
                        this::publishRaisesWhenTopicIdEmpty),
                dynamic("TestTeamRuntimeSendPublishValidation::test_send_routes_through_message_bus",
                        this::sendRoutesThroughMessageBus),
                dynamic("TestTeamRuntimeSendPublishValidation::test_publish_routes_through_message_bus",
                        this::publishRoutesThroughMessageBus)
        );
    }

    private void runtimeConfigDefaults() {
        RuntimeConfig config = new RuntimeConfig();

        assertThat(config.getTeamId()).isEqualTo("default");
        assertThat(config.getMessageBus()).isNull();
    }

    private void runtimeConfigCustomTeamId() {
        RuntimeConfig config = new RuntimeConfig("my_team", null, 1800.0d);

        assertThat(config.getTeamId()).isEqualTo("my_team");
    }

    private void runtimeConfigCustomMessageBus() {
        MessageBusConfig busConfig = new MessageBusConfig(50, 1800.0d, null);
        RuntimeConfig config = new RuntimeConfig("g", busConfig, 1800.0d);

        assertThat(config.getMessageBus().getMaxQueueSize()).isEqualTo(50);
    }

    private void startSetsRunning() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.start().join();

        assertThat(runtime.isRunning()).isTrue();
        runtime.stop().join();
    }

    private void stopClearsRunning() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.start().join();
        runtime.stop().join();

        assertThat(runtime.isRunning()).isFalse();
    }

    private void startIsIdempotent() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.start().join();
        runtime.start().join();

        assertThat(runtime.isRunning()).isTrue();
        runtime.stop().join();
    }

    private void stopWhenNotRunningIsSafe() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.stop().join();

        assertThat(runtime.isRunning()).isFalse();
    }

    private void closeStartsAndStops() {
        TeamRuntime runtime = new TeamRuntime();

        try (runtime) {
            runtime.start().join();
            assertThat(runtime.isRunning()).isTrue();
        }

        assertThat(runtime.isRunning()).isFalse();
    }

    private void hasAgentFalseBeforeRegistration() {
        assertThat(new TeamRuntime().hasAgent("unknown")).isFalse();
    }

    private void registerAgentStoresCard() {
        TeamRuntime runtime = new TeamRuntime();

        register(runtime, card("agent_a"));

        assertThat(runtime.hasAgent("agent_a")).isTrue();
    }

    private void getAgentCardReturnsRegisteredCard() {
        TeamRuntime runtime = new TeamRuntime();
        AgentCard card = card("agent_b");

        register(runtime, card);

        assertSame(card, runtime.getAgentCard("agent_b"));
    }

    private void getAgentCardReturnsNoneForUnknown() {
        assertThat(new TeamRuntime().getAgentCard("ghost")).isNull();
    }

    private void getAgentCountIncrements() {
        TeamRuntime runtime = new TeamRuntime();

        assertThat(runtime.getAgentCount()).isZero();
        register(runtime, card("a1"));
        register(runtime, card("a2"));

        assertThat(runtime.getAgentCount()).isEqualTo(2);
    }

    private void listAgentsReturnsAllIds() {
        TeamRuntime runtime = new TeamRuntime();

        register(runtime, card("a1"));
        register(runtime, card("a2"));

        assertThat(new LinkedHashSet<>(runtime.listAgents())).isEqualTo(Set.of("a1", "a2"));
    }

    private void unregisterAgentRemovesCard() {
        TeamRuntime runtime = new TeamRuntime();
        AgentCard card = card("agent_c");
        register(runtime, card);

        AgentCard removed = runtime.unregisterAgent("agent_c");

        assertSame(card, removed);
        assertThat(runtime.hasAgent("agent_c")).isFalse();
    }

    private void unregisterUnknownAgentReturnsNone() {
        assertThat(new TeamRuntime().unregisterAgent("nonexistent")).isNull();
    }

    private void wrapProviderAutoBindsCommunicableAgent() {
        TeamRuntime runtime = new TeamRuntime();
        EchoAgent agent = new EchoAgent();

        runtime.registerAgent(card("comm_agent"), () -> agent);
        Object result = runtime.send("hello", "comm_agent", "sender").join();

        assertThat(result).isEqualTo("echo:hello:null");
        assertThat(agent.isBound()).isTrue();
        assertSame(runtime, agent.getRuntime());
        assertThat(agent.getAgentId()).isEqualTo("comm_agent");
    }

    private void subscribeIncrementsCount() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.subscribe("agent_a", "topic1").join();

        assertThat(runtime.getSubscriptionCount()).isEqualTo(1);
        runtime.stop().join();
    }

    private void unsubscribeDecrementsCount() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.subscribe("agent_a", "topic1").join();
        runtime.unsubscribe("agent_a", "topic1").join();

        assertThat(runtime.getSubscriptionCount()).isZero();
        runtime.stop().join();
    }

    @SuppressWarnings("unchecked")
    private void listSubscriptionsAll() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.subscribe("agent_a", "t1").join();
        runtime.subscribe("agent_b", "t2").join();
        Map<String, Object> result = runtime.listSubscriptions();

        assertThat(result).containsKey("subscriptions");
        Map<String, List<String>> subscriptions = (Map<String, List<String>>) result.get("subscriptions");
        assertThat(subscriptions).containsKeys("t1", "t2");
        runtime.stop().join();
    }

    @SuppressWarnings("unchecked")
    private void listSubscriptionsFilteredByAgent() {
        TeamRuntime runtime = new TeamRuntime();

        runtime.subscribe("agent_a", "t1").join();
        runtime.subscribe("agent_a", "t2").join();
        Map<String, Object> result = runtime.listSubscriptions("agent_a");

        assertThat(result).containsEntry("agent_id", "agent_a");
        assertThat((List<String>) result.get("topics")).contains("t1", "t2");
        runtime.stop().join();
    }

    private void subscribeEmptyAgentIdRaises() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.subscribe("", "topic"));
    }

    private void subscribeEmptyTopicRaises() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.subscribe("agent_a", ""));
    }

    private void unsubscribeEmptyAgentIdRaises() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.unsubscribe("", "topic"));
    }

    private void unsubscribeEmptyTopicRaises() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.unsubscribe("agent_a", ""));
    }

    private void unregisterAgentClearsSubscriptions() {
        TeamRuntime runtime = new TeamRuntime();

        register(runtime, card("agent_sub"));
        runtime.subscribe("agent_sub", "events").join();
        assertThat(runtime.getSubscriptionCount()).isEqualTo(1);
        runtime.unregisterAgent("agent_sub");

        assertThat(runtime.getSubscriptionCount()).isZero();
        runtime.stop().join();
    }

    private void sendRaisesWhenSenderEmpty() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.send("msg", "agent_b", ""));
    }

    private void sendRaisesWhenRecipientEmpty() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.send("msg", "", "agent_a"));
    }

    private void sendRaisesWhenRecipientNotRegistered() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.send("msg", "ghost", "agent_a"));
    }

    private void publishRaisesWhenSenderEmpty() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.publish("msg", "events", ""));
    }

    private void publishRaisesWhenTopicIdEmpty() {
        TeamRuntime runtime = new TeamRuntime();

        assertThrows(RuntimeException.class, () -> runtime.publish("msg", "", "agent_a"));
    }

    private void sendRoutesThroughMessageBus() {
        TeamRuntime runtime = new TeamRuntime();
        EchoAgent recipient = new EchoAgent();
        runtime.registerAgent(card("agent_b"), () -> recipient);

        Object result = runtime.send("hello", "agent_b", "agent_a").join();

        assertThat(result).isEqualTo("echo:hello:null");
        assertThat(runtime.getMessageBus().getActiveSubscriptions()).contains("default__p2p__");
        runtime.stop().join();
    }

    private void publishRoutesThroughMessageBus() {
        TeamRuntime runtime = new TeamRuntime();
        RecordingAgent subscriber = new RecordingAgent();
        runtime.registerAgent(card("agent_a"), () -> subscriber);
        runtime.subscribe("agent_a", "my_topic").join();

        runtime.publish("event", "my_topic", "agent_a").join();

        assertThat(subscriber.messages).containsExactly("event");
        assertThat(runtime.getMessageBus().getActiveSubscriptions()).contains("default__pubsub__");
        runtime.stop().join();
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static AgentCard card(String agentId) {
        return new AgentCard(agentId, agentId, "test agent");
    }

    private static void register(TeamRuntime runtime, AgentCard card) {
        runtime.registerAgent(card, EchoAgent::new);
    }

    static class EchoAgent implements CommunicableAgent {
        Object invoke(Object message, Object session) {
            return CompletableFuture.completedFuture("echo:" + message + ":" + session);
        }
    }

    static final class RecordingAgent implements CommunicableAgent {
        private final List<Object> messages = new ArrayList<>();

        Object invoke(Object message, Object session) {
            messages.add(message);
            return null;
        }
    }
}
