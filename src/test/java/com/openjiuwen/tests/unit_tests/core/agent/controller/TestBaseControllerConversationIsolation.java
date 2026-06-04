/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.agent.controller;

import com.openjiuwen.core.controller.legacy.BaseController;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.BaseAgent;
import com.openjiuwen.core.singleagent.legacy.ControllerAgent;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Mirrors Python's {@code TestBaseControllerConversationIsolation},
 * {@code TestBaseAgentClearSession}, and {@code TestControllerAgentClearSession}
 * in {@code tests.unit_tests.core.agent.controller.test_base_controller_conversation_isolation}.
 */
@Tag("unit-test")
class TestBaseControllerConversationIsolation {

    private AgentConfig config;
    private SimpleSession session;

    @BeforeEach
    void asyncSetUp() {
        config = new AgentConfig();
        session = new SimpleSession("test_session");
    }

    @Test
    @DisplayName("Test single conversation works correctly")
    void testSingleConversation() {
        SimpleController controller = new SimpleController();

        Map<String, Object> result = controller.invoke(
                Map.of("conversation_id", "conv_001", "query", "Hello"),
                session);

        assertEquals("conv_001", result.get("conversation_id"));
        assertEquals("Hello", result.get("content"));
        assertTrue(hasSubscription(controller, "conv_001"));

        controller.cleanupConversation("conv_001");
        assertFalse(hasSubscription(controller, "conv_001"));
        controller.stop();
    }

    @Test
    @DisplayName("Test multiple conversations are isolated")
    void testMultipleConversationsIsolated() throws Exception {
        SimpleController controller = new SimpleController();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Map<String, Object>> first = executor.submit(() -> controller.invoke(
                    Map.of("conversation_id", "conv_001", "query", "Event 1"),
                    session));
            Future<Map<String, Object>> second = executor.submit(() -> controller.invoke(
                    Map.of("conversation_id", "conv_002", "query", "Event 2"),
                    session));

            Map<String, Object> result1 = first.get();
            Map<String, Object> result2 = second.get();

            assertEquals("conv_001", result1.get("conversation_id"));
            assertEquals("Event 1", result1.get("content"));
            assertEquals("conv_002", result2.get("conversation_id"));
            assertEquals("Event 2", result2.get("content"));
            assertTrue(hasSubscription(controller, "conv_001"));
            assertTrue(hasSubscription(controller, "conv_002"));

            controller.cleanupConversation("conv_001");
            controller.cleanupConversation("conv_002");
            assertEquals(0, subscriptionCount(controller));
        } finally {
            controller.stop();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Test stop cleanup all subscriptions")
    void testStopCleanupAllSubscriptions() {
        SimpleController controller = new SimpleController();

        controller.invoke(Map.of("conversation_id", "conv_001", "query", "Test 1"), session);
        controller.invoke(Map.of("conversation_id", "conv_002", "query", "Test 2"), session);

        assertEquals(2, subscriptionCount(controller));

        controller.stop();

        assertEquals(0, subscriptionCount(controller));
    }

    @Test
    @DisplayName("Test concurrent calls with same conversation_id")
    void testConcurrentSameConversation() throws Exception {
        SimpleController controller = new SimpleController();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int index = i;
                futures.add(executor.submit(() -> controller.invoke(
                        Map.of("conversation_id", "conv_001", "query", "Event " + index),
                        session)));
            }

            for (Future<Map<String, Object>> future : futures) {
                assertEquals("conv_001", future.get().get("conversation_id"));
            }
            assertEquals(1, subscriptionCount(controller));
            assertTrue(hasSubscription(controller, "conv_001"));
        } finally {
            controller.stop();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Test BaseAgent.clearSession calls Runner.release with session_id")
    void testClearSessionCallsRunnerRelease() {
        ConcreteAgent agent = new ConcreteAgent(config);

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            agent.clearSession("test_session_123");

            runner.verify(() -> Runner.release("test_session_123"));
        }
    }

    @Test
    @DisplayName("Test BaseAgent.clearSession uses default_session")
    void testClearSessionDefaultSessionId() {
        ConcreteAgent agent = new ConcreteAgent(config);

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            agent.clearSession();

            runner.verify(() -> Runner.release("default_session"));
        }
    }

    @Test
    @DisplayName("Test ControllerAgent.clearSession calls parent clearSession")
    void testClearSessionCallsParentClearSession() {
        TrackingController controller = new TrackingController();
        ControllerAgent agent = new ControllerAgent(config, controller);
        SimpleSession contextSession = new SimpleSession("test_session_123");
        agent.getContextEngine().createContext("ctx", contextSession);
        assertNotNull(agent.getContextEngine().getContext("ctx", "test_session_123"));

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            agent.clearSession("test_session_123");

            runner.verify(() -> Runner.release("test_session_123"));
        }

        assertNull(agent.getContextEngine().getContext("ctx", "test_session_123"));
        assertEquals(List.of("test_session_123"), controller.cleanedConversations);
    }

    @Test
    @DisplayName("Test ControllerAgent.clearSession uses default_session")
    void testControllerAgentClearSessionDefaultSessionId() {
        TrackingController controller = new TrackingController();
        ControllerAgent agent = new ControllerAgent(config, controller);
        SimpleSession contextSession = new SimpleSession("default_session");
        agent.getContextEngine().createContext("ctx", contextSession);
        assertNotNull(agent.getContextEngine().getContext("ctx", "default_session"));

        try (MockedStatic<Runner> runner = mockStatic(Runner.class)) {
            agent.clearSession();

            runner.verify(() -> Runner.release("default_session"));
        }

        assertNull(agent.getContextEngine().getContext("ctx", "default_session"));
        assertEquals(List.of("default_session"), controller.cleanedConversations);
    }

    static class SimpleController extends BaseController {
        @Override
        protected Map<String, Object> handleEvent(Event event, Session session) {
            return Map.of(
                    "conversation_id", event.getSource().getConversationId(),
                    "content", event.getContent().getQueryText(),
                    "handled_by", "SimpleController");
        }
    }

    static class TrackingController extends SimpleController {
        private final List<String> cleanedConversations = new ArrayList<>();

        @Override
        public void cleanupConversation(String conversationId) {
            cleanedConversations.add(conversationId);
            super.cleanupConversation(conversationId);
        }
    }

    static class ConcreteAgent extends BaseAgent {
        ConcreteAgent(AgentConfig agentConfig) {
            super(agentConfig);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Session session) {
            return Map.of();
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
            return List.of().iterator();
        }
    }

    static class SimpleSession implements Session {
        private final String sessionId;
        private final java.util.HashMap<String, Object> state = new java.util.HashMap<>();

        SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static boolean hasSubscription(BaseController controller, String conversationId) {
        return subscriptions(controller).containsKey(conversationId);
    }

    private static int subscriptionCount(BaseController controller) {
        return subscriptions(controller).size();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> subscriptions(BaseController controller) {
        try {
            Field field = BaseController.class.getDeclaredField("subscriptions");
            field.setAccessible(true);
            return (Map<String, ?>) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect controller subscriptions", e);
        }
    }
}
