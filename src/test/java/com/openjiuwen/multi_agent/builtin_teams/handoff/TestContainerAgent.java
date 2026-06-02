/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.ContainerAgent;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffOrchestrator;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffRequest;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContainerAgent.
 *
 * <p>Mirrors Python's {@code test_container_agent.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestContainerAgent {

    private static final class TestableContainerAgent extends ContainerAgent {
        TestableContainerAgent(AgentCard targetCard, java.util.function.Supplier<BaseAgent> provider) {
            super(targetCard, provider, Set.of());
        }

        TestableContainerAgent(
                AgentCard targetCard,
                java.util.function.Supplier<BaseAgent> provider,
                java.util.function.Function<String, HandoffOrchestrator> lookup) {
            super(targetCard, provider, Set.of(), lookup);
        }

        Object buildInput(HandoffRequest request) {
            return buildAgentInput(request);
        }

        BaseAgent target() {
            return getTargetAgent();
        }
    }

    private static final class StubAgent extends BaseAgent {
        private final Object result;
        private final RuntimeException failure;
        private Object lastInput;

        StubAgent(AgentCard card, Object result) {
            this(card, result, null);
        }

        StubAgent(AgentCard card, Object result, RuntimeException failure) {
            super(card);
            this.result = result;
            this.failure = failure;
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
            lastInput = inputs;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(result).iterator();
        }

        Object getLastInput() {
            return lastInput;
        }
    }

    private static AgentCard card(String aid) {
        return AgentCard.builder().id(aid).name(aid).description("agent " + aid).build();
    }

    private static HandoffOrchestrator coordinator() {
        return new HandoffOrchestrator("a", List.of("a", "b"), HandoffConfig.builder().maxHandoffs(3).build());
    }

    @Nested
    class TestBuildAgentInput {
        @Test
        void testNoHistoryReturnsRawMessage() {
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            assertEquals("hello", agent.buildInput(new HandoffRequest("hello", List.of())));
        }

        @Test
        void testNoHistoryDictReturnedAsIs() {
            Map<String, Object> message = Map.of("query", "q");
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            assertSame(message, agent.buildInput(new HandoffRequest(message, List.of())));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testDictMessageWithHistoryMerged() {
            List<Map<String, Object>> history = List.of(Map.of("agent", "a", "output", Map.of()));
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            Map<String, Object> result = (Map<String, Object>) agent.buildInput(new HandoffRequest(
                    Map.of("query", "q"), history));
            assertEquals("q", result.get("query"));
            assertSame(history, result.get("handoff_history"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testStringMessageWithHistoryWrapped() {
            List<Map<String, Object>> history = List.of(Map.of("agent", "a", "output", Map.of()));
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            Map<String, Object> result = (Map<String, Object>) agent.buildInput(new HandoffRequest("hello", history));
            assertEquals("hello", result.get("query"));
            assertSame(history, result.get("handoff_history"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testHistoryListPassedThrough() {
            List<Map<String, Object>> history = new ArrayList<>(List.of(Map.of("agent", "a"), Map.of("agent", "b")));
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            Map<String, Object> result = (Map<String, Object>) agent.buildInput(new HandoffRequest("x", history));
            assertSame(history, result.get("handoff_history"));
        }
    }

    @Nested
    class TestStripHandoffMessages {
        @Test
        void testToolMessagesRemoved() {
            List<Object> cleaned = ContainerAgent.stripHandoffMessages(List.of(
                    Map.of("role", "user"), Map.of("role", "tool")));
            assertTrue(cleaned.stream().noneMatch(m -> "tool".equals(((Map<?, ?>) m).get("role"))));
        }

        @Test
        void testAssistantWithToolCallsRemoved() {
            List<Object> cleaned = ContainerAgent.stripHandoffMessages(List.of(
                    Map.of("role", "assistant", "toolCalls", List.of("call"))));
            assertEquals(List.of(), cleaned);
        }

        @Test
        void testAssistantWithoutToolCallsKept() {
            assertEquals(1, ContainerAgent.stripHandoffMessages(List.of(
                    Map.of("role", "assistant", "toolCalls", List.of()))).size());
        }

        @Test
        void testUserMessagesKept() {
            assertEquals(1, ContainerAgent.stripHandoffMessages(List.of(Map.of("role", "user"))).size());
        }

        @Test
        void testEmptyListReturnsEmpty() {
            assertEquals(List.of(), ContainerAgent.stripHandoffMessages(List.of()));
        }

        @Test
        void testIsStaticMethod() throws NoSuchMethodException {
            assertTrue(java.lang.reflect.Modifier.isStatic(
                    ContainerAgent.class.getMethod("stripHandoffMessages", List.class).getModifiers()));
        }
    }

    @Nested
    class TestGetTargetAgent {
        @Test
        void testProviderCalledOnFirstAccess() {
            AtomicInteger calls = new AtomicInteger();
            StubAgent target = new StubAgent(card("a"), Map.of());
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> {
                calls.incrementAndGet();
                return target;
            });
            assertSame(target, agent.target());
            assertEquals(1, calls.get());
        }

        @Test
        void testProviderCalledOnlyOnce() {
            AtomicInteger calls = new AtomicInteger();
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> {
                calls.incrementAndGet();
                return new StubAgent(card("a"), Map.of());
            });
            agent.target();
            agent.target();
            assertEquals(1, calls.get());
        }

        @Test
        void testSameInstanceReturned() {
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            assertSame(agent.target(), agent.target());
        }
    }

    @Nested
    class TestContainerAgentInvoke {
        @Test
        void testReturnsEmptyForNonHandoffRequest() {
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            assertEquals(Map.of(), agent.invoke("not a request", null));
        }

        @Test
        void testRaisesWhenNoCoordinatorEmptySession() {
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()),
                    ignored -> null);
            assertThrows(IllegalStateException.class, () -> agent.invoke(new HandoffRequest("hi"), null));
        }

        @Test
        void testCompletesWithAgentResult() throws Exception {
            HandoffOrchestrator coordinator = coordinator();
            StubAgent target = new StubAgent(card("a"), Map.of("answer", "done"));
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> target, ignored -> coordinator);
            assertEquals(Map.of(), agent.invoke(new HandoffRequest("hi", List.of(), "sid"), null));
            assertEquals(Map.of("answer", "done"), coordinator.getDoneFuture().get());
        }

        @Test
        void testInvokeReturnsEmptyDict() {
            HandoffOrchestrator coordinator = coordinator();
            TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                    () -> new StubAgent(card("a"), Map.of("ok", true)), ignored -> coordinator);
            assertEquals(Map.of(), agent.invoke(new HandoffRequest("hi", List.of(), "sid"), null));
        }

        @Test
        void testErrorCalledOnAgentException() {
            HandoffOrchestrator coordinator = coordinator();
            TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                    () -> new StubAgent(card("a"), Map.of(), new RuntimeException("crash")), ignored -> coordinator);
            assertEquals(Map.of(), agent.invoke(new HandoffRequest("hi", List.of(), "sid"), null));
            assertTrue(coordinator.getDoneFuture().isCompletedExceptionally());
        }
    }

    @Nested
    class TestContainerAgentStream {
        @Test
        void testStreamYieldsOneChunk() {
            HandoffOrchestrator coordinator = coordinator();
            TestableContainerAgent agent = new TestableContainerAgent(card("a"),
                    () -> new StubAgent(card("a"), Map.of("ok", true)), ignored -> coordinator);
            Iterator<Object> chunks = agent.stream(new HandoffRequest("hi", List.of(), "sid"), null, List.of());
            assertTrue(chunks.hasNext());
            assertEquals(Map.of(), chunks.next());
            assertFalse(chunks.hasNext());
        }

        @Test
        void testStreamNonRequestYieldsEmpty() {
            TestableContainerAgent agent = new TestableContainerAgent(card("a"), () -> new StubAgent(card("a"), Map.of()));
            Iterator<Object> chunks = agent.stream("not a request", null, List.of());
            assertEquals(Map.of(), chunks.next());
            assertFalse(chunks.hasNext());
        }
    }
}
