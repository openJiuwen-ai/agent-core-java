/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffSignal;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff tool.
 *
 * <p>Mirrors Python's {@code test_handoff_tool.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffTool {

    private static final String TARGET = HandoffSignal.HANDOFF_TARGET_KEY;
    private static final String MESSAGE = HandoffSignal.HANDOFF_MESSAGE_KEY;
    private static final String REASON = HandoffSignal.HANDOFF_REASON_KEY;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoke(HandoffTool tool, Object input) throws Exception {
        return (Map<String, Object>) tool.invoke(input);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(HandoffTool tool) {
        return (Map<String, Object>) tool.getCard().getInputParams().get("properties");
    }

    @Nested
    class TestHandoffToolConstruction {
        @Test
        void testIsToolSubclass() {
            assertInstanceOf(Tool.class, new HandoffTool("b"));
        }

        @Test
        void testCardNamePrefixedWithTransferTo() {
            assertEquals("transfer_to_agent_b", new HandoffTool("agent_b").getCard().getName());
        }

        @Test
        void testCardIdMatchesName() {
            assertEquals("transfer_to_agent_b", new HandoffTool("agent_b").getCard().getId());
        }

        @Test
        void testDescriptionContainsTargetId() {
            assertTrue(new HandoffTool("billing_agent").getCard().getDescription().contains("billing_agent"));
        }

        @Test
        void testDescriptionAppendsTargetDescription() {
            assertTrue(new HandoffTool("b", "handles billing").getCard().getDescription().contains("handles billing"));
        }

        @Test
        void testDescriptionWithoutTargetDescriptionNonEmpty() {
            assertFalse(new HandoffTool("b", "").getCard().getDescription().isEmpty());
        }

        @Test
        void testInputParamsSchemaTypeObject() {
            assertEquals("object", new HandoffTool("b").getCard().getInputParams().get("type"));
        }

        @Test
        void testInputParamsReasonIsRequired() {
            assertTrue(((java.util.List<?>) new HandoffTool("b").getCard().getInputParams().get("required"))
                    .contains("reason"));
        }

        @Test
        void testInputParamsMessageNotRequired() {
            assertFalse(((java.util.List<?>) new HandoffTool("b").getCard().getInputParams().get("required"))
                    .contains("message"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testInputParamsReasonPropertyType() {
            assertEquals("string", ((Map<String, Object>) properties(new HandoffTool("b")).get("reason")).get("type"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testInputParamsMessagePropertyType() {
            assertEquals("string", ((Map<String, Object>) properties(new HandoffTool("b")).get("message")).get("type"));
        }
    }

    @Nested
    class TestHandoffToolInvoke {
        @Test
        void testDictInputTargetKey() throws Exception {
            assertEquals("b", invoke(new HandoffTool("b"), Map.of("reason", "go", "message", "ctx")).get(TARGET));
        }

        @Test
        void testDictInputReasonKey() throws Exception {
            assertEquals("need billing", invoke(new HandoffTool("b"),
                    Map.of("reason", "need billing", "message", "")).get(REASON));
        }

        @Test
        void testDictInputMessageKey() throws Exception {
            assertEquals("carry this", invoke(new HandoffTool("b"),
                    Map.of("reason", "r", "message", "carry this")).get(MESSAGE));
        }

        @Test
        void testJsonStringInputTarget() throws Exception {
            assertEquals("b", invoke(new HandoffTool("b"), "{\"reason\":\"go\",\"message\":\"hi\"}").get(TARGET));
        }

        @Test
        void testJsonStringInputReason() throws Exception {
            assertEquals("json reason", invoke(new HandoffTool("b"), "{\"reason\":\"json reason\"}").get(REASON));
        }

        @Test
        void testPlainStringFallbackTarget() throws Exception {
            assertEquals("b", invoke(new HandoffTool("b"), "plain fallback reason").get(TARGET));
        }

        @Test
        void testPlainStringFallbackReason() throws Exception {
            assertEquals("plain fallback reason", invoke(new HandoffTool("b"), "plain fallback reason").get(REASON));
        }

        @Test
        void testEmptyDictInputReasonEmpty() throws Exception {
            assertEquals("", invoke(new HandoffTool("b"), Map.of()).get(REASON));
        }

        @Test
        void testEmptyDictInputMessageEmpty() throws Exception {
            assertEquals("", invoke(new HandoffTool("b"), Map.of()).get(MESSAGE));
        }

        @Test
        void testNoneInputTreatedAsEmptyDict() throws Exception {
            Map<String, Object> result = invoke(new HandoffTool("b"), null);
            assertEquals("b", result.get(TARGET));
            assertEquals("", result.get(REASON));
        }

        @Test
        void testListInputTreatedAsEmptyDict() throws Exception {
            assertEquals("b", invoke(new HandoffTool("b"), java.util.List.of("not", "a", "dict")).get(TARGET));
        }

        @Test
        void testMissingMessageDefaultsToEmptyString() throws Exception {
            assertEquals("", invoke(new HandoffTool("b"), Map.of("reason", "only reason")).get(MESSAGE));
        }

        @Test
        void testNoneMessageDefaultsToEmptyString() throws Exception {
            Map<String, Object> input = new java.util.HashMap<>();
            input.put("reason", "r");
            input.put("message", null);
            assertEquals("", invoke(new HandoffTool("b"), input).get(MESSAGE));
        }

        @Test
        void testNoneReasonDefaultsToEmptyString() throws Exception {
            Map<String, Object> input = new java.util.HashMap<>();
            input.put("reason", null);
            assertEquals("", invoke(new HandoffTool("b"), input).get(REASON));
        }

        @Test
        void testResultHasAllThreeKeys() throws Exception {
            Map<String, Object> result = invoke(new HandoffTool("b"), Map.of("reason", "r"));
            assertTrue(result.containsKey(TARGET));
            assertTrue(result.containsKey(MESSAGE));
            assertTrue(result.containsKey(REASON));
        }

        @Test
        void testDifferentTargetIdsProduceCorrectTarget() throws Exception {
            for (String targetId : java.util.List.of("agent_x", "billing", "support_123")) {
                assertEquals(targetId, invoke(new HandoffTool(targetId), Map.of("reason", "go")).get(TARGET));
            }
        }
    }

    @Nested
    class TestHandoffToolStream {
        @Test
        void testStreamYieldsExactlyOneChunk() throws Exception {
            Iterator<Object> chunks = new HandoffTool("b").stream(Map.of("reason", "r"));
            assertTrue(chunks.hasNext());
            chunks.next();
            assertFalse(chunks.hasNext());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testStreamChunkHasTargetKey() throws Exception {
            Map<String, Object> chunk = (Map<String, Object>) new HandoffTool("b")
                    .stream(Map.of("reason", "r")).next();
            assertEquals("b", chunk.get(TARGET));
        }

        @Test
        void testStreamChunkMatchesInvokeResult() throws Exception {
            HandoffTool tool = new HandoffTool("b");
            Object invokeResult = tool.invoke(Map.of("reason", "test", "message", "msg"));
            Object streamResult = tool.stream(Map.of("reason", "test", "message", "msg")).next();
            assertEquals(invokeResult, streamResult);
        }

        @Test
        @SuppressWarnings("unchecked")
        void testStreamWithEmptyInput() throws Exception {
            Iterator<Object> chunks = new HandoffTool("b").stream(Map.of());
            Map<String, Object> chunk = (Map<String, Object>) chunks.next();
            assertFalse(chunks.hasNext());
            assertEquals("b", chunk.get(TARGET));
        }
    }
}
