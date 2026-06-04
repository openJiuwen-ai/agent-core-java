/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.MessageBusConfig;
import com.openjiuwen.core.multiagent.teamruntime.RuntimeConfig;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamRuntime.
 *
 * <p>Mirrors Python's {@code test_team_runtime.py} in
 * {@code tests.unit_tests.multi_agent.team}.
 */
class TestTeamRuntime {

    static class CommAgent implements CommunicableAgent {
    }

    private static AgentCard card(String id) {
        return AgentCard.builder().id(id).name(id).description("test agent").build();
    }

    private static void register(TeamRuntime runtime, AgentCard card) {
        runtime.registerAgent(card, () -> (Function<Object, Object>) message -> message);
    }

    @Nested
    class TestRuntimeConfig {
        @Test
        void testDefaults() {
            RuntimeConfig cfg = new RuntimeConfig();

            assertEquals("default", cfg.getTeamId());
            assertTrue(cfg.getMessageBus().isEmpty());
        }

        @Test
        void testCustomTeamId() {
            RuntimeConfig cfg = RuntimeConfig.builder().teamId("my_team").build();

            assertEquals("my_team", cfg.getTeamId());
        }

        @Test
        void testCustomMessageBus() {
            MessageBusConfig busCfg = MessageBusConfig.builder().maxQueueSize(50).build();
            RuntimeConfig cfg = RuntimeConfig.builder().teamId("g").messageBus(busCfg).build();

            assertEquals(50, cfg.getMessageBus().orElseThrow().getMaxQueueSize());
        }
    }

    @Nested
    class TestTeamRuntimeLifecycle {
        @Test
        void testStartSetsRunning() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.start();

            assertTrue(runtime.isRunning());
            runtime.stop();
        }

        @Test
        void testStopClearsRunning() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.start();
            runtime.stop();

            assertFalse(runtime.isRunning());
        }

        @Test
        void testStartIsIdempotent() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.start();
            runtime.start();

            assertTrue(runtime.isRunning());
            runtime.stop();
        }

        @Test
        void testStopWhenNotRunningIsSafe() {
            assertDoesNotThrow(() -> new TeamRuntime().stop());
        }

        @Test
        void testAsyncContextManagerStartsAndStops() {
            TeamRuntime runtime = new TeamRuntime();

            runtime.start();
            assertTrue(runtime.isRunning());
            runtime.stop();
            assertFalse(runtime.isRunning());
        }
    }

    @Nested
    class TestTeamRuntimeAgentRegistration {
        @Test
        void testHasAgentFalseBeforeRegistration() {
            assertFalse(new TeamRuntime().hasAgent("unknown"));
        }

        @Test
        void testRegisterAgentStoresCard() {
            TeamRuntime runtime = new TeamRuntime();
            register(runtime, card("agent_a"));

            assertTrue(runtime.hasAgent("agent_a"));
        }

        @Test
        void testGetAgentCardReturnsTheRegisteredCard() {
            TeamRuntime runtime = new TeamRuntime();
            AgentCard card = card("agent_b");
            register(runtime, card);

            assertSame(card, runtime.getAgentCard("agent_b"));
        }

        @Test
        void testGetAgentCardReturnsNoneForUnknown() {
            assertNull(new TeamRuntime().getAgentCard("ghost"));
        }

        @Test
        void testGetAgentCountIncrements() {
            TeamRuntime runtime = new TeamRuntime();

            assertEquals(0, runtime.getAgentCount());
            register(runtime, card("a1"));
            register(runtime, card("a2"));
            assertEquals(2, runtime.getAgentCount());
        }

        @Test
        void testListAgentsReturnsAllIds() {
            TeamRuntime runtime = new TeamRuntime();
            register(runtime, card("a1"));
            register(runtime, card("a2"));

            assertEquals(Set.of("a1", "a2"), Set.copyOf(runtime.listAgents()));
        }

        @Test
        void testUnregisterAgentRemovesCard() {
            TeamRuntime runtime = new TeamRuntime();
            AgentCard card = card("agent_c");
            register(runtime, card);

            AgentCard removed = runtime.unregisterAgent("agent_c");

            assertSame(card, removed);
            assertFalse(runtime.hasAgent("agent_c"));
        }

        @Test
        void testUnregisterUnknownAgentReturnsNone() {
            assertNull(new TeamRuntime().unregisterAgent("nonexistent"));
        }

        @Test
        void testWrapProviderAutoBindsCommunicableAgent() {
            TeamRuntime runtime = new TeamRuntime();
            CommAgent agent = new CommAgent();
            runtime.registerAgent(card("comm_agent"), () -> agent);

            Object created = runtime.createAgent("comm_agent");

            assertSame(agent, created);
            assertTrue(agent.isBound());
            assertEquals("comm_agent", agent.getAgentId());
        }
    }

    @Nested
    class TestTeamRuntimeSubscriptions {
        @Test
        void testSubscribeIncrementsCount() {
            TeamRuntime runtime = new TeamRuntime();

            runtime.subscribe("agent_a", "topic1");

            assertEquals(1, runtime.getSubscriptionCount());
        }

        @Test
        void testUnsubscribeDecrementsCount() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.subscribe("agent_a", "topic1");
            runtime.unsubscribe("agent_a", "topic1");

            assertEquals(0, runtime.getSubscriptionCount());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testListSubscriptionsAll() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.subscribe("agent_a", "t1");
            runtime.subscribe("agent_b", "t2");

            Map<String, Object> result = runtime.listSubscriptions();
            Map<String, Object> subscriptions = (Map<String, Object>) result.get("subscriptions");

            assertTrue(result.containsKey("subscriptions"));
            assertTrue(subscriptions.containsKey("t1"));
            assertTrue(subscriptions.containsKey("t2"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testListSubscriptionsFilteredByAgent() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.subscribe("agent_a", "t1");
            runtime.subscribe("agent_a", "t2");

            Map<String, Object> result = runtime.listSubscriptions("agent_a");
            List<String> topics = (List<String>) result.get("topics");

            assertEquals("agent_a", result.get("agent_id"));
            assertTrue(topics.contains("t1"));
        }

        @Test
        void testSubscribeEmptyAgentIdRaises() {
            assertThrows(Exception.class, () -> new TeamRuntime().subscribe("", "topic"));
        }

        @Test
        void testSubscribeEmptyTopicRaises() {
            assertThrows(Exception.class, () -> new TeamRuntime().subscribe("agent_a", ""));
        }

        @Test
        void testUnsubscribeEmptyAgentIdRaises() {
            assertThrows(Exception.class, () -> new TeamRuntime().unsubscribe("", "topic"));
        }

        @Test
        void testUnsubscribeEmptyTopicRaises() {
            assertThrows(Exception.class, () -> new TeamRuntime().unsubscribe("agent_a", ""));
        }

        @Test
        void testUnregisterAgentClearsSubscriptions() {
            TeamRuntime runtime = new TeamRuntime();
            register(runtime, card("agent_sub"));
            runtime.subscribe("agent_sub", "events");

            assertEquals(1, runtime.getSubscriptionCount());
            runtime.unregisterAgent("agent_sub");
            assertEquals(0, runtime.getSubscriptionCount());
        }
    }

    @Nested
    class TestTeamRuntimeSendPublishValidation {
        @Test
        void testSendRaisesWhenSenderEmpty() {
            TeamRuntime runtime = new TeamRuntime();
            register(runtime, card("agent_b"));

            assertThrows(Exception.class, () -> runtime.send("msg", "agent_b", "").join());
        }

        @Test
        void testSendRaisesWhenRecipientEmpty() {
            assertThrows(Exception.class, () -> new TeamRuntime().send("msg", "", "agent_a").join());
        }

        @Test
        void testSendRaisesWhenRecipientNotRegistered() {
            assertThrows(Exception.class, () -> new TeamRuntime().send("msg", "ghost", "agent_a").join());
        }

        @Test
        void testPublishRaisesWhenSenderEmpty() {
            assertThrows(Exception.class, () -> new TeamRuntime().publish("msg", "events", "").join());
        }

        @Test
        void testPublishRaisesWhenTopicIdEmpty() {
            assertThrows(Exception.class, () -> new TeamRuntime().publish("msg", "", "agent_a").join());
        }

        @Test
        void testSendRoutesThroughMessageBus() {
            TeamRuntime runtime = new TeamRuntime();
            runtime.registerAgent(card("agent_b"), () -> (Function<Object, Object>) message -> "hello_response");

            Object result = runtime.send("hello", "agent_b", "agent_a").join();

            assertEquals("hello_response", result);
        }

        @Test
        void testPublishRoutesThroughMessageBus() {
            TeamRuntime runtime = new TeamRuntime();
            AtomicReference<Object> received = new AtomicReference<>();
            runtime.registerAgent(card("agent_a"), () -> (Function<Object, Object>) message -> {
                received.set(message);
                return null;
            });
            runtime.subscribe("agent_a", "my_topic");

            runtime.publish("event", "my_topic", "agent_a").join();

            assertEquals("event", received.get());
        }
    }
}
