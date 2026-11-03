/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.foundation.tool.Tool;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.multi_agent.builtin_teams.handoff.test_handoff_tool} in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_tool.py}.</p>
 */
class HandoffToolPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_is_tool_subclass",
            "test_card_name_prefixed_with_transfer_to",
            "test_card_id_matches_name",
            "test_description_contains_target_id",
            "test_description_appends_target_description",
            "test_description_without_target_description_non_empty",
            "test_input_params_schema_type_object",
            "test_input_params_reason_is_required",
            "test_input_params_message_not_required",
            "test_input_params_reason_property_type",
            "test_input_params_message_property_type",
            "test_dict_input_target_key",
            "test_dict_input_reason_key",
            "test_dict_input_message_key",
            "test_json_string_input_target",
            "test_json_string_input_reason",
            "test_plain_string_fallback_target",
            "test_plain_string_fallback_reason",
            "test_empty_dict_input_reason_empty",
            "test_empty_dict_input_message_empty",
            "test_none_input_treated_as_empty_dict",
            "test_list_input_treated_as_empty_dict",
            "test_missing_message_defaults_to_empty_string",
            "test_none_message_defaults_to_empty_string",
            "test_none_reason_defaults_to_empty_string",
            "test_result_has_all_three_keys",
            "test_different_target_ids_produce_correct_target",
            "test_stream_yields_exactly_one_chunk",
            "test_stream_chunk_has_target_key",
            "test_stream_chunk_matches_invoke_result",
            "test_stream_with_empty_input"
    );

    @TestFactory
    Collection<DynamicTest> pythonHandoffToolCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "test_is_tool_subclass" -> isToolSubclass();
            case "test_card_name_prefixed_with_transfer_to" -> cardNamePrefixedWithTransferTo();
            case "test_card_id_matches_name" -> cardIdMatchesName();
            case "test_description_contains_target_id" -> descriptionContainsTargetId();
            case "test_description_appends_target_description" -> descriptionAppendsTargetDescription();
            case "test_description_without_target_description_non_empty" -> descriptionWithoutTargetDescriptionNonEmpty();
            case "test_input_params_schema_type_object" -> inputParamsSchemaTypeObject();
            case "test_input_params_reason_is_required" -> inputParamsReasonIsRequired();
            case "test_input_params_message_not_required" -> inputParamsMessageNotRequired();
            case "test_input_params_reason_property_type" -> inputParamsReasonPropertyType();
            case "test_input_params_message_property_type" -> inputParamsMessagePropertyType();
            case "test_dict_input_target_key" -> dictInputTargetKey();
            case "test_dict_input_reason_key" -> dictInputReasonKey();
            case "test_dict_input_message_key" -> dictInputMessageKey();
            case "test_json_string_input_target" -> jsonStringInputTarget();
            case "test_json_string_input_reason" -> jsonStringInputReason();
            case "test_plain_string_fallback_target" -> plainStringFallbackTarget();
            case "test_plain_string_fallback_reason" -> plainStringFallbackReason();
            case "test_empty_dict_input_reason_empty" -> emptyDictInputReasonEmpty();
            case "test_empty_dict_input_message_empty" -> emptyDictInputMessageEmpty();
            case "test_none_input_treated_as_empty_dict" -> noneInputTreatedAsEmptyDict();
            case "test_list_input_treated_as_empty_dict" -> listInputTreatedAsEmptyDict();
            case "test_missing_message_defaults_to_empty_string" -> missingMessageDefaultsToEmptyString();
            case "test_none_message_defaults_to_empty_string" -> noneMessageDefaultsToEmptyString();
            case "test_none_reason_defaults_to_empty_string" -> noneReasonDefaultsToEmptyString();
            case "test_result_has_all_three_keys" -> resultHasAllThreeKeys();
            case "test_different_target_ids_produce_correct_target" -> differentTargetIdsProduceCorrectTarget();
            case "test_stream_yields_exactly_one_chunk" -> streamYieldsExactlyOneChunk();
            case "test_stream_chunk_has_target_key" -> streamChunkHasTargetKey();
            case "test_stream_chunk_matches_invoke_result" -> streamChunkMatchesInvokeResult();
            case "test_stream_with_empty_input" -> streamWithEmptyInput();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void isToolSubclass() {
        assertInstanceOf(Tool.class, new HandoffTool("b"));
    }

    private void cardNamePrefixedWithTransferTo() {
        assertEquals("transfer_to_agent_b", new HandoffTool("agent_b").getCard().getName());
    }

    private void cardIdMatchesName() {
        assertEquals("transfer_to_agent_b", new HandoffTool("agent_b").getCard().getId());
    }

    private void descriptionContainsTargetId() {
        assertTrue(new HandoffTool("billing_agent").getCard().getDescription().contains("billing_agent"));
    }

    private void descriptionAppendsTargetDescription() {
        assertTrue(new HandoffTool("b", "handles billing").getCard().getDescription().contains("handles billing"));
    }

    private void descriptionWithoutTargetDescriptionNonEmpty() {
        assertTrue(new HandoffTool("b", "").getCard().getDescription().length() > 0);
    }

    private void inputParamsSchemaTypeObject() {
        assertEquals("object", new HandoffTool("b").getCard().getInputParams().get("type"));
    }

    private void inputParamsReasonIsRequired() {
        assertTrue(required(new HandoffTool("b")).contains("reason"));
    }

    private void inputParamsMessageNotRequired() {
        assertFalse(required(new HandoffTool("b")).contains("message"));
    }

    private void inputParamsReasonPropertyType() {
        assertEquals("string", property(new HandoffTool("b"), "reason").get("type"));
    }

    private void inputParamsMessagePropertyType() {
        assertEquals("string", property(new HandoffTool("b"), "message").get("type"));
    }

    private void dictInputTargetKey() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of("reason", "go", "message", "ctx"));

        assertEquals("b", result.get(HandoffSignal.HANDOFF_TARGET_KEY));
    }

    private void dictInputReasonKey() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of("reason", "need billing", "message", ""));

        assertEquals("need billing", result.get(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void dictInputMessageKey() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of("reason", "r", "message", "carry this"));

        assertEquals("carry this", result.get(HandoffSignal.HANDOFF_MESSAGE_KEY));
    }

    private void jsonStringInputTarget() {
        Map<String, Object> result = new HandoffTool("b").invokePayload("{\"reason\":\"go\",\"message\":\"hi\"}");

        assertEquals("b", result.get(HandoffSignal.HANDOFF_TARGET_KEY));
    }

    private void jsonStringInputReason() {
        Map<String, Object> result = new HandoffTool("b").invokePayload("{\"reason\":\"json reason\"}");

        assertEquals("json reason", result.get(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void plainStringFallbackTarget() {
        Map<String, Object> result = new HandoffTool("b").invokePayload("plain fallback reason");

        assertEquals("b", result.get(HandoffSignal.HANDOFF_TARGET_KEY));
    }

    private void plainStringFallbackReason() {
        Map<String, Object> result = new HandoffTool("b").invokePayload("plain fallback reason");

        assertEquals("plain fallback reason", result.get(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void emptyDictInputReasonEmpty() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of());

        assertEquals("", result.get(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void emptyDictInputMessageEmpty() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of());

        assertEquals("", result.get(HandoffSignal.HANDOFF_MESSAGE_KEY));
    }

    private void noneInputTreatedAsEmptyDict() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(null);

        assertEquals("b", result.get(HandoffSignal.HANDOFF_TARGET_KEY));
        assertEquals("", result.get(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void listInputTreatedAsEmptyDict() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(List.of("not", "a", "dict"));

        assertEquals("b", result.get(HandoffSignal.HANDOFF_TARGET_KEY));
    }

    private void missingMessageDefaultsToEmptyString() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of("reason", "only reason"));

        assertEquals("", result.get(HandoffSignal.HANDOFF_MESSAGE_KEY));
    }

    private void noneMessageDefaultsToEmptyString() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(mapOf("reason", "r", "message", null));

        assertEquals("", result.get(HandoffSignal.HANDOFF_MESSAGE_KEY));
    }

    private void noneReasonDefaultsToEmptyString() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(mapOf("reason", null));

        assertEquals("", result.get(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void resultHasAllThreeKeys() {
        Map<String, Object> result = new HandoffTool("b").invokePayload(Map.of("reason", "r"));

        assertTrue(result.containsKey(HandoffSignal.HANDOFF_TARGET_KEY));
        assertTrue(result.containsKey(HandoffSignal.HANDOFF_MESSAGE_KEY));
        assertTrue(result.containsKey(HandoffSignal.HANDOFF_REASON_KEY));
    }

    private void differentTargetIdsProduceCorrectTarget() {
        for (String targetId : List.of("agent_x", "billing", "support_123")) {
            Map<String, Object> result = new HandoffTool(targetId).invokePayload(Map.of("reason", "go"));

            assertEquals(targetId, result.get(HandoffSignal.HANDOFF_TARGET_KEY));
        }
    }

    private void streamYieldsExactlyOneChunk() {
        List<Map<String, Object>> chunks = streamChunks(new HandoffTool("b"), Map.of("reason", "r"));

        assertEquals(1, chunks.size());
    }

    private void streamChunkHasTargetKey() {
        List<Map<String, Object>> chunks = streamChunks(new HandoffTool("b"), Map.of("reason", "r"));

        assertEquals("b", chunks.get(0).get(HandoffSignal.HANDOFF_TARGET_KEY));
    }

    private void streamChunkMatchesInvokeResult() {
        HandoffTool tool = new HandoffTool("b");
        Map<String, Object> input = Map.of("reason", "test", "message", "msg");
        Map<String, Object> invokeResult = tool.invokePayload(input);
        List<Map<String, Object>> chunks = streamChunks(tool, input);

        assertEquals(invokeResult, chunks.get(0));
    }

    private void streamWithEmptyInput() {
        List<Map<String, Object>> chunks = streamChunks(new HandoffTool("b"), Map.of());

        assertEquals(1, chunks.size());
        assertEquals("b", chunks.get(0).get(HandoffSignal.HANDOFF_TARGET_KEY));
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(HandoffTool tool) {
        Object required = tool.getCard().getInputParams().getOrDefault("required", List.of());
        return required instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(HandoffTool tool, String name) {
        Map<String, Object> properties = (Map<String, Object>) tool.getCard().getInputParams().get("properties");
        return (Map<String, Object>) properties.get(name);
    }

    private static Map<String, Object> mapOf(Object... values) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private static List<Map<String, Object>> streamChunks(HandoffTool tool, Object input) {
        Iterator<Map<String, Object>> iterator = tool.streamPayload(input);
        java.util.ArrayList<Map<String, Object>> chunks = new java.util.ArrayList<>();
        iterator.forEachRemaining(chunks::add);
        return chunks;
    }
}
