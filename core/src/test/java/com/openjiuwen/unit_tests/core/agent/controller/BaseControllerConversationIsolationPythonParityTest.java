/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.agent.controller;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.legacy.BaseController;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.agent.BaseAgent;
import com.openjiuwen.core.singleagent.legacy.agent.ControllerAgent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestBaseControllerConversationIsolation},
 * {@code TestBaseAgentClearSession}, and {@code TestControllerAgentClearSession} in
 * {@code tests/unit_tests/core/agent/controller/test_base_controller_conversation_isolation.py}.
 */
class BaseControllerConversationIsolationPythonParityTest {

    @Test
    void singleConversation() {
        SimpleController controller = new SimpleController(new Object(), new ContextEngine());

        Map<String, Object> result = controller.invoke(Map.of(
                "conversation_id", "conv_001",
                "query", "Hello"), new Object());

        assertThat(result).containsEntry("conversation_id", "conv_001");
        assertThat(result).containsEntry("content", "Hello");
        assertThat(subscriptionIds(controller)).contains("conv_001");

        controller.cleanupConversation("conv_001");

        assertThat(subscriptionIds(controller)).doesNotContain("conv_001");
    }

    @Test
    void multipleConversationsIsolated() {
        SimpleController controller = new SimpleController(new Object(), new ContextEngine());

        CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() -> controller.invoke(Map.of(
                "conversation_id", "conv_001",
                "query", "Event 1"), new Object()));
        CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() -> controller.invoke(Map.of(
                "conversation_id", "conv_002",
                "query", "Event 2"), new Object()));
        List<Map<String, Object>> results = List.of(first.join(), second.join());

        assertThat(results.get(0)).containsEntry("conversation_id", "conv_001");
        assertThat(results.get(0)).containsEntry("content", "Event 1");
        assertThat(results.get(1)).containsEntry("conversation_id", "conv_002");
        assertThat(results.get(1)).containsEntry("content", "Event 2");
        assertThat(subscriptionIds(controller)).contains("conv_001", "conv_002");

        controller.cleanupConversation("conv_001");
        controller.cleanupConversation("conv_002");

        assertThat(subscriptionIds(controller)).isEmpty();
    }

    @Test
    void stopCleanupAllSubscriptions() {
        SimpleController controller = new SimpleController(new Object(), new ContextEngine());
        controller.invoke(Map.of("conversation_id", "conv_001", "query", "Test 1"), new Object());
        controller.invoke(Map.of("conversation_id", "conv_002", "query", "Test 2"), new Object());

        assertThat(subscriptionIds(controller)).hasSize(2);

        controller.stop();

        assertThat(subscriptionIds(controller)).isEmpty();
    }

    @Test
    void concurrentSameConversation() {
        SimpleController controller = new SimpleController(new Object(), new ContextEngine());
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String query = "Event " + i;
            futures.add(CompletableFuture.supplyAsync(() -> controller.invoke(Map.of(
                    "conversation_id", "conv_001",
                    "query", query), new Object())));
        }

        List<Map<String, Object>> results = futures.stream().map(CompletableFuture::join).toList();

        assertThat(results).allSatisfy(result -> assertThat(result).containsEntry("conversation_id", "conv_001"));
        assertThat(subscriptionIds(controller)).containsExactly("conv_001");

        controller.stop();
    }

    @Test
    void baseAgentClearSessionCallsRunnerRelease() {
        ConcreteAgent agent = new ConcreteAgent(new Object());
        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.release("test_session_123"))
                    .thenReturn(CompletableFuture.completedFuture(null));

            agent.clearSession("test_session_123").toCompletableFuture().join();

            runner.verify(() -> Runner.release("test_session_123"));
        }
    }

    @Test
    void baseAgentClearSessionDefaultSessionId() {
        ConcreteAgent agent = new ConcreteAgent(new Object());
        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.release("default_session"))
                    .thenReturn(CompletableFuture.completedFuture(null));

            agent.clearSession().toCompletableFuture().join();

            runner.verify(() -> Runner.release("default_session"));
        }
    }

    @Test
    void controllerAgentClearSessionCallsParentClearSession() {
        CleanupController controller = new CleanupController();
        ControllerAgent agent = new ControllerAgent(new Object(), controller);
        TestSession session = new TestSession("test_session_123");
        agent.getContextEngine().createContext(null, session);
        assertThat(agent.getContextEngine().getContext(ContextEngine.DEFAULT_CONTEXT_ID, "test_session_123"))
                .isNotNull();
        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.release("test_session_123"))
                    .thenReturn(CompletableFuture.completedFuture(null));

            agent.clearSession("test_session_123").toCompletableFuture().join();

            runner.verify(() -> Runner.release("test_session_123"));
        }

        assertThat(agent.getContextEngine().getContext(ContextEngine.DEFAULT_CONTEXT_ID, "test_session_123"))
                .isNull();
        assertThat(controller.cleanedSessions).containsExactly("test_session_123");
    }

    @Test
    void controllerAgentClearSessionDefaultSessionId() {
        CleanupController controller = new CleanupController();
        ControllerAgent agent = new ControllerAgent(new Object(), controller);
        try (MockedStatic<Runner> runner = Mockito.mockStatic(Runner.class)) {
            runner.when(() -> Runner.release("default_session"))
                    .thenReturn(CompletableFuture.completedFuture(null));

            agent.clearSession().toCompletableFuture().join();

            runner.verify(() -> Runner.release("default_session"));
        }

        assertThat(controller.cleanedSessions).containsExactly("default_session");
    }

    private static List<String> subscriptionIds(BaseController controller) {
        try {
            Field field = BaseController.class.getDeclaredField("subscriptions");
            field.setAccessible(true);
            Map<?, ?> subscriptions = (Map<?, ?>) field.get(controller);
            return subscriptions.keySet().stream().map(String::valueOf).sorted().toList();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class SimpleController extends BaseController {
        private SimpleController(Object config, ContextEngine contextEngine) {
            super(config, contextEngine);
        }

        @Override
        protected Map<String, Object> handleEvent(Event event, Object session) {
            return Map.of(
                    "conversation_id", event.getSource().getConversationId(),
                    "content", event.getContent().getQuery() == null ? "" : event.getContent().getQuery(),
                    "handled_by", "SimpleController"
            );
        }
    }

    private static final class ConcreteAgent extends BaseAgent {
        private ConcreteAgent(Object agentConfig) {
            super(agentConfig);
        }

        @Override
        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                       List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    public static final class CleanupController {
        private final List<String> cleanedSessions = new ArrayList<>();

        public void setupFromAgent(Object agent) {
            // No setup state needed for this parity test.
        }

        public void cleanupConversation(String sessionId) {
            cleanedSessions.add(sessionId);
        }
    }

    private record TestSession(String sessionId) implements ContextEngine.SessionPort {
        @Override
        public String getSessionId() {
            return sessionId;
        }
    }
}
