/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's handoff-signal unit coverage in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_signal.py}.
 */
class HandoffSignalTest {

    private static final String PYTHON_FILE =
            "tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_signal.py";
    private static final String DEFAULT_CONTEXT_ID = "default_context_id";

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonParityCases")
    void mirrorsPythonHandoffSignalNodes(String nodeId, Executable assertion) throws Throwable {
        assertThat(nodeId).startsWith(PYTHON_FILE);
        assertion.execute();
    }

    @Test
    void extractsDirectAndNestedPayloads() {
        Optional<HandoffSignal> direct = HandoffSignal.extractHandoffSignal(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "agent_b",
                HandoffSignal.HANDOFF_MESSAGE_KEY, "context",
                HandoffSignal.HANDOFF_REASON_KEY, "needs billing"
        ));
        Optional<HandoffSignal> nested = HandoffSignal.extractHandoffSignal(map(
                "output",
                map(HandoffSignal.HANDOFF_TARGET_KEY, "agent_c")
        ));

        assertThat(direct).isPresent();
        assertThat(direct.orElseThrow().getTarget()).isEqualTo("agent_b");
        assertThat(direct.orElseThrow().getMessage()).contains("context");
        assertThat(direct.orElseThrow().getReason()).contains("needs billing");
        assertThat(nested).isPresent();
        assertThat(nested.orElseThrow().getTarget()).isEqualTo("agent_c");
    }

    @Test
    void invalidTargetsReturnEmpty() {
        assertThat(HandoffSignal.extractHandoffSignal(map())).isEmpty();
        assertThat(HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, ""))).isEmpty();
        assertThat(HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, 123))).isEmpty();
        assertThat(HandoffSignal.extractHandoffSignal("plain string")).isEmpty();
    }

    @Test
    void recoversLatestToolPayloadFromSessionJsonOrPythonDictText() {
        FakeSession jsonSession = new FakeSession(contextWithMessages(List.of(
                message("tool", "{\"__handoff_to__\":\"billing_agent\",\"__handoff_reason__\":\"billing question\"}")
        )));
        FakeSession pythonDictSession = new FakeSession(contextWithMessages(List.of(
                message("tool", "{'__handoff_to__': 'tech_agent', '__handoff_message__': 'escalate'}")
        )));

        assertThat(HandoffSignal.findHandoffFromSession(jsonSession))
                .contains(map(
                        HandoffSignal.HANDOFF_TARGET_KEY, "billing_agent",
                        HandoffSignal.HANDOFF_REASON_KEY, "billing question"
                ));
        assertThat(HandoffSignal.findHandoffFromSession(pythonDictSession))
                .contains(map(
                        HandoffSignal.HANDOFF_TARGET_KEY, "tech_agent",
                        HandoffSignal.HANDOFF_MESSAGE_KEY, "escalate"
                ));
    }

    @Test
    void directResultPayloadTakesPriorityOverRecoveredSessionPayload() {
        FakeSession session = sessionWithMessages(List.of(
                message("tool", "{\"__handoff_to__\":\"session_agent\"}")
        ));

        HandoffSignal signal = HandoffSignal.extractHandoffSignal(
                map(HandoffSignal.HANDOFF_TARGET_KEY, "result_agent"),
                session
        ).orElseThrow();

        assertThat(signal.getTarget()).isEqualTo("result_agent");
    }

    @Test
    void sessionRecoverySkipsNonToolAndMalformedMessages() {
        FakeSession session = sessionWithMessages(List.of(
                message("assistant", "{\"__handoff_to__\":\"assistant_agent\"}"),
                message("tool", "not valid json or python"),
                message("tool", "{\"result_type\":\"answer\"}"),
                message("tool", "{\"__handoff_to__\":\"recovered_agent\"}")
        ));

        HandoffSignal signal = HandoffSignal.extractHandoffSignal(map("output", "plain answer"), session)
                .orElseThrow();

        assertThat(signal.getTarget()).isEqualTo("recovered_agent");
    }

    private static Stream<Arguments> pythonParityCases() {
        return Stream.of(
                node("TestHandoffSignal::test_target_stored", HandoffSignalTest::assertTargetStored),
                node("TestHandoffSignal::test_message_defaults_to_none",
                        HandoffSignalTest::assertMessageDefaultsToNone),
                node("TestHandoffSignal::test_reason_defaults_to_none",
                        HandoffSignalTest::assertReasonDefaultsToNone),
                node("TestHandoffSignal::test_custom_message", HandoffSignalTest::assertCustomMessage),
                node("TestHandoffSignal::test_custom_reason", HandoffSignalTest::assertCustomReason),
                node("TestHandoffSignal::test_frozen_prevents_target_mutation",
                        HandoffSignalTest::assertFrozenPreventsTargetMutation),
                node("TestHandoffSignal::test_equality_based_on_values",
                        HandoffSignalTest::assertEqualityBasedOnValues),
                node("TestHandoffSignal::test_inequality_different_target",
                        HandoffSignalTest::assertInequalityDifferentTarget),
                node("TestExtractHandoffSignal::test_direct_dict_with_target",
                        HandoffSignalTest::assertDirectDictWithTarget),
                node("TestExtractHandoffSignal::test_direct_dict_with_reason",
                        HandoffSignalTest::assertDirectDictWithReason),
                node("TestExtractHandoffSignal::test_direct_dict_with_message",
                        HandoffSignalTest::assertDirectDictWithMessage),
                node("TestExtractHandoffSignal::test_nested_under_output_key",
                        HandoffSignalTest::assertNestedUnderOutputKey),
                node("TestExtractHandoffSignal::test_nested_under_result_key",
                        HandoffSignalTest::assertNestedUnderResultKey),
                node("TestExtractHandoffSignal::test_nested_under_content_key",
                        HandoffSignalTest::assertNestedUnderContentKey),
                node("TestExtractHandoffSignal::test_no_handoff_key_returns_none",
                        HandoffSignalTest::assertNoHandoffKeyReturnsNone),
                node("TestExtractHandoffSignal::test_empty_dict_returns_none",
                        HandoffSignalTest::assertEmptyDictReturnsNone),
                node("TestExtractHandoffSignal::test_none_input_returns_none",
                        HandoffSignalTest::assertNoneInputReturnsNone),
                node("TestExtractHandoffSignal::test_string_input_returns_none",
                        HandoffSignalTest::assertStringInputReturnsNone),
                node("TestExtractHandoffSignal::test_list_input_returns_none",
                        HandoffSignalTest::assertListInputReturnsNone),
                node("TestExtractHandoffSignal::test_int_input_returns_none",
                        HandoffSignalTest::assertIntInputReturnsNone),
                node("TestExtractHandoffSignal::test_empty_target_string_returns_none",
                        HandoffSignalTest::assertEmptyTargetStringReturnsNone),
                node("TestExtractHandoffSignal::test_non_string_target_int_returns_none",
                        HandoffSignalTest::assertNonStringTargetIntReturnsNone),
                node("TestExtractHandoffSignal::test_non_string_target_none_returns_none",
                        HandoffSignalTest::assertNonStringTargetNoneReturnsNone),
                node("TestExtractHandoffSignal::test_non_string_target_list_returns_none",
                        HandoffSignalTest::assertNonStringTargetListReturnsNone),
                node("TestExtractHandoffSignal::test_message_none_when_key_absent",
                        HandoffSignalTest::assertMessageNoneWhenKeyAbsent),
                node("TestExtractHandoffSignal::test_reason_none_when_key_absent",
                        HandoffSignalTest::assertReasonNoneWhenKeyAbsent),
                node("TestExtractHandoffSignal::test_message_none_when_empty_string",
                        HandoffSignalTest::assertMessageNoneWhenEmptyString),
                node("TestExtractHandoffSignal::test_reason_none_when_empty_string",
                        HandoffSignalTest::assertReasonNoneWhenEmptyString),
                node("TestExtractHandoffSignal::test_all_fields_populated",
                        HandoffSignalTest::assertAllFieldsPopulated),
                node("TestExtractHandoffSignal::test_direct_key_takes_priority_over_nested",
                        HandoffSignalTest::assertDirectKeyTakesPriorityOverNested),
                node("TestExtractHandoffSignal::test_nested_output_non_dict_ignored",
                        HandoffSignalTest::assertNestedOutputNonDictIgnored),
                node("TestExtractHandoffSignal::test_nested_result_non_dict_ignored",
                        HandoffSignalTest::assertNestedResultNonDictIgnored),
                node("TestExtractHandoffSignal::test_nested_content_non_dict_ignored",
                        HandoffSignalTest::assertNestedContentNonDictIgnored),
                node("TestExtractHandoffSignal::test_constant_target_key_value",
                        HandoffSignalTest::assertConstantTargetKeyValue),
                node("TestExtractHandoffSignal::test_constant_message_key_value",
                        HandoffSignalTest::assertConstantMessageKeyValue),
                node("TestExtractHandoffSignal::test_constant_reason_key_value",
                        HandoffSignalTest::assertConstantReasonKeyValue),
                node("TestFindHandoffFromSession::test_finds_json_handoff_from_tool_message",
                        HandoffSignalTest::assertFindsJsonHandoffFromToolMessage),
                node("TestFindHandoffFromSession::test_finds_handoff_from_python_dict_repr",
                        HandoffSignalTest::assertFindsHandoffFromPythonDictRepr),
                node("TestFindHandoffFromSession::test_reversed_search_returns_last_handoff",
                        HandoffSignalTest::assertReversedSearchReturnsLastHandoff),
                node("TestFindHandoffFromSession::test_non_tool_messages_ignored",
                        HandoffSignalTest::assertNonToolMessagesIgnored),
                node("TestFindHandoffFromSession::test_returns_none_when_no_handoff_key",
                        HandoffSignalTest::assertSessionNoHandoffKeyReturnsNone),
                node("TestFindHandoffFromSession::test_returns_none_for_unparseable_content",
                        HandoffSignalTest::assertUnparseableContentReturnsNone),
                node("TestFindHandoffFromSession::test_returns_none_when_agent_session_is_none",
                        HandoffSignalTest::assertAgentSessionNoneReturnsNone),
                node("TestFindHandoffFromSession::test_returns_none_when_context_state_missing",
                        HandoffSignalTest::assertContextStateMissingReturnsNone),
                node("TestFindHandoffFromSession::test_returns_none_when_context_state_not_dict",
                        HandoffSignalTest::assertContextStateNotDictReturnsNone),
                node("TestFindHandoffFromSession::test_returns_none_when_no_messages",
                        HandoffSignalTest::assertNoMessagesReturnsNone),
                node("TestFindHandoffFromSession::test_returns_none_when_default_context_key_missing",
                        HandoffSignalTest::assertDefaultContextKeyMissingReturnsNone),
                node("TestFindHandoffFromSession::test_empty_tool_content_ignored",
                        HandoffSignalTest::assertEmptyToolContentIgnored),
                node("TestExtractHandoffSignalWithSession::test_result_without_handoff_recovered_from_session",
                        HandoffSignalTest::assertResultWithoutHandoffRecoveredFromSession),
                node("TestExtractHandoffSignalWithSession::test_result_handoff_takes_priority_over_session",
                        HandoffSignalTest::assertResultHandoffTakesPriorityOverSession),
                node("TestExtractHandoffSignalWithSession::test_no_handoff_in_result_or_session_returns_none",
                        HandoffSignalTest::assertNoHandoffInResultOrSessionReturnsNone),
                node("TestExtractHandoffSignalWithSession::test_none_agent_session_falls_back_to_result_only",
                        HandoffSignalTest::assertNoneAgentSessionFallsBackToResultOnly),
                node("TestExtractHandoffSignalWithSession::test_session_recovery_supplies_optional_fields",
                        HandoffSignalTest::assertSessionRecoverySuppliesOptionalFields)
        );
    }

    private static Arguments node(String nodeId, Executable assertion) {
        return Arguments.of(PYTHON_FILE + "::" + nodeId, assertion);
    }

    private static void assertTargetStored() {
        assertThat(new HandoffSignal("agent_b").getTarget()).isEqualTo("agent_b");
    }

    private static void assertMessageDefaultsToNone() {
        assertThat(new HandoffSignal("b").getMessage()).isEmpty();
    }

    private static void assertReasonDefaultsToNone() {
        assertThat(new HandoffSignal("b").getReason()).isEmpty();
    }

    private static void assertCustomMessage() {
        assertThat(new HandoffSignal("b", "context", null).getMessage()).contains("context");
    }

    private static void assertCustomReason() {
        assertThat(new HandoffSignal("b", null, "needs billing").getReason()).contains("needs billing");
    }

    private static void assertFrozenPreventsTargetMutation() throws NoSuchFieldException {
        assertThat(Modifier.isFinal(HandoffSignal.class.getModifiers())).isTrue();
        for (String fieldName : List.of("target", "message", "reason")) {
            Field field = HandoffSignal.class.getDeclaredField(fieldName);
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        }
        assertThat(Arrays.stream(HandoffSignal.class.getMethods()).map(Method::getName).toList())
                .doesNotContain("setTarget", "setMessage", "setReason");
    }

    private static void assertEqualityBasedOnValues() {
        HandoffSignal first = new HandoffSignal("b", "m", "r");
        HandoffSignal second = new HandoffSignal("b", "m", "r");
        assertThat(first).isEqualTo(second);
    }

    private static void assertInequalityDifferentTarget() {
        assertThat(new HandoffSignal("a")).isNotEqualTo(new HandoffSignal("b"));
    }

    private static void assertDirectDictWithTarget() {
        HandoffSignal signal = extract(map(HandoffSignal.HANDOFF_TARGET_KEY, "b"));
        assertThat(signal.getTarget()).isEqualTo("b");
    }

    private static void assertDirectDictWithReason() {
        HandoffSignal signal = extract(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "b",
                HandoffSignal.HANDOFF_REASON_KEY, "needs billing"
        ));
        assertThat(signal.getReason()).contains("needs billing");
    }

    private static void assertDirectDictWithMessage() {
        HandoffSignal signal = extract(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "b",
                HandoffSignal.HANDOFF_MESSAGE_KEY, "carry this"
        ));
        assertThat(signal.getMessage()).contains("carry this");
    }

    private static void assertNestedUnderOutputKey() {
        HandoffSignal signal = extract(map(
                "output",
                map(HandoffSignal.HANDOFF_TARGET_KEY, "c", HandoffSignal.HANDOFF_MESSAGE_KEY, "ctx")
        ));
        assertThat(signal.getTarget()).isEqualTo("c");
        assertThat(signal.getMessage()).contains("ctx");
    }

    private static void assertNestedUnderResultKey() {
        assertThat(extract(map("result", map(HandoffSignal.HANDOFF_TARGET_KEY, "d"))).getTarget())
                .isEqualTo("d");
    }

    private static void assertNestedUnderContentKey() {
        assertThat(extract(map("content", map(HandoffSignal.HANDOFF_TARGET_KEY, "e"))).getTarget())
                .isEqualTo("e");
    }

    private static void assertNoHandoffKeyReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(map("result_type", "answer"))).isEmpty();
    }

    private static void assertEmptyDictReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(map())).isEmpty();
    }

    private static void assertNoneInputReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(null)).isEmpty();
    }

    private static void assertStringInputReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal("plain string")).isEmpty();
    }

    private static void assertListInputReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(List.of(map(HandoffSignal.HANDOFF_TARGET_KEY, "b")))).isEmpty();
    }

    private static void assertIntInputReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(42)).isEmpty();
    }

    private static void assertEmptyTargetStringReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, ""))).isEmpty();
    }

    private static void assertNonStringTargetIntReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, 123))).isEmpty();
    }

    private static void assertNonStringTargetNoneReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, null))).isEmpty();
    }

    private static void assertNonStringTargetListReturnsNone() {
        assertThat(HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, List.of("agent"))))
                .isEmpty();
    }

    private static void assertMessageNoneWhenKeyAbsent() {
        assertThat(extract(map(HandoffSignal.HANDOFF_TARGET_KEY, "b")).getMessage()).isEmpty();
    }

    private static void assertReasonNoneWhenKeyAbsent() {
        assertThat(extract(map(HandoffSignal.HANDOFF_TARGET_KEY, "b")).getReason()).isEmpty();
    }

    private static void assertMessageNoneWhenEmptyString() {
        HandoffSignal signal = extract(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "b",
                HandoffSignal.HANDOFF_MESSAGE_KEY, ""
        ));
        assertThat(signal.getMessage()).isEmpty();
    }

    private static void assertReasonNoneWhenEmptyString() {
        HandoffSignal signal = extract(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "b",
                HandoffSignal.HANDOFF_REASON_KEY, ""
        ));
        assertThat(signal.getReason()).isEmpty();
    }

    private static void assertAllFieldsPopulated() {
        HandoffSignal signal = extract(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "agent_x",
                HandoffSignal.HANDOFF_MESSAGE_KEY, "context data",
                HandoffSignal.HANDOFF_REASON_KEY, "specialist needed"
        ));
        assertThat(signal.getTarget()).isEqualTo("agent_x");
        assertThat(signal.getMessage()).contains("context data");
        assertThat(signal.getReason()).contains("specialist needed");
    }

    private static void assertDirectKeyTakesPriorityOverNested() {
        HandoffSignal signal = extract(map(
                HandoffSignal.HANDOFF_TARGET_KEY, "direct_agent",
                "output", map(HandoffSignal.HANDOFF_TARGET_KEY, "nested_agent")
        ));
        assertThat(signal.getTarget()).isEqualTo("direct_agent");
    }

    private static void assertNestedOutputNonDictIgnored() {
        assertThat(HandoffSignal.extractHandoffSignal(map("output", "not a dict"))).isEmpty();
    }

    private static void assertNestedResultNonDictIgnored() {
        assertThat(HandoffSignal.extractHandoffSignal(map("result", 42))).isEmpty();
    }

    private static void assertNestedContentNonDictIgnored() {
        assertThat(HandoffSignal.extractHandoffSignal(map("content", List.of("list")))).isEmpty();
    }

    private static void assertConstantTargetKeyValue() {
        assertThat(HandoffSignal.HANDOFF_TARGET_KEY).isEqualTo("__handoff_to__");
    }

    private static void assertConstantMessageKeyValue() {
        assertThat(HandoffSignal.HANDOFF_MESSAGE_KEY).isEqualTo("__handoff_message__");
    }

    private static void assertConstantReasonKeyValue() {
        assertThat(HandoffSignal.HANDOFF_REASON_KEY).isEqualTo("__handoff_reason__");
    }

    private static void assertFindsJsonHandoffFromToolMessage() {
        FakeSession session = sessionWithMessages(List.of(
                message("user", "I need help"),
                message("tool", "{\"__handoff_to__\": \"billing_agent\", "
                        + "\"__handoff_reason__\": \"billing question\"}")
        ));
        Map<String, Object> result = HandoffSignal.findHandoffFromSession(session).orElseThrow();
        assertThat(result.get(HandoffSignal.HANDOFF_TARGET_KEY)).isEqualTo("billing_agent");
        assertThat(result.get(HandoffSignal.HANDOFF_REASON_KEY)).isEqualTo("billing question");
    }

    private static void assertFindsHandoffFromPythonDictRepr() {
        FakeSession session = sessionWithMessages(List.of(
                message("tool", "{'__handoff_to__': 'tech_agent', '__handoff_message__': 'escalate'}")
        ));
        Map<String, Object> result = HandoffSignal.findHandoffFromSession(session).orElseThrow();
        assertThat(result.get(HandoffSignal.HANDOFF_TARGET_KEY)).isEqualTo("tech_agent");
        assertThat(result.get(HandoffSignal.HANDOFF_MESSAGE_KEY)).isEqualTo("escalate");
    }

    private static void assertReversedSearchReturnsLastHandoff() {
        FakeSession session = sessionWithMessages(List.of(
                message("tool", "{\"__handoff_to__\": \"first_agent\"}"),
                message("tool", "{\"__handoff_to__\": \"second_agent\"}")
        ));
        assertThat(HandoffSignal.findHandoffFromSession(session).orElseThrow().get(HandoffSignal.HANDOFF_TARGET_KEY))
                .isEqualTo("second_agent");
    }

    private static void assertNonToolMessagesIgnored() {
        FakeSession session = sessionWithMessages(List.of(
                message("assistant", "{\"__handoff_to__\": \"billing_agent\"}")
        ));
        assertThat(HandoffSignal.findHandoffFromSession(session)).isEmpty();
    }

    private static void assertSessionNoHandoffKeyReturnsNone() {
        FakeSession session = sessionWithMessages(List.of(message("tool", "{\"result_type\": \"answer\"}")));
        assertThat(HandoffSignal.findHandoffFromSession(session)).isEmpty();
    }

    private static void assertUnparseableContentReturnsNone() {
        FakeSession session = sessionWithMessages(List.of(message("tool", "not valid json or python")));
        assertThat(HandoffSignal.findHandoffFromSession(session)).isEmpty();
    }

    private static void assertAgentSessionNoneReturnsNone() {
        assertThat(HandoffSignal.findHandoffFromSession(null)).isEmpty();
    }

    private static void assertContextStateMissingReturnsNone() {
        assertThat(HandoffSignal.findHandoffFromSession(new FakeSession(null))).isEmpty();
    }

    private static void assertContextStateNotDictReturnsNone() {
        assertThat(HandoffSignal.findHandoffFromSession(new FakeSession("not a dict"))).isEmpty();
    }

    private static void assertNoMessagesReturnsNone() {
        FakeSession session = new FakeSession(map(DEFAULT_CONTEXT_ID, map("messages", List.of(), "offload_messages", map())));
        assertThat(HandoffSignal.findHandoffFromSession(session)).isEmpty();
    }

    private static void assertDefaultContextKeyMissingReturnsNone() {
        assertThat(HandoffSignal.findHandoffFromSession(new FakeSession(map()))).isEmpty();
    }

    private static void assertEmptyToolContentIgnored() {
        FakeSession session = sessionWithMessages(List.of(message("tool", "")));
        assertThat(HandoffSignal.findHandoffFromSession(session)).isEmpty();
    }

    private static void assertResultWithoutHandoffRecoveredFromSession() {
        FakeSession session = sessionWithMessages(List.of(message("tool", "{\"__handoff_to__\": \"recovered_agent\"}")));
        HandoffSignal signal = HandoffSignal.extractHandoffSignal(map("output", "plain answer"), session).orElseThrow();
        assertThat(signal.getTarget()).isEqualTo("recovered_agent");
    }

    private static void assertResultHandoffTakesPriorityOverSession() {
        FakeSession session = sessionWithMessages(List.of(message("tool", "{\"__handoff_to__\": \"session_agent\"}")));
        HandoffSignal signal = HandoffSignal.extractHandoffSignal(
                map(HandoffSignal.HANDOFF_TARGET_KEY, "result_agent"),
                session
        ).orElseThrow();
        assertThat(signal.getTarget()).isEqualTo("result_agent");
    }

    private static void assertNoHandoffInResultOrSessionReturnsNone() {
        FakeSession session = sessionWithMessages(List.of(message("tool", "{\"result_type\": \"answer\"}")));
        assertThat(HandoffSignal.extractHandoffSignal(map("output", "hello"), session)).isEmpty();
    }

    private static void assertNoneAgentSessionFallsBackToResultOnly() {
        HandoffSignal signal = HandoffSignal.extractHandoffSignal(map(HandoffSignal.HANDOFF_TARGET_KEY, "direct_agent"))
                .orElseThrow();
        assertThat(signal.getTarget()).isEqualTo("direct_agent");
    }

    private static void assertSessionRecoverySuppliesOptionalFields() {
        FakeSession session = sessionWithMessages(List.of(
                message("tool", "{\"__handoff_to__\": \"specialist\", "
                        + "\"__handoff_message__\": \"urgent\", "
                        + "\"__handoff_reason__\": \"complex issue\"}")
        ));
        HandoffSignal signal = HandoffSignal.extractHandoffSignal(map("output", "ignored"), session).orElseThrow();
        assertThat(signal.getTarget()).isEqualTo("specialist");
        assertThat(signal.getMessage()).contains("urgent");
        assertThat(signal.getReason()).contains("complex issue");
    }

    private static HandoffSignal extract(Object result) {
        return HandoffSignal.extractHandoffSignal(result).orElseThrow();
    }

    private static Map<String, Object> contextWithMessages(List<?> messages) {
        return map(DEFAULT_CONTEXT_ID, map(
                "messages", messages,
                "offload_messages", map()
        ));
    }

    private static FakeSession sessionWithMessages(List<?> messages) {
        return new FakeSession(contextWithMessages(messages));
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }

    private static FakeMsg message(String role, String content) {
        return new FakeMsg(role, content);
    }

    /**
     * Mirrors Python's {@code _FakeMsg} test fixture in
     * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_signal.py}.
     */
    private static final class FakeMsg {
        private final String role;
        private final String content;

        private FakeMsg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * Mirrors Python's {@code _FakeSession} test fixture in
     * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_signal.py}.
     */
    static final class FakeSession {
        private final Object contextState;

        private FakeSession(Object contextState) {
            this.contextState = contextState;
        }

        public Object getState(String key) {
            return "context".equals(key) ? contextState : null;
        }
    }
}
