/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.HandoffSignal;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff signal.
 *
 * <p>Mirrors Python's {@code test_handoff_signal.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffSignal {

    private static final String TARGET = HandoffSignal.HANDOFF_TARGET_KEY;
    private static final String MESSAGE = HandoffSignal.HANDOFF_MESSAGE_KEY;
    private static final String REASON = HandoffSignal.HANDOFF_REASON_KEY;

    private static final class FakeSession implements Session {
        private final Object contextState;

        FakeSession(Object contextState) {
            this.contextState = contextState;
        }

        @Override
        public String getSessionId() {
            return "sid";
        }

        @Override
        public Object getState(String key) {
            return "context".equals(key) ? contextState : null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }
    }

    private static Map<String, Object> contextWithMessages(List<?> messages) {
        return Map.of("default_context_id", Map.of("messages", messages, "offload_messages", Map.of()));
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    @Nested
    class TestHandoffSignalData {
        @Test
        void testTargetStored() {
            assertEquals("agent_b", new HandoffSignal("agent_b").getTarget());
        }

        @Test
        void testMessageDefaultsToEmptyOptional() {
            assertTrue(new HandoffSignal("b").getMessage().isEmpty());
        }

        @Test
        void testReasonDefaultsToEmptyOptional() {
            assertTrue(new HandoffSignal("b").getReason().isEmpty());
        }

        @Test
        void testCustomMessage() {
            assertEquals("context", new HandoffSignal("b", "context", null).getMessage().orElseThrow());
        }

        @Test
        void testCustomReason() {
            assertEquals("needs billing", new HandoffSignal("b", null, "needs billing").getReason().orElseThrow());
        }

        @Test
        void testFrozenPreventsTargetMutation() throws NoSuchFieldException {
            Field field = HandoffSignal.class.getDeclaredField("target");
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(Modifier.isFinal(HandoffSignal.class.getModifiers()));
        }

        @Test
        void testEqualityBasedOnValues() {
            assertEquals(new HandoffSignal("b", "m", "r"), new HandoffSignal("b", "m", "r"));
        }

        @Test
        void testInequalityDifferentTarget() {
            assertNotEquals(new HandoffSignal("a"), new HandoffSignal("b"));
        }
    }

    @Nested
    class TestExtractHandoffSignal {
        @Test
        void testDirectDictWithTarget() {
            Optional<HandoffSignal> signal = HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b"));
            assertTrue(signal.isPresent());
            assertEquals("b", signal.orElseThrow().getTarget());
        }

        @Test
        void testDirectDictWithReason() {
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b", REASON, "needs billing"))
                    .orElseThrow();
            assertEquals("needs billing", signal.getReason().orElseThrow());
        }

        @Test
        void testDirectDictWithMessage() {
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b", MESSAGE, "carry this"))
                    .orElseThrow();
            assertEquals("carry this", signal.getMessage().orElseThrow());
        }

        @Test
        void testNestedUnderOutputKey() {
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(
                    Map.of("output", Map.of(TARGET, "c", MESSAGE, "ctx"))).orElseThrow();
            assertEquals("c", signal.getTarget());
            assertEquals("ctx", signal.getMessage().orElseThrow());
        }

        @Test
        void testNestedUnderResultKey() {
            assertEquals("d", HandoffSignal.extractHandoffSignal(Map.of("result", Map.of(TARGET, "d")))
                    .orElseThrow().getTarget());
        }

        @Test
        void testNestedUnderContentKey() {
            assertEquals("e", HandoffSignal.extractHandoffSignal(Map.of("content", Map.of(TARGET, "e")))
                    .orElseThrow().getTarget());
        }

        @Test
        void testNoHandoffKeyReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of("result_type", "answer")).isEmpty());
        }

        @Test
        void testEmptyDictReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of()).isEmpty());
        }

        @Test
        void testNoneInputReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(null).isEmpty());
        }

        @Test
        void testStringInputReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal("plain string").isEmpty());
        }

        @Test
        void testListInputReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(List.of(Map.of(TARGET, "b"))).isEmpty());
        }

        @Test
        void testIntInputReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(42).isEmpty());
        }

        @Test
        void testEmptyTargetStringReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, "")).isEmpty());
        }

        @Test
        void testNonStringTargetIntReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, 123)).isEmpty());
        }

        @Test
        void testNonStringTargetNoneReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(new java.util.HashMap<>(Map.of(TARGET, new Object()))).isEmpty());
        }

        @Test
        void testNonStringTargetListReturnsNone() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, List.of("agent"))).isEmpty());
        }

        @Test
        void testMessageNoneWhenKeyAbsent() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b")).orElseThrow().getMessage().isEmpty());
        }

        @Test
        void testReasonNoneWhenKeyAbsent() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b")).orElseThrow().getReason().isEmpty());
        }

        @Test
        void testMessageNoneWhenEmptyString() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b", MESSAGE, ""))
                    .orElseThrow().getMessage().isEmpty());
        }

        @Test
        void testReasonNoneWhenEmptyString() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of(TARGET, "b", REASON, ""))
                    .orElseThrow().getReason().isEmpty());
        }

        @Test
        void testAllFieldsPopulated() {
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(
                    Map.of(TARGET, "agent_x", MESSAGE, "context data", REASON, "specialist needed")).orElseThrow();
            assertEquals("agent_x", signal.getTarget());
            assertEquals("context data", signal.getMessage().orElseThrow());
            assertEquals("specialist needed", signal.getReason().orElseThrow());
        }

        @Test
        void testDirectKeyTakesPriorityOverNested() {
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(
                    Map.of(TARGET, "direct_agent", "output", Map.of(TARGET, "nested_agent"))).orElseThrow();
            assertEquals("direct_agent", signal.getTarget());
        }

        @Test
        void testNestedOutputNonDictIgnored() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of("output", "not a dict")).isEmpty());
        }

        @Test
        void testNestedResultNonDictIgnored() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of("result", 42)).isEmpty());
        }

        @Test
        void testNestedContentNonDictIgnored() {
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of("content", List.of("list"))).isEmpty());
        }

        @Test
        void testConstantTargetKeyValue() {
            assertEquals("__handoff_to__", TARGET);
        }

        @Test
        void testConstantMessageKeyValue() {
            assertEquals("__handoff_message__", MESSAGE);
        }

        @Test
        void testConstantReasonKeyValue() {
            assertEquals("__handoff_reason__", REASON);
        }
    }

    @Nested
    class TestFindHandoffFromSession {
        @Test
        void testFindsJsonHandoffFromToolMessage() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("user", "I need help"),
                    message("tool", "{\"__handoff_to__\":\"billing_agent\",\"__handoff_reason__\":\"billing question\"}"))));
            Map<String, Object> result = HandoffSignal.findHandoffFromSession(session).orElseThrow();
            assertEquals("billing_agent", result.get(TARGET));
            assertEquals("billing question", result.get(REASON));
        }

        @Test
        void testFindsHandoffFromPythonDictRepr() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "{'__handoff_to__': 'tech_agent', '__handoff_message__': 'escalate'}"))));
            Map<String, Object> result = HandoffSignal.findHandoffFromSession(session).orElseThrow();
            assertEquals("tech_agent", result.get(TARGET));
            assertEquals("escalate", result.get(MESSAGE));
        }

        @Test
        void testReversedSearchReturnsLastHandoff() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "{\"__handoff_to__\":\"first_agent\"}"),
                    message("tool", "{\"__handoff_to__\":\"second_agent\"}"))));
            assertEquals("second_agent", HandoffSignal.findHandoffFromSession(session).orElseThrow().get(TARGET));
        }

        @Test
        void testNonToolMessagesIgnored() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("assistant", "{\"__handoff_to__\":\"billing_agent\"}"))));
            assertTrue(HandoffSignal.findHandoffFromSession(session).isEmpty());
        }

        @Test
        void testReturnsNoneWhenNoHandoffKey() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "{\"result_type\":\"answer\"}"))));
            assertTrue(HandoffSignal.findHandoffFromSession(session).isEmpty());
        }

        @Test
        void testReturnsNoneForUnparseableContent() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "not valid json or python"))));
            assertTrue(HandoffSignal.findHandoffFromSession(session).isEmpty());
        }

        @Test
        void testReturnsNoneWhenAgentSessionIsNone() {
            assertTrue(HandoffSignal.findHandoffFromSession(null).isEmpty());
        }

        @Test
        void testReturnsNoneWhenContextStateMissing() {
            assertTrue(HandoffSignal.findHandoffFromSession(new FakeSession(null)).isEmpty());
        }

        @Test
        void testReturnsNoneWhenContextStateNotDict() {
            assertTrue(HandoffSignal.findHandoffFromSession(new FakeSession("not a dict")).isEmpty());
        }

        @Test
        void testReturnsNoneWhenNoMessages() {
            assertTrue(HandoffSignal.findHandoffFromSession(new FakeSession(contextWithMessages(List.of()))).isEmpty());
        }

        @Test
        void testReturnsNoneWhenDefaultContextKeyMissing() {
            assertTrue(HandoffSignal.findHandoffFromSession(new FakeSession(Map.of())).isEmpty());
        }

        @Test
        void testEmptyToolContentIgnored() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(message("tool", ""))));
            assertTrue(HandoffSignal.findHandoffFromSession(session).isEmpty());
        }
    }

    @Nested
    class TestExtractHandoffSignalWithSession {
        @Test
        void testResultWithoutHandoffRecoveredFromSession() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "{\"__handoff_to__\":\"recovered_agent\"}"))));
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(Map.of("output", "plain answer"), session)
                    .orElseThrow();
            assertEquals("recovered_agent", signal.getTarget());
        }

        @Test
        void testResultHandoffTakesPriorityOverSession() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "{\"__handoff_to__\":\"session_agent\"}"))));
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(Map.of(TARGET, "result_agent"), session)
                    .orElseThrow();
            assertEquals("result_agent", signal.getTarget());
        }

        @Test
        void testNoHandoffInResultOrSessionReturnsNone() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(message("tool", "{\"result_type\":\"answer\"}"))));
            assertTrue(HandoffSignal.extractHandoffSignal(Map.of("output", "hello"), session).isEmpty());
        }

        @Test
        void testNoneAgentSessionFallsBackToResultOnly() {
            assertEquals("direct_agent", HandoffSignal.extractHandoffSignal(Map.of(TARGET, "direct_agent"))
                    .orElseThrow().getTarget());
        }

        @Test
        void testSessionRecoverySuppliesOptionalFields() {
            FakeSession session = new FakeSession(contextWithMessages(List.of(
                    message("tool", "{\"__handoff_to__\":\"specialist\","
                            + "\"__handoff_message__\":\"urgent\","
                            + "\"__handoff_reason__\":\"complex issue\"}"))));
            HandoffSignal signal = HandoffSignal.extractHandoffSignal(Map.of("output", "ignored"), session)
                    .orElseThrow();
            assertEquals("specialist", signal.getTarget());
            assertEquals("urgent", signal.getMessage().orElseThrow());
            assertEquals("complex issue", signal.getReason().orElseThrow());
        }
    }
}
