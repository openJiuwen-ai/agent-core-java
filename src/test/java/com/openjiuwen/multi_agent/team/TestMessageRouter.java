/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.teamruntime.MessageEnvelope;
import com.openjiuwen.core.multiagent.teamruntime.MessageRouter;
import com.openjiuwen.core.multiagent.teamruntime.SubscriptionManager;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageRouter.
 *
 * <p>Mirrors Python's {@code test_message_router.py} in
 * {@code tests.unit_tests.multi_agent.team}.
 */
class TestMessageRouter {

    static class RecordingAgent {
        private final Object response;
        private final RuntimeException failure;
        private final List<Object> messages = new ArrayList<>();
        private final List<String> sessions = new ArrayList<>();

        RecordingAgent(Object response) {
            this.response = response;
            this.failure = null;
        }

        RecordingAgent(RuntimeException failure) {
            this.response = null;
            this.failure = failure;
        }

        public Object invoke(Object message, String sessionId) {
            if (failure != null) {
                throw failure;
            }
            messages.add(message);
            sessions.add(sessionId);
            return response;
        }
    }

    private static AgentCard card(String id) {
        return AgentCard.builder().id(id).name(id).description("test agent").build();
    }

    private static MessageEnvelope makeP2pEnvelope() {
        return makeP2pEnvelope("agent_b", "agent_a", "hello", null);
    }

    private static MessageEnvelope makeP2pEnvelope(String recipient, String sender, Object message, String sessionId) {
        return new MessageEnvelope("test-p2p", message, sender, recipient, null, sessionId, Map.of());
    }

    private static MessageEnvelope makePubsubEnvelope(String topicId, String sender, Object message, String sessionId) {
        return new MessageEnvelope("test-pubsub", message, sender, null, topicId, sessionId, Map.of());
    }

    @Nested
    class TestMessageRouterP2P {
        private SubscriptionManager subMgr;
        private TeamRuntime runtime;
        private MessageRouter router;

        @BeforeEach
        void setup() {
            subMgr = new SubscriptionManager();
            runtime = new TeamRuntime();
            router = new MessageRouter(subMgr, runtime);
        }

        @Test
        void testRouteP2pCallsRunnerRunAgent() {
            RecordingAgent agent = new RecordingAgent("pong");
            runtime.registerAgent(card("agent_b"), () -> agent);

            Object result = router.routeP2pMessage(
                    makeP2pEnvelope("agent_b", "agent_a", "ping", null)).join();

            assertEquals(List.of("ping"), agent.messages);
            assertEquals("pong", result);
        }

        @Test
        void testRouteP2pPassesSessionId() {
            RecordingAgent agent = new RecordingAgent("ok");
            runtime.registerAgent(card("agent_b"), () -> agent);

            router.routeP2pMessage(makeP2pEnvelope("agent_b", "agent_a", "hello", "session-123")).join();

            assertEquals(List.of("session-123"), agent.sessions);
        }

        @Test
        void testRouteP2pRaisesOnRunnerError() {
            runtime.registerAgent(card("bad_agent"), () -> new RecordingAgent(new RuntimeException("agent crash")));

            CompletionException error = assertThrows(CompletionException.class,
                    () -> router.routeP2pMessage(makeP2pEnvelope("bad_agent", "agent_a", "hello", null)).join());

            assertTrue(error.getMessage().contains("agent crash"));
        }

        @Test
        void testRouteP2pRaisesOnAttributeError() {
            MessageRouter unboundRouter = new MessageRouter(subMgr);

            assertThrows(CompletionException.class, () -> unboundRouter.routeP2pMessage(makeP2pEnvelope()).join());
        }
    }

    @Nested
    class TestMessageRouterPubSub {
        private SubscriptionManager subMgr;
        private TeamRuntime runtime;
        private MessageRouter router;

        @BeforeEach
        void setup() {
            subMgr = new SubscriptionManager();
            runtime = new TeamRuntime();
            router = new MessageRouter(subMgr, runtime);
        }

        @Test
        void testRoutePubsubNoSubscribersDoesNotRaise() {
            assertDoesNotThrow(() -> router.routePubsubMessage(
                    makePubsubEnvelope("empty_topic", "agent_a", "event_data", null)).join());
        }

        @Test
        void testRoutePubsubInvokesAllSubscribers() {
            Set<String> callLog = ConcurrentHashMap.newKeySet();
            runtime.registerAgent(card("agent_a"), () -> new RecordingAgent((Object) null) {
                @Override
                public Object invoke(Object message, String sessionId) {
                    callLog.add("agent_a");
                    return null;
                }
            });
            runtime.registerAgent(card("agent_b"), () -> new RecordingAgent((Object) null) {
                @Override
                public Object invoke(Object message, String sessionId) {
                    callLog.add("agent_b");
                    return null;
                }
            });
            subMgr.subscribe("agent_a", "code_events");
            subMgr.subscribe("agent_b", "code_events");

            router.routePubsubMessage(makePubsubEnvelope("code_events", "agent_a", "event_data", null)).join();

            assertEquals(Set.of("agent_a", "agent_b"), callLog);
        }

        @Test
        void testRoutePubsubWildcardSubscriber() {
            Set<String> received = ConcurrentHashMap.newKeySet();
            runtime.registerAgent(card("listener"), () -> new RecordingAgent((Object) null) {
                @Override
                public Object invoke(Object message, String sessionId) {
                    received.add("listener");
                    return null;
                }
            });
            subMgr.subscribe("listener", "code_*");

            router.routePubsubMessage(makePubsubEnvelope("code_review", "agent_a", "event_data", null)).join();

            assertTrue(received.contains("listener"));
        }

        @Test
        void testRoutePubsubOneFailingSubscriberDoesNotAbortOthers() {
            Set<String> callLog = ConcurrentHashMap.newKeySet();
            runtime.registerAgent(card("good_agent"), () -> new RecordingAgent((Object) null) {
                @Override
                public Object invoke(Object message, String sessionId) {
                    callLog.add("good_agent");
                    return null;
                }
            });
            runtime.registerAgent(card("bad_agent"), () -> new RecordingAgent(new RuntimeException("subscriber failed")));
            subMgr.subscribe("good_agent", "events");
            subMgr.subscribe("bad_agent", "events");

            assertDoesNotThrow(() -> router.routePubsubMessage(
                    makePubsubEnvelope("events", "agent_a", "event_data", null)).join());

            assertTrue(callLog.contains("good_agent"));
        }

        @Test
        void testRoutePubsubPassesSessionIdToSubscribers() {
            RecordingAgent agent = new RecordingAgent((Object) null);
            runtime.registerAgent(card("agent_c"), () -> agent);
            subMgr.subscribe("agent_c", "task_events");

            router.routePubsubMessage(
                    makePubsubEnvelope("task_events", "agent_a", "event_data", "sess-99")).join();

            assertTrue(agent.sessions.contains("sess-99"));
        }
    }
}
