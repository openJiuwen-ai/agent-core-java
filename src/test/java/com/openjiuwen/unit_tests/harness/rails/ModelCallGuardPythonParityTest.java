/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.AgentCallbackEvent;
import com.openjiuwen.core.single_agent.rail.ForceFinishRequest;
import com.openjiuwen.core.single_agent.rail.ModelCallInputs;
import com.openjiuwen.core.single_agent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.security.SecurityAlert;
import com.openjiuwen.harness.rails.security.SecurityAlertLevel;
import com.openjiuwen.harness.rails.security.SecurityAllow;
import com.openjiuwen.harness.rails.security.SecurityDecision;
import com.openjiuwen.harness.rails.security.SecurityInterrupt;
import com.openjiuwen.harness.rails.security.SecurityReject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Supplemental parity tests for security rail demo behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.rails.test_model_call_guard} in
 * {@code tests/unit_tests/harness/rails/test_model_call_guard.py}.</p>
 */
class ModelCallGuardPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/rails/test_model_call_guard.py";
    private static final String SECRET = "sk-abc123def456ghi789jkl";
    private static final List<Pattern> API_KEY_PATTERNS = List.of(
            Pattern.compile("(?:api_key|API_KEY|apikey|APIKEY|secret|SECRET|token|TOKEN|credential|CREDENTIAL)"
                    + "\\s*[=:]\\s*[\\\"']?\\S+[\\\"']?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sk-[a-zA-Z0-9_-]{20,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Bearer\\s+[a-zA-Z0-9\\-_]+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("AKIA[0-9A-Z]{16}", Pattern.CASE_INSENSITIVE)
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonModelCallGuardTests(String pythonNodeId) {
        runPythonNode(pythonNodeId);
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardInputPop::test_before_pops_user_message_with_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardInputPop::test_before_pops_all_messages_with_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardInputPop::test_before_skips_if_no_context"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardInputPop::test_before_allows_clean_user_message"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardInputPop::test_before_pops_tool_message_with_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardInputPop::test_before_keeps_clean_messages_when_tool_result_has_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_pops_history_on_response_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_pops_all_matching_messages"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_skips_if_response_none"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_allows_clean_response"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_reject_secret_in_tool_arguments"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_response_has_secret_then_context_cleaned"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestModelCallGuardOutputPop::test_after_response_has_secret_and_history_has_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestConversationContinues::test_second_turn_succeeds_after_first_rejected"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestConversationContinues::test_full_flow_tool_result_secret_then_second_turn"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestBothEvents::test_both_events_registered"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSingleEventOnly::test_only_before_model_call"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSingleEventOnly::test_only_after_model_call"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSingleEventOnly::test_only_before_allows_clean_input"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSingleEventOnly::test_only_after_allows_clean_output"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSanitizeHelper::test_sanitize_replaces_secret_in_user_message"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSanitizeHelper::test_sanitize_preserves_other_content"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSanitizeHelper::test_sanitize_response_content"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestInterruptResumeHelper::test_auto_confirmed_returns_allow"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestInterruptResumeHelper::test_no_user_input_returns_none"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestInterruptResumeHelper::test_user_approved_returns_allow"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestInterruptResumeHelper::test_user_rejected_returns_reject"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestInterruptResumeHelper::test_user_approved_with_auto_confirm_stores_it"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolInterruptReject::test_after_reject_triggers_force_finish"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolInterruptReject::test_before_reject_skips_tool_no_force_finish"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolInterruptReject::test_before_interrupt_on_args_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolInterruptReject::test_after_interrupt_on_result_secret"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolInterruptReject::test_before_approve_executes_tool"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolInterruptReject::test_both_events_registered"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardInterruptAutoConfirm::test_before_auto_confirm_key_format"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardInterruptAutoConfirm::test_after_auto_confirm_key_format"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestUnderscoreKeyFormats::test_model_before_pops_underscore_key"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestUnderscoreKeyFormats::test_model_after_rejects_underscore_key_in_response"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestUnderscoreKeyFormats::test_tool_before_interrupt_underscore_key_in_args"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestUnderscoreKeyFormats::test_tool_after_interrupt_underscore_key_in_result"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestUnderscoreKeyFormats::test_sanitize_underscore_key"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolRejectExample::test_before_reject_skips_tool_continues"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolRejectExample::test_after_reject_force_finish"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolRejectExample::test_before_allows_clean_args"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolRejectExample::test_after_allows_clean_result"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolRejectExample::test_non_whitelist_tool_allowed"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestToolRejectExample::test_both_events_registered"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_security_alert_level_enum_values"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_security_alert_dataclass_fields"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_security_alert_default_values"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_security_alert_display_modes"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_apply_alert_streams_to_session"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_apply_alert_with_history_mode"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_apply_alert_with_inline_mode"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestSecurityAlert::test_apply_alert_without_session"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_alert_on_api_key_in_result"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_allow_clean_result"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_allow_non_whitelist_tool"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_custom_display_mode"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_custom_alert_level"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_underscore_key_detected"),
                arg("tests/unit_tests/harness/rails/test_model_call_guard.py::TestApiKeyGuardAlert::test_both_events_registered")
        );
    }

    private static Arguments arg(String pythonNodeId) {
        return Arguments.of(pythonNodeId);
    }

    private static void runPythonNode(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::TestModelCallGuardInputPop::test_before_pops_user_message_with_secret" -> beforePopsUserMessageWithSecret();
            case SOURCE + "::TestModelCallGuardInputPop::test_before_pops_all_messages_with_secret" -> beforePopsAllMessagesWithSecret();
            case SOURCE + "::TestModelCallGuardInputPop::test_before_skips_if_no_context" -> beforeSkipsIfNoContext();
            case SOURCE + "::TestModelCallGuardInputPop::test_before_allows_clean_user_message" -> beforeAllowsCleanUserMessage();
            case SOURCE + "::TestModelCallGuardInputPop::test_before_pops_tool_message_with_secret" -> beforePopsToolMessageWithSecret();
            case SOURCE + "::TestModelCallGuardInputPop::test_before_keeps_clean_messages_when_tool_result_has_secret" -> beforeKeepsCleanMessagesWhenToolResultHasSecret();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_pops_history_on_response_secret" -> afterPopsHistoryOnResponseSecret();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_pops_all_matching_messages" -> afterPopsAllMatchingMessages();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_skips_if_response_none" -> afterSkipsIfResponseNone();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_allows_clean_response" -> afterAllowsCleanResponse();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_reject_secret_in_tool_arguments" -> afterRejectSecretInToolArguments();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_response_has_secret_then_context_cleaned" -> afterResponseHasSecretThenContextCleaned();
            case SOURCE + "::TestModelCallGuardOutputPop::test_after_response_has_secret_and_history_has_secret" -> afterResponseHasSecretAndHistoryHasSecret();
            case SOURCE + "::TestConversationContinues::test_second_turn_succeeds_after_first_rejected" -> secondTurnSucceedsAfterFirstRejected();
            case SOURCE + "::TestConversationContinues::test_full_flow_tool_result_secret_then_second_turn" -> fullFlowToolResultSecretThenSecondTurn();
            case SOURCE + "::TestBothEvents::test_both_events_registered" -> modelGuardBothEventsRegistered();
            case SOURCE + "::TestSingleEventOnly::test_only_before_model_call" -> onlyBeforeModelCall();
            case SOURCE + "::TestSingleEventOnly::test_only_after_model_call" -> onlyAfterModelCall();
            case SOURCE + "::TestSingleEventOnly::test_only_before_allows_clean_input" -> onlyBeforeAllowsCleanInput();
            case SOURCE + "::TestSingleEventOnly::test_only_after_allows_clean_output" -> onlyAfterAllowsCleanOutput();
            case SOURCE + "::TestSanitizeHelper::test_sanitize_replaces_secret_in_user_message" -> sanitizeReplacesSecretInUserMessage();
            case SOURCE + "::TestSanitizeHelper::test_sanitize_preserves_other_content" -> sanitizePreservesOtherContent();
            case SOURCE + "::TestSanitizeHelper::test_sanitize_response_content" -> sanitizeResponseContent();
            case SOURCE + "::TestInterruptResumeHelper::test_auto_confirmed_returns_allow" -> autoConfirmedReturnsAllow();
            case SOURCE + "::TestInterruptResumeHelper::test_no_user_input_returns_none" -> noUserInputReturnsNone();
            case SOURCE + "::TestInterruptResumeHelper::test_user_approved_returns_allow" -> userApprovedReturnsAllow();
            case SOURCE + "::TestInterruptResumeHelper::test_user_rejected_returns_reject" -> userRejectedReturnsReject();
            case SOURCE + "::TestInterruptResumeHelper::test_user_approved_with_auto_confirm_stores_it" -> userApprovedWithAutoConfirmStoresIt();
            case SOURCE + "::TestToolInterruptReject::test_after_reject_triggers_force_finish" -> afterRejectTriggersForceFinish();
            case SOURCE + "::TestToolInterruptReject::test_before_reject_skips_tool_no_force_finish" -> beforeRejectSkipsToolNoForceFinish();
            case SOURCE + "::TestToolInterruptReject::test_before_interrupt_on_args_secret" -> beforeInterruptOnArgsSecret();
            case SOURCE + "::TestToolInterruptReject::test_after_interrupt_on_result_secret" -> afterInterruptOnResultSecret();
            case SOURCE + "::TestToolInterruptReject::test_before_approve_executes_tool" -> beforeApproveExecutesTool();
            case SOURCE + "::TestToolInterruptReject::test_both_events_registered" -> interruptBothEventsRegistered();
            case SOURCE + "::TestApiKeyGuardInterruptAutoConfirm::test_before_auto_confirm_key_format" -> beforeAutoConfirmKeyFormat();
            case SOURCE + "::TestApiKeyGuardInterruptAutoConfirm::test_after_auto_confirm_key_format" -> afterAutoConfirmKeyFormat();
            case SOURCE + "::TestUnderscoreKeyFormats::test_model_before_pops_underscore_key" -> modelBeforePopsUnderscoreKey();
            case SOURCE + "::TestUnderscoreKeyFormats::test_model_after_rejects_underscore_key_in_response" -> modelAfterRejectsUnderscoreKeyInResponse();
            case SOURCE + "::TestUnderscoreKeyFormats::test_tool_before_interrupt_underscore_key_in_args" -> toolBeforeInterruptUnderscoreKeyInArgs();
            case SOURCE + "::TestUnderscoreKeyFormats::test_tool_after_interrupt_underscore_key_in_result" -> toolAfterInterruptUnderscoreKeyInResult();
            case SOURCE + "::TestUnderscoreKeyFormats::test_sanitize_underscore_key" -> sanitizeUnderscoreKey();
            case SOURCE + "::TestToolRejectExample::test_before_reject_skips_tool_continues" -> toolRejectBeforeRejectSkipsToolContinues();
            case SOURCE + "::TestToolRejectExample::test_after_reject_force_finish" -> toolRejectAfterRejectForceFinish();
            case SOURCE + "::TestToolRejectExample::test_before_allows_clean_args" -> toolRejectBeforeAllowsCleanArgs();
            case SOURCE + "::TestToolRejectExample::test_after_allows_clean_result" -> toolRejectAfterAllowsCleanResult();
            case SOURCE + "::TestToolRejectExample::test_non_whitelist_tool_allowed" -> toolRejectNonWhitelistToolAllowed();
            case SOURCE + "::TestToolRejectExample::test_both_events_registered" -> toolRejectBothEventsRegistered();
            case SOURCE + "::TestSecurityAlert::test_security_alert_level_enum_values" -> securityAlertLevelEnumValues();
            case SOURCE + "::TestSecurityAlert::test_security_alert_dataclass_fields" -> securityAlertDataclassFields();
            case SOURCE + "::TestSecurityAlert::test_security_alert_default_values" -> securityAlertDefaultValues();
            case SOURCE + "::TestSecurityAlert::test_security_alert_display_modes" -> securityAlertDisplayModes();
            case SOURCE + "::TestSecurityAlert::test_apply_alert_streams_to_session" -> applyAlertStreamsToSession();
            case SOURCE + "::TestSecurityAlert::test_apply_alert_with_history_mode" -> applyAlertWithHistoryMode();
            case SOURCE + "::TestSecurityAlert::test_apply_alert_with_inline_mode" -> applyAlertWithInlineMode();
            case SOURCE + "::TestSecurityAlert::test_apply_alert_without_session" -> applyAlertWithoutSession();
            case SOURCE + "::TestApiKeyGuardAlert::test_alert_on_api_key_in_result" -> apiKeyGuardAlertOnApiKeyInResult();
            case SOURCE + "::TestApiKeyGuardAlert::test_allow_clean_result" -> apiKeyGuardAllowCleanResult();
            case SOURCE + "::TestApiKeyGuardAlert::test_allow_non_whitelist_tool" -> apiKeyGuardAllowNonWhitelistTool();
            case SOURCE + "::TestApiKeyGuardAlert::test_custom_display_mode" -> apiKeyGuardCustomDisplayMode();
            case SOURCE + "::TestApiKeyGuardAlert::test_custom_alert_level" -> apiKeyGuardCustomAlertLevel();
            case SOURCE + "::TestApiKeyGuardAlert::test_underscore_key_detected" -> apiKeyGuardUnderscoreKeyDetected();
            case SOURCE + "::TestApiKeyGuardAlert::test_both_events_registered" -> apiKeyGuardBothEventsRegistered();
            default -> throw new IllegalArgumentException("Unknown Python test node: " + nodeId);
        }
    }

    private static void beforePopsUserMessageWithSecret() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(user("My API key is sk-1234567890abcdefghijklmnop")));
        AgentCallbackContext ctx = modelCtx(context, null);

        rail.beforeModelCall(ctx);

        assertThat(context.messages).isEmpty();
        assertThat(context.popped).hasSize(1);
        assertThat(content(context.popped.get(0))).contains("sk-1234567890abcdefghijklmnop");
        assertThat(finishOutput(ctx)).contains("API key");
    }

    private static void beforePopsAllMessagesWithSecret() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(
                user("Hello"),
                assistant("Hi there!"),
                user("My secret token=secret123"),
                assistant("Also token=secret123 here")));

        rail.beforeModelCall(modelCtx(context, null));

        assertThat(context.messages).extracting(ModelCallGuardPythonParityTest::content)
                .containsExactly("Hello", "Hi there!");
        assertThat(context.popped).hasSize(2);
    }

    private static void beforeSkipsIfNoContext() {
        ModelcallguardRail rail = new ModelcallguardRail();
        AgentCallbackContext ctx = modelCtx(null, null, List.of(user("My API key is sk-1234567890abcdefghijklmnop")));

        rail.beforeModelCall(ctx);

        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void beforeAllowsCleanUserMessage() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(user("What is the weather today?")));
        AgentCallbackContext ctx = modelCtx(context, null);

        rail.beforeModelCall(ctx);

        assertThat(context.messages).hasSize(1);
        assertThat(context.popped).isEmpty();
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void beforePopsToolMessageWithSecret() {
        ModelcallguardRail rail = new ModelcallguardRail();
        String secret = "sk-1234567890abcdefghijklmnop";
        MockModelContext context = new MockModelContext(List.of(
                user("read the config file"),
                tool("API_KEY=" + secret + "\nSECRET=xyz", "call_001")));
        AgentCallbackContext ctx = modelCtx(context, null);

        rail.beforeModelCall(ctx);

        assertThat(context.messages).singleElement().extracting(ModelCallGuardPythonParityTest::content)
                .isEqualTo("read the config file");
        assertThat(context.popped).singleElement().extracting(ModelCallGuardPythonParityTest::content)
                .asString().contains(secret);
        assertThat(finishOutput(ctx)).contains("conversation history");
    }

    private static void beforeKeepsCleanMessagesWhenToolResultHasSecret() {
        ModelcallguardRail rail = new ModelcallguardRail();
        String secret = "sk-abc123def456ghi789jkl012";
        MockModelContext context = new MockModelContext(List.of(
                user("Hello"),
                assistant("Hi!"),
                user("read config"),
                tool("API_KEY=" + secret, "call_001")));

        rail.beforeModelCall(modelCtx(context, null));

        assertThat(context.messages).extracting(ModelCallGuardPythonParityTest::content)
                .containsExactly("Hello", "Hi!", "read config");
        assertThat(context.popped).singleElement().extracting(ModelCallGuardPythonParityTest::content)
                .asString().contains(secret);
    }

    private static void afterPopsHistoryOnResponseSecret() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(
                user("Hello"),
                user("My key is sk-abc123def456ghi789jkl"),
                assistant("I can help.")));
        AgentCallbackContext ctx = modelCtx(context,
                assistant("I see you mentioned sk-abc123def456ghi789jkl earlier."));

        rail.afterModelCall(ctx);

        assertThat(context.popped).extracting(ModelCallGuardPythonParityTest::content)
                .anyMatch(value -> value.contains("sk-abc123def456ghi789jkl"));
        assertThat(finishOutput(ctx)).contains("model response");
    }

    private static void afterPopsAllMatchingMessages() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(
                user("First message"),
                user("sk-test12345678901234567 here"),
                assistant("sk-test12345678901234567 too"),
                user("Last clean message")));

        rail.afterModelCall(modelCtx(context, assistant("Response with sk-test12345678901234567")));

        assertThat(context.popped).extracting(ModelCallGuardPythonParityTest::content)
                .anyMatch(value -> value.contains("sk-test12345678901234567"));
    }

    private static void afterSkipsIfResponseNone() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(user("Hello")));
        AgentCallbackContext ctx = modelCtx(context, null);

        rail.afterModelCall(ctx);

        assertThat(context.messages).hasSize(1);
        assertThat(context.popped).isEmpty();
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void afterAllowsCleanResponse() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(user("What is weather?")));
        AgentCallbackContext ctx = modelCtx(context, assistant("The weather is sunny."));

        rail.afterModelCall(ctx);

        assertThat(context.messages).hasSize(1);
        assertThat(context.popped).isEmpty();
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void afterRejectSecretInToolArguments() {
        ModelcallguardRail rail = new ModelcallguardRail();
        AssistantMessage response = assistant("");
        response.setToolCalls(List.of(ToolCall.builder()
                .id("call_123")
                .type("function")
                .name("send_email")
                .arguments("{\"body\": \"My API_KEY=sk-secret12345678901234\"}")
                .build()));
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of(user("Send email"))), response);

        rail.afterModelCall(ctx);

        assertThat(finishOutput(ctx)).contains("tool arguments");
    }

    private static void afterResponseHasSecretThenContextCleaned() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(
                user("What is my API key?"),
                assistant("Your API key is shown below."),
                user("Show me again")));
        AgentCallbackContext ctx = modelCtx(context, assistant("Here is your key: " + SECRET));

        rail.afterModelCall(ctx);

        assertThat(finishOutput(ctx)).contains("model response");
        assertThat(context.messages).hasSize(3);
    }

    private static void afterResponseHasSecretAndHistoryHasSecret() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context = new MockModelContext(List.of(
                user("My key is " + SECRET),
                assistant("I see."),
                user("Show me again")));
        AgentCallbackContext ctx = modelCtx(context, assistant("Here is your key: " + SECRET));

        rail.afterModelCall(ctx);

        assertThat(finishOutput(ctx)).contains("model response");
        assertThat(context.messages).extracting(ModelCallGuardPythonParityTest::content)
                .containsExactly("I see.", "Show me again");
    }

    private static void secondTurnSucceedsAfterFirstRejected() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext context1 = new MockModelContext(List.of(user("My API key is sk-1234567890abcdefghijklmnop")));
        AgentCallbackContext ctx1 = modelCtx(context1, null);
        rail.beforeModelCall(ctx1);
        assertThat(ctx1.consumeForceFinish()).isNotNull();
        assertThat(context1.messages).isEmpty();

        MockModelContext context2 = new MockModelContext(List.of(user("What is the weather today?")));
        AgentCallbackContext ctx2 = modelCtx(context2, null);
        rail.beforeModelCall(ctx2);
        assertThat(ctx2.consumeForceFinish()).isNull();
        assertThat(context2.messages).hasSize(1);
    }

    private static void fullFlowToolResultSecretThenSecondTurn() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockModelContext turn1 = new MockModelContext(List.of(user("read the config file")));
        AgentCallbackContext first = modelCtx(turn1, null);
        rail.beforeModelCall(first);
        assertThat(first.consumeForceFinish()).isNull();
        turn1.messages.add(tool("API_KEY=" + SECRET, "call_001"));

        AgentCallbackContext second = modelCtx(turn1, null);
        rail.beforeModelCall(second);

        assertThat(finishOutput(second)).contains("conversation history");
        assertThat(turn1.messages).singleElement().extracting(ModelCallGuardPythonParityTest::content)
                .isEqualTo("read the config file");

        MockModelContext turn2 = new MockModelContext(List.of(user("hi")));
        AgentCallbackContext third = modelCtx(turn2, null);
        rail.beforeModelCall(third);
        assertThat(third.consumeForceFinish()).isNull();
        assertThat(turn2.messages).hasSize(1);
    }

    private static void modelGuardBothEventsRegistered() {
        assertThat(new ModelcallguardRail().getCallbacks())
                .containsExactlyInAnyOrder(AgentCallbackEvent.BEFORE_MODEL_CALL, AgentCallbackEvent.AFTER_MODEL_CALL);
    }

    private static void onlyBeforeModelCall() {
        ModelcallguardRail rail = new ModelcallguardRail();
        rail.setSupportedEvents(Set.of(AgentCallbackEvent.BEFORE_MODEL_CALL));
        MockModelContext context = new MockModelContext(List.of(user("My key is " + SECRET)));
        AgentCallbackContext ctx = modelCtx(context, null);

        rail.beforeModelCall(ctx);

        assertThat(ctx.consumeForceFinish()).isNotNull();
        assertThat(context.messages).isEmpty();
    }

    private static void onlyAfterModelCall() {
        ModelcallguardRail rail = new ModelcallguardRail();
        rail.setSupportedEvents(Set.of(AgentCallbackEvent.AFTER_MODEL_CALL));
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of(user("Hello"))), assistant("Key is " + SECRET));

        rail.afterModelCall(ctx);

        assertThat(finishOutput(ctx)).contains("model response");
    }

    private static void onlyBeforeAllowsCleanInput() {
        ModelcallguardRail rail = new ModelcallguardRail();
        rail.setSupportedEvents(Set.of(AgentCallbackEvent.BEFORE_MODEL_CALL));
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of(user("Clean input"))), null);

        rail.beforeModelCall(ctx);

        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void onlyAfterAllowsCleanOutput() {
        ModelcallguardRail rail = new ModelcallguardRail();
        rail.setSupportedEvents(Set.of(AgentCallbackEvent.AFTER_MODEL_CALL));
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of(user("Hello"))), assistant("Clean output"));

        rail.afterModelCall(ctx);

        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void sanitizeReplacesSecretInUserMessage() {
        SensitivedatasanitizeRail rail = new SensitivedatasanitizeRail();
        MockModelContext context = new MockModelContext(List.of(user("My key is " + SECRET)));
        AgentCallbackContext ctx = modelCtx(context, null);

        rail.beforeModelCall(ctx);

        assertThat(content(context.messages.get(0))).contains("[REDACTED]").doesNotContain(SECRET);
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void sanitizePreservesOtherContent() {
        SensitivedatasanitizeRail rail = new SensitivedatasanitizeRail();
        MockModelContext context = new MockModelContext(List.of(user("Hello, my API_KEY=" + SECRET + ", goodbye")));

        rail.beforeModelCall(modelCtx(context, null));

        assertThat(content(context.messages.get(0))).contains("Hello,", "goodbye", "[REDACTED]");
    }

    private static void sanitizeResponseContent() {
        SensitivedatasanitizeRail rail = new SensitivedatasanitizeRail();
        AssistantMessage response = assistant("The key is " + SECRET);
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of(user("Hello"))), response);

        rail.afterModelCall(ctx);

        assertThat(content(response)).contains("[REDACTED]").doesNotContain(SECRET);
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void autoConfirmedReturnsAllow() {
        LocalSecurityContext securityCtx = localSecurityContext(
                modelCtx(new MockModelContext(List.of()), null),
                AgentCallbackEvent.AFTER_TOOL_CALL,
                null,
                Map.of("test_key", true));

        assertThat(new ModelcallguardRail().handleInterruptResume(securityCtx, "test_key"))
                .isInstanceOf(SecurityAllow.class);
    }

    private static void noUserInputReturnsNone() {
        LocalSecurityContext securityCtx = localSecurityContext(
                modelCtx(new MockModelContext(List.of()), null),
                AgentCallbackEvent.AFTER_TOOL_CALL,
                null,
                Map.of());

        assertThat(new ModelcallguardRail().handleInterruptResume(securityCtx, "test_key")).isNull();
    }

    private static void userApprovedReturnsAllow() {
        LocalSecurityContext securityCtx = localSecurityContext(
                modelCtx(new MockModelContext(List.of()), null),
                AgentCallbackEvent.AFTER_TOOL_CALL,
                Map.of("approved", true),
                Map.of());

        assertThat(new ModelcallguardRail().handleInterruptResume(securityCtx, "test_key"))
                .isInstanceOf(SecurityAllow.class);
    }

    private static void userRejectedReturnsReject() {
        LocalSecurityContext securityCtx = localSecurityContext(
                modelCtx(new MockModelContext(List.of()), null),
                AgentCallbackEvent.AFTER_TOOL_CALL,
                Map.of("approved", false),
                Map.of());

        assertThat(new ModelcallguardRail().handleInterruptResume(securityCtx, "test_key"))
                .isInstanceOf(SecurityReject.class);
    }

    private static void userApprovedWithAutoConfirmStoresIt() {
        MockSession session = new MockSession();
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of()), null);
        ctx.setSession(session);
        LocalSecurityContext securityCtx = localSecurityContext(
                ctx,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                Map.of("approved", true, "auto_confirm", true),
                Map.of());

        SecurityDecision decision = new ModelcallguardRail().handleInterruptResume(securityCtx, "test_key");

        assertThat(decision).isInstanceOf(SecurityAllow.class);
        assertThat(session.state.get("interrupt_auto_confirm")).isEqualTo(Map.of("test_key", true));
    }

    private static void afterRejectTriggersForceFinish() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        AgentCallbackContext ctx = toolCtx("read_file", null, "API_KEY=" + SECRET);
        LocalSecurityContext securityCtx = localSecurityContext(
                ctx, AgentCallbackEvent.AFTER_TOOL_CALL, Map.of("approved", false), Map.of());

        rail.applySecurityDecision(securityCtx, rail.runSecurityCheck(securityCtx));

        assertThat(finishOutput(ctx)).contains("Rejected");
    }

    private static void beforeRejectSkipsToolNoForceFinish() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        AgentCallbackContext ctx = toolCtx("read_file", "{\"path\": \"file_with_" + SECRET + "\"}", null);
        LocalSecurityContext securityCtx = localSecurityContext(
                ctx, AgentCallbackEvent.BEFORE_TOOL_CALL, Map.of("approved", false), Map.of());

        rail.applySecurityDecision(securityCtx, rail.runSecurityCheck(securityCtx));

        assertThat(ctx.consumeForceFinish()).isNull();
        assertThat(ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(content((BaseMessage) ((ToolCallInputs) ctx.getInputs()).getToolMsg()))
                .contains("Tool execution skipped");
    }

    private static void beforeInterruptOnArgsSecret() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        LocalSecurityContext securityCtx = localSecurityContext(
                toolCtx("read_file", "{\"path\": \"file_with_" + SECRET + "\"}", null),
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                null,
                Map.of());

        SecurityDecision decision = rail.runSecurityCheck(securityCtx);

        assertThat(decision).isInstanceOf(SecurityInterrupt.class);
        assertThat(((SecurityInterrupt) decision).request().get("message").toString().toLowerCase())
                .contains("tool arguments");
    }

    private static void afterInterruptOnResultSecret() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        LocalSecurityContext securityCtx = localSecurityContext(
                toolCtx("read_file", null, "API_KEY=" + SECRET),
                AgentCallbackEvent.AFTER_TOOL_CALL,
                null,
                Map.of());

        SecurityDecision decision = rail.runSecurityCheck(securityCtx);

        assertThat(decision).isInstanceOf(SecurityInterrupt.class);
        assertThat(((SecurityInterrupt) decision).request().get("message").toString().toLowerCase())
                .contains("tool result");
    }

    private static void beforeApproveExecutesTool() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        AgentCallbackContext ctx = toolCtx("read_file", "{\"path\": \"file_with_" + SECRET + "\"}", null);
        LocalSecurityContext securityCtx = localSecurityContext(
                ctx, AgentCallbackEvent.BEFORE_TOOL_CALL, Map.of("approved", true), Map.of());

        rail.applySecurityDecision(securityCtx, rail.runSecurityCheck(securityCtx));

        assertThat(ctx.consumeForceFinish()).isNull();
        assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
    }

    private static void interruptBothEventsRegistered() {
        assertThat(new ApikeyguardinterruptRail().getCallbacks())
                .containsExactlyInAnyOrder(AgentCallbackEvent.BEFORE_TOOL_CALL, AgentCallbackEvent.AFTER_TOOL_CALL);
    }

    private static void beforeAutoConfirmKeyFormat() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        LocalSecurityContext securityCtx = localSecurityContext(
                toolCtx("read_file", "{\"path\": \"secret_file\"}", null),
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                null,
                Map.of("api_key_guard:read_file:before", true));

        assertThat(rail.runSecurityCheck(securityCtx)).isInstanceOf(SecurityAllow.class);
    }

    private static void afterAutoConfirmKeyFormat() {
        ApikeyguardinterruptRail rail = new ApikeyguardinterruptRail();
        LocalSecurityContext securityCtx = localSecurityContext(
                toolCtx("read_file", null, "API_KEY=sk-secret123"),
                AgentCallbackEvent.AFTER_TOOL_CALL,
                null,
                Map.of("api_key_guard:read_file:after", true));

        assertThat(rail.runSecurityCheck(securityCtx)).isInstanceOf(SecurityAllow.class);
    }

    private static void modelBeforePopsUnderscoreKey() {
        String secret = "sk-A7nhsRpv_vcK55IGQe6hSQ";
        MockModelContext context = new MockModelContext(List.of(user("My key is " + secret)));
        AgentCallbackContext ctx = modelCtx(context, null);

        new ModelcallguardRail().beforeModelCall(ctx);

        assertThat(context.messages).isEmpty();
        assertThat(content(context.popped.get(0))).contains(secret);
        assertThat(ctx.consumeForceFinish()).isNotNull();
    }

    private static void modelAfterRejectsUnderscoreKeyInResponse() {
        String secret = "sk-proj_abc123def456ghi789";
        AgentCallbackContext ctx = modelCtx(new MockModelContext(List.of(user("What is my key?"))),
                assistant("Your key is " + secret));

        new ModelcallguardRail().afterModelCall(ctx);

        assertThat(finishOutput(ctx)).contains("model response");
    }

    private static void toolBeforeInterruptUnderscoreKeyInArgs() {
        String secret = "sk-A7nhsRpv_vcK55IGQe6hSQ";
        SecurityDecision decision = new ApikeyguardinterruptRail().runSecurityCheck(localSecurityContext(
                toolCtx("glob", "{\"pattern\": \"*" + secret + "*\"}", null),
                AgentCallbackEvent.BEFORE_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityInterrupt.class);
    }

    private static void toolAfterInterruptUnderscoreKeyInResult() {
        String secret = "sk-A7nhsRpv_vcK55IGQe6hSQ";
        SecurityDecision decision = new ApikeyguardinterruptRail().runSecurityCheck(localSecurityContext(
                toolCtx("read_file", null, "Found key: " + secret),
                AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityInterrupt.class);
    }

    private static void sanitizeUnderscoreKey() {
        String secret = "sk-proj_abc123def456ghi789";
        MockModelContext context = new MockModelContext(List.of(user("My key is " + secret)));
        AgentCallbackContext ctx = modelCtx(context, null);

        new SensitivedatasanitizeRail().beforeModelCall(ctx);

        assertThat(content(context.messages.get(0))).contains("[REDACTED]").doesNotContain(secret);
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void toolRejectBeforeRejectSkipsToolContinues() {
        ToolrejectexampleRail rail = new ToolrejectexampleRail();
        AgentCallbackContext ctx = toolCtx("glob", "{\"pattern\": \"*sk-A7nhsRpv_vcK55IGQe6hSQ*\"}", null);
        LocalSecurityContext securityCtx = localSecurityContext(ctx, AgentCallbackEvent.BEFORE_TOOL_CALL, null, Map.of());

        rail.applySecurityDecision(securityCtx, rail.runSecurityCheck(securityCtx));

        assertThat(ctx.consumeForceFinish()).isNull();
        assertThat(ctx.getExtra()).containsEntry("_skip_tool", true);
        assertThat(content((BaseMessage) ((ToolCallInputs) ctx.getInputs()).getToolMsg())).contains("Tool skipped");
    }

    private static void toolRejectAfterRejectForceFinish() {
        ToolrejectexampleRail rail = new ToolrejectexampleRail();
        AgentCallbackContext ctx = toolCtx("read_file", null, "API_KEY=sk-A7nhsRpv_vcK55IGQe6hSQ");
        LocalSecurityContext securityCtx = localSecurityContext(ctx, AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of());

        rail.applySecurityDecision(securityCtx, rail.runSecurityCheck(securityCtx));

        assertThat(finishOutput(ctx)).contains("Agent terminated");
    }

    private static void toolRejectBeforeAllowsCleanArgs() {
        ToolrejectexampleRail rail = new ToolrejectexampleRail();
        AgentCallbackContext ctx = toolCtx("glob", "{\"pattern\": \"*.txt\"}", null);
        LocalSecurityContext securityCtx = localSecurityContext(ctx, AgentCallbackEvent.BEFORE_TOOL_CALL, null, Map.of());

        SecurityDecision decision = rail.runSecurityCheck(securityCtx);
        rail.applySecurityDecision(securityCtx, decision);

        assertThat(decision).isInstanceOf(SecurityAllow.class);
        assertThat(ctx.consumeForceFinish()).isNull();
        assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
    }

    private static void toolRejectAfterAllowsCleanResult() {
        ToolrejectexampleRail rail = new ToolrejectexampleRail();
        AgentCallbackContext ctx = toolCtx("read_file", null, "File content without secrets");
        LocalSecurityContext securityCtx = localSecurityContext(ctx, AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of());

        SecurityDecision decision = rail.runSecurityCheck(securityCtx);
        rail.applySecurityDecision(securityCtx, decision);

        assertThat(decision).isInstanceOf(SecurityAllow.class);
        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void toolRejectNonWhitelistToolAllowed() {
        SecurityDecision decision = new ToolrejectexampleRail().runSecurityCheck(localSecurityContext(
                toolCtx("calculate", "{\"data\": \"" + SECRET + "\"}", null),
                AgentCallbackEvent.BEFORE_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAllow.class);
    }

    private static void toolRejectBothEventsRegistered() {
        assertThat(new ToolrejectexampleRail().getCallbacks())
                .containsExactlyInAnyOrder(AgentCallbackEvent.BEFORE_TOOL_CALL, AgentCallbackEvent.AFTER_TOOL_CALL);
    }

    private static void securityAlertLevelEnumValues() {
        assertThat(SecurityAlertLevel.INFO.value()).isEqualTo("info");
        assertThat(SecurityAlertLevel.WARNING.value()).isEqualTo("warning");
        assertThat(SecurityAlertLevel.ERROR.value()).isEqualTo("error");
        assertThat(SecurityAlertLevel.CRITICAL.value()).isEqualTo("critical");
    }

    private static void securityAlertDataclassFields() {
        SecurityAlert alert = new ModelcallguardRail().alert("Test alert",
                SecurityAlertLevel.WARNING, "test", "popup");

        assertThat(alert.message()).isEqualTo("Test alert");
        assertThat(alert.level()).isEqualTo(SecurityAlertLevel.WARNING);
        assertThat(alert.alertType()).isEqualTo("test");
        assertThat(alert.displayMode()).isEqualTo("popup");
    }

    private static void securityAlertDefaultValues() {
        SecurityAlert alert = new ModelcallguardRail().alert("Default test");

        assertThat(alert.message()).isEqualTo("Default test");
        assertThat(alert.level()).isEqualTo(SecurityAlertLevel.WARNING);
        assertThat(alert.alertType()).isEqualTo("security");
        assertThat(alert.displayMode()).isEqualTo("popup");
    }

    private static void securityAlertDisplayModes() {
        ModelcallguardRail rail = new ModelcallguardRail();

        assertThat(rail.alert("popup", SecurityAlertLevel.WARNING, "security", "popup").displayMode())
                .isEqualTo("popup");
        assertThat(rail.alert("history", SecurityAlertLevel.WARNING, "security", "history").displayMode())
                .isEqualTo("history");
        assertThat(rail.alert("inline", SecurityAlertLevel.WARNING, "security", "inline").displayMode())
                .isEqualTo("inline");
    }

    private static void applyAlertStreamsToSession() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockSession session = new MockSession();
        AgentCallbackContext ctx = toolCtx("read", null, "test");
        ctx.setSession(session);

        rail.applyAlert(localSecurityContext(ctx, AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()),
                rail.alert("Stream test", SecurityAlertLevel.ERROR, "security", "popup"));

        OutputSchema streamData = (OutputSchema) session.streams.get(0);
        assertThat(streamData.getType()).isEqualTo("message");
        Map<?, ?> payload = (Map<?, ?>) streamData.getPayload();
        assertThat(payload.get("role")).isEqualTo("system");
        assertThat(payload.get("content")).asString().contains("[ERROR]", "Stream test");
        Map<?, ?> metadata = (Map<?, ?>) payload.get("metadata");
        assertThat(metadata.get("is_security_alert")).isEqualTo(true);
        assertThat(metadata.get("level")).isEqualTo("error");
        assertThat(metadata.get("display_mode")).isEqualTo("popup");
    }

    private static void applyAlertWithHistoryMode() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockSession session = new MockSession();
        AgentCallbackContext ctx = toolCtx("read", null, "test");
        ctx.setSession(session);

        rail.applyAlert(localSecurityContext(ctx, AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()),
                rail.alert("History test", SecurityAlertLevel.WARNING, "security", "history"));

        Map<?, ?> metadata = (Map<?, ?>) ((Map<?, ?>) ((OutputSchema) session.streams.get(0)).getPayload())
                .get("metadata");
        assertThat(metadata.get("display_mode")).isEqualTo("history");
    }

    private static void applyAlertWithInlineMode() {
        ModelcallguardRail rail = new ModelcallguardRail();
        MockSession session = new MockSession();
        AgentCallbackContext ctx = toolCtx("read", null, "test");
        ctx.setSession(session);

        rail.applyAlert(localSecurityContext(ctx, AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()),
                rail.alert("Inline test", SecurityAlertLevel.WARNING, "security", "inline"));

        Map<?, ?> metadata = (Map<?, ?>) ((Map<?, ?>) ((OutputSchema) session.streams.get(0)).getPayload())
                .get("metadata");
        assertThat(metadata.get("display_mode")).isEqualTo("inline");
    }

    private static void applyAlertWithoutSession() {
        ModelcallguardRail rail = new ModelcallguardRail();
        AgentCallbackContext ctx = toolCtx("read", null, "test");

        rail.applyAlert(localSecurityContext(ctx, AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()),
                rail.alert("No session test"));

        assertThat(ctx.consumeForceFinish()).isNull();
    }

    private static void apiKeyGuardAlertOnApiKeyInResult() {
        SecurityDecision decision = new ApikeyguardalertRail("popup", SecurityAlertLevel.WARNING)
                .runSecurityCheck(localSecurityContext(toolCtx("read_file", null, "API_KEY=sk-abc123def456ghi789jkl012mno"),
                        AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAlert.class);
        assertThat(((SecurityAlert) decision).message()).contains("API key");
        assertThat(((SecurityAlert) decision).displayMode()).isEqualTo("popup");
    }

    private static void apiKeyGuardAllowCleanResult() {
        SecurityDecision decision = new ApikeyguardalertRail()
                .runSecurityCheck(localSecurityContext(toolCtx("read_file", null, "Normal file content"),
                        AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAllow.class);
    }

    private static void apiKeyGuardAllowNonWhitelistTool() {
        SecurityDecision decision = new ApikeyguardalertRail()
                .runSecurityCheck(localSecurityContext(toolCtx("write_file", null, "API_KEY=sk-abc123"),
                        AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAllow.class);
    }

    private static void apiKeyGuardCustomDisplayMode() {
        SecurityDecision decision = new ApikeyguardalertRail("history", SecurityAlertLevel.WARNING)
                .runSecurityCheck(localSecurityContext(toolCtx("bash", null, "Found: " + SECRET),
                        AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAlert.class);
        assertThat(((SecurityAlert) decision).displayMode()).isEqualTo("history");
    }

    private static void apiKeyGuardCustomAlertLevel() {
        SecurityDecision decision = new ApikeyguardalertRail("popup", SecurityAlertLevel.CRITICAL)
                .runSecurityCheck(localSecurityContext(toolCtx("read", null, "Key: sk-abc123def456ghi789jkl012"),
                        AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAlert.class);
        assertThat(((SecurityAlert) decision).level()).isEqualTo(SecurityAlertLevel.CRITICAL);
    }

    private static void apiKeyGuardUnderscoreKeyDetected() {
        SecurityDecision decision = new ApikeyguardalertRail()
                .runSecurityCheck(localSecurityContext(toolCtx("grep", null, "Found key: sk-A7nhsRpv_vcK55IGQe6hSQ"),
                        AgentCallbackEvent.AFTER_TOOL_CALL, null, Map.of()));

        assertThat(decision).isInstanceOf(SecurityAlert.class);
    }

    private static void apiKeyGuardBothEventsRegistered() {
        assertThat(new ApikeyguardalertRail().getCallbacks()).containsExactly(AgentCallbackEvent.AFTER_TOOL_CALL);
    }

    private static AgentCallbackContext modelCtx(MockModelContext context, Object response) {
        List<BaseMessage> messages = context == null ? List.of() : context.getMessages(null, true);
        return modelCtx(context, response, messages);
    }

    private static AgentCallbackContext modelCtx(MockModelContext context, Object response, List<BaseMessage> messages) {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setMessages(new ArrayList<>(messages));
        inputs.setResponse(response);
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setInputs(inputs);
        ctx.setContext(context);
        return ctx;
    }

    private static AgentCallbackContext toolCtx(String toolName, Object toolArgs, Object toolResult) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolName(toolName);
        inputs.setToolArgs(toolArgs);
        inputs.setToolResult(toolResult);
        inputs.setToolCall(ToolCall.builder().id("call_001").type("function").name(toolName).arguments("{}").build());
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setInputs(inputs);
        return ctx;
    }

    private static LocalSecurityContext localSecurityContext(
            AgentCallbackContext ctx,
            AgentCallbackEvent event,
            Object userInput,
            Map<String, Object> autoConfirmConfig
    ) {
        return new LocalSecurityContext(ctx, event, userInput, autoConfirmConfig, "call_001");
    }

    private static UserMessage user(String content) {
        return new UserMessage(content);
    }

    private static AssistantMessage assistant(String content) {
        return new AssistantMessage(content);
    }

    private static ToolMessage tool(String content, String toolCallId) {
        return new ToolMessage(content, toolCallId);
    }

    private static String content(BaseMessage message) {
        return message.getContentAsString();
    }

    private static String finishOutput(AgentCallbackContext ctx) {
        ForceFinishRequest finish = ctx.consumeForceFinish();
        assertThat(finish).isNotNull();
        return String.valueOf(finish.getResult().get("output"));
    }

    private static boolean containsSecret(String text) {
        return API_KEY_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    private static String sanitize(String text, String replacement) {
        String value = text;
        for (Pattern pattern : API_KEY_PATTERNS) {
            value = pattern.matcher(value).replaceAll(replacement);
        }
        return value;
    }

    /**
     * Test-local context matching the Python {@code SecurityCheckContext} shape.
     *
     * <p>Mirrors Python's {@code SecurityCheckContext} in
     * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
     */
    private record LocalSecurityContext(
            AgentCallbackContext callbackCtx,
            AgentCallbackEvent event,
            Object userInput,
            Map<String, Object> autoConfirmConfig,
            String subjectId
    ) {
    }

    /**
     * Python-like base security rail used by the example rails in this parity test.
     *
     * <p>Mirrors Python's {@code BaseSecurityRail} in
     * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
     */
    private abstract static class LocalBaseSecurityRail {
        private final Set<AgentCallbackEvent> supportedEvents = new LinkedHashSet<>();

        protected SecurityAllow allow() {
            return new SecurityAllow(null);
        }

        protected SecurityReject reject(String message) {
            return new SecurityReject(message);
        }

        protected SecurityInterrupt interrupt(String message, String subjectId) {
            return new SecurityInterrupt(Map.of("message", message), subjectId);
        }

        protected SecurityAlert alert(String message) {
            return alert(message, SecurityAlertLevel.WARNING, "security", "popup");
        }

        protected SecurityAlert alert(String message, SecurityAlertLevel level, String alertType, String displayMode) {
            return new SecurityAlert(message, level, alertType, displayMode);
        }

        protected void setSupportedEvents(Set<AgentCallbackEvent> events) {
            supportedEvents.clear();
            supportedEvents.addAll(events);
        }

        protected Set<AgentCallbackEvent> getCallbacks() {
            return new LinkedHashSet<>(supportedEvents);
        }

        protected void beforeModelCall(AgentCallbackContext ctx) {
            runIfSupported(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL);
        }

        protected void afterModelCall(AgentCallbackContext ctx) {
            runIfSupported(ctx, AgentCallbackEvent.AFTER_MODEL_CALL);
        }

        protected void applySecurityDecision(LocalSecurityContext securityCtx, SecurityDecision decision) {
            if (decision == null || decision instanceof SecurityAllow) {
                return;
            }
            if (decision instanceof SecurityAlert alert) {
                applyAlert(securityCtx, alert);
                return;
            }
            if (decision instanceof SecurityReject reject) {
                applyReject(securityCtx, reject);
            }
        }

        protected void applyAlert(LocalSecurityContext securityCtx, SecurityAlert decision) {
            AgentCallbackContext ctx = securityCtx.callbackCtx();
            if (ctx.getSession() == null) {
                return;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("is_security_alert", true);
            metadata.put("level", decision.level().value());
            metadata.put("alert_type", decision.alertType());
            metadata.put("display_mode", decision.displayMode());
            metadata.put("rail", getClass().getSimpleName());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("role", "system");
            payload.put("content", "[" + decision.level().value().toUpperCase() + "] " + decision.message());
            payload.put("metadata", metadata);
            ctx.getSession().writeStream(new OutputSchema("message", 0, payload));
        }

        protected SecurityDecision handleInterruptResume(LocalSecurityContext securityCtx, String autoConfirmKey) {
            if (Boolean.TRUE.equals(securityCtx.autoConfirmConfig().get(autoConfirmKey))) {
                return allow();
            }
            Object userInput = securityCtx.userInput();
            if (userInput == null) {
                return null;
            }
            if (userInput instanceof Map<?, ?> map) {
                boolean approved = Boolean.TRUE.equals(map.get("approved"));
                if (approved) {
                    if (Boolean.TRUE.equals(map.get("auto_confirm"))) {
                        storeAutoConfirm(securityCtx.callbackCtx(), autoConfirmKey);
                    }
                    return allow();
                }
            }
            return reject("");
        }

        protected abstract SecurityDecision runSecurityCheck(LocalSecurityContext securityCtx);

        private void runIfSupported(AgentCallbackContext ctx, AgentCallbackEvent event) {
            if (!supportedEvents.contains(event)) {
                return;
            }
            LocalSecurityContext securityCtx = new LocalSecurityContext(ctx, event, null, Map.of(), "");
            applySecurityDecision(securityCtx, runSecurityCheck(securityCtx));
        }

        private void applyReject(LocalSecurityContext securityCtx, SecurityReject decision) {
            AgentCallbackContext ctx = securityCtx.callbackCtx();
            AgentCallbackEvent event = securityCtx.event();
            String message = decision.message();
            String errorMsg;
            if (event == AgentCallbackEvent.BEFORE_TOOL_CALL && (message == null || message.isBlank())) {
                errorMsg = "Tool execution skipped";
            } else if (message == null || message.isBlank()) {
                errorMsg = "Blocked by security rail";
            } else {
                errorMsg = message;
            }
            if (event == AgentCallbackEvent.BEFORE_MODEL_CALL || event == AgentCallbackEvent.AFTER_MODEL_CALL) {
                ctx.requestForceFinish(forceResult(decision));
                return;
            }
            ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
            ToolCall call = (ToolCall) inputs.getToolCall();
            if (event == AgentCallbackEvent.BEFORE_TOOL_CALL) {
                ctx.getExtra().put("_skip_tool", true);
                inputs.setToolResult(errorMsg);
                inputs.setToolMsg(new ToolMessage(errorMsg, call == null ? "" : call.getId()));
                return;
            }
            if (event == AgentCallbackEvent.AFTER_TOOL_CALL) {
                inputs.setToolResult(errorMsg);
                inputs.setToolMsg(new ToolMessage(errorMsg, call == null ? "" : call.getId()));
                ctx.requestForceFinish(forceResult(decision));
            }
        }

        private static Map<String, Object> forceResult(SecurityReject decision) {
            if (decision.result() instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                map.forEach((key, value) -> copied.put(String.valueOf(key), value));
                return copied;
            }
            String message = decision.message();
            Object output = message == null || message.isBlank()
                    ? (decision.result() == null ? "Rejected by security rail." : decision.result())
                    : message;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("output", output);
            result.put("result_type", "error");
            return result;
        }

        @SuppressWarnings("unchecked")
        private static void storeAutoConfirm(AgentCallbackContext ctx, String autoConfirmKey) {
            if (ctx.getSession() == null) {
                return;
            }
            Object current = ctx.getSession().getState("interrupt_auto_confirm");
            Map<String, Object> config = current instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map)
                    : new LinkedHashMap<>();
            config.put(autoConfirmKey, true);
            ctx.getSession().updateState(Map.of("interrupt_auto_confirm", config));
        }
    }

    /**
     * Test-local ModelCallGuard example rail.
     *
     * <p>Mirrors Python's {@code ModelcallguardRail} in
     * {@code examples/security_rail_demo/ModelCallGuard/rail.py}.</p>
     */
    private static final class ModelcallguardRail extends LocalBaseSecurityRail {
        private ModelcallguardRail() {
            setSupportedEvents(Set.of(AgentCallbackEvent.BEFORE_MODEL_CALL, AgentCallbackEvent.AFTER_MODEL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(LocalSecurityContext securityCtx) {
            if (!(securityCtx.callbackCtx().getInputs() instanceof ModelCallInputs inputs)) {
                return allow();
            }
            if (securityCtx.event() == AgentCallbackEvent.BEFORE_MODEL_CALL) {
                return checkInput(securityCtx.callbackCtx());
            }
            if (securityCtx.event() == AgentCallbackEvent.AFTER_MODEL_CALL) {
                return checkOutput(inputs, securityCtx.callbackCtx());
            }
            return allow();
        }

        private SecurityDecision checkInput(AgentCallbackContext ctx) {
            if (!(ctx.getContext() instanceof MockModelContext context)) {
                return allow();
            }
            for (BaseMessage message : context.getMessages(null, true)) {
                if (containsSecret(content(message))) {
                    popMatchingMessages(context);
                    return reject("API key/secret detected in conversation history. Operation blocked for security.");
                }
            }
            return allow();
        }

        private SecurityDecision checkOutput(ModelCallInputs inputs, AgentCallbackContext ctx) {
            Object response = inputs.getResponse();
            if (!(response instanceof AssistantMessage assistant)) {
                return allow();
            }
            if (containsSecret(content(assistant))) {
                if (ctx.getContext() instanceof MockModelContext context) {
                    popMatchingMessages(context);
                }
                return reject("API key/secret detected in model response. Operation blocked for security.");
            }
            if (assistant.getToolCalls() != null) {
                for (ToolCall call : assistant.getToolCalls()) {
                    if (call.getArguments() != null && containsSecret(call.getArguments())) {
                        if (ctx.getContext() instanceof MockModelContext context) {
                            popMatchingMessages(context);
                        }
                        return reject("API key/secret detected in tool arguments. Operation blocked for security.");
                    }
                }
            }
            return allow();
        }

        private void popMatchingMessages(MockModelContext context) {
            List<BaseMessage> kept = new ArrayList<>();
            List<BaseMessage> popped = new ArrayList<>();
            for (BaseMessage message : context.messages) {
                if (containsSecret(content(message))) {
                    popped.add(message);
                } else {
                    kept.add(message);
                }
            }
            if (!popped.isEmpty()) {
                context.setMessages(kept, true);
            }
        }
    }

    /**
     * Test-local sensitive-data sanitize example rail.
     *
     * <p>Mirrors Python's {@code SensitivedatasanitizeRail} in
     * {@code examples/security_rail_demo/SensitiveDataSanitize/rail.py}.</p>
     */
    private static final class SensitivedatasanitizeRail extends LocalBaseSecurityRail {
        private SensitivedatasanitizeRail() {
            setSupportedEvents(Set.of(AgentCallbackEvent.BEFORE_MODEL_CALL, AgentCallbackEvent.AFTER_MODEL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(LocalSecurityContext securityCtx) {
            AgentCallbackContext ctx = securityCtx.callbackCtx();
            if (!(ctx.getInputs() instanceof ModelCallInputs inputs)) {
                return allow();
            }
            if (ctx.getContext() instanceof MockModelContext context) {
                for (BaseMessage message : context.messages) {
                    String sanitized = sanitize(content(message), "[REDACTED]");
                    if (!sanitized.equals(content(message))) {
                        message.setContent(sanitized);
                    }
                }
            }
            if (securityCtx.event() == AgentCallbackEvent.AFTER_MODEL_CALL
                    && inputs.getResponse() instanceof AssistantMessage response) {
                response.setContent(sanitize(content(response), "[REDACTED]"));
            }
            return allow();
        }
    }

    /**
     * Test-local API-key interrupt example rail.
     *
     * <p>Mirrors Python's {@code ApikeyguardinterruptRail} in
     * {@code examples/security_rail_demo/ApiKeyGuardInterrupt/rail.py}.</p>
     */
    private static final class ApikeyguardinterruptRail extends LocalBaseSecurityRail {
        private static final Set<String> FILE_READING_TOOLS = Set.of("read_file", "bash", "grep", "glob");

        private ApikeyguardinterruptRail() {
            setSupportedEvents(Set.of(AgentCallbackEvent.BEFORE_TOOL_CALL, AgentCallbackEvent.AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(LocalSecurityContext securityCtx) {
            ToolCallInputs inputs = (ToolCallInputs) securityCtx.callbackCtx().getInputs();
            String toolName = inputs.getToolName();
            if (!FILE_READING_TOOLS.contains(toolName)) {
                return allow();
            }
            String phase = securityCtx.event() == AgentCallbackEvent.BEFORE_TOOL_CALL ? "before" : "after";
            SecurityDecision resume = handleInterruptResume(securityCtx, "api_key_guard:" + toolName + ":" + phase);
            if (resume != null) {
                return resume;
            }
            Object value = securityCtx.event() == AgentCallbackEvent.BEFORE_TOOL_CALL
                    ? inputs.getToolArgs()
                    : inputs.getToolResult();
            if (value != null && containsSecret(String.valueOf(value))) {
                String target = securityCtx.event() == AgentCallbackEvent.BEFORE_TOOL_CALL
                        ? "tool arguments"
                        : "tool result";
                return interrupt("API key/secret detected in " + target + ". Approve execution?", securityCtx.subjectId());
            }
            return allow();
        }
    }

    /**
     * Test-local direct reject example rail.
     *
     * <p>Mirrors Python's {@code ToolrejectexampleRail} in
     * {@code examples/security_rail_demo/ToolRejectExample/rail.py}.</p>
     */
    private static final class ToolrejectexampleRail extends LocalBaseSecurityRail {
        private static final Set<String> TOOL_WHITELIST = Set.of("read_file", "bash", "grep", "glob", "write_file");

        private ToolrejectexampleRail() {
            setSupportedEvents(Set.of(AgentCallbackEvent.BEFORE_TOOL_CALL, AgentCallbackEvent.AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(LocalSecurityContext securityCtx) {
            ToolCallInputs inputs = (ToolCallInputs) securityCtx.callbackCtx().getInputs();
            String toolName = inputs.getToolName();
            if (!TOOL_WHITELIST.contains(toolName)) {
                return allow();
            }
            Object value = securityCtx.event() == AgentCallbackEvent.BEFORE_TOOL_CALL
                    ? inputs.getToolArgs()
                    : inputs.getToolResult();
            if (value == null || !containsSecret(String.valueOf(value))) {
                return allow();
            }
            if (securityCtx.event() == AgentCallbackEvent.BEFORE_TOOL_CALL) {
                return reject("Secret detected in " + toolName + " arguments. Tool skipped.");
            }
            return reject("Secret detected in " + toolName + " result. Agent terminated.");
        }
    }

    /**
     * Test-local API-key alert example rail.
     *
     * <p>Mirrors Python's {@code ApikeyguardalertRail} in
     * {@code examples/security_rail_demo/ApiKeyGuardAlert/rail.py}.</p>
     */
    private static final class ApikeyguardalertRail extends LocalBaseSecurityRail {
        private static final Set<String> FILE_READING_TOOLS = Set.of("read_file", "bash", "grep", "glob", "read");
        private final String displayMode;
        private final SecurityAlertLevel alertLevel;

        private ApikeyguardalertRail() {
            this("popup", SecurityAlertLevel.WARNING);
        }

        private ApikeyguardalertRail(String displayMode, SecurityAlertLevel alertLevel) {
            this.displayMode = displayMode;
            this.alertLevel = alertLevel;
            setSupportedEvents(Set.of(AgentCallbackEvent.AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(LocalSecurityContext securityCtx) {
            ToolCallInputs inputs = (ToolCallInputs) securityCtx.callbackCtx().getInputs();
            String toolName = inputs.getToolName();
            if (!FILE_READING_TOOLS.contains(toolName)) {
                return allow();
            }
            Object result = inputs.getToolResult();
            if (result != null && containsSecret(String.valueOf(result))) {
                return alert("API key/secret detected in " + toolName + " result. Execution allowed but flagged.",
                        alertLevel, "api_key_leakage", displayMode);
            }
            return allow();
        }
    }

    /**
     * In-memory model context matching the Python test fixture.
     *
     * <p>Mirrors Python's {@code MockModelContext} in
     * {@code tests/unit_tests/harness/rails/test_model_call_guard.py}.</p>
     */
    private static final class MockModelContext implements ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();
        private final List<BaseMessage> popped = new ArrayList<>();

        private MockModelContext(List<BaseMessage> messages) {
            this.messages.addAll(messages);
        }

        @Override
        public int length() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            if (size == null) {
                return new ArrayList<>(messages);
            }
            int start = Math.max(0, messages.size() - size);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            List<BaseMessage> old = new ArrayList<>(this.messages);
            this.messages.clear();
            this.messages.addAll(messages);
            old.stream().filter(message -> !this.messages.contains(message)).forEach(popped::add);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int count = Math.max(1, size);
            int start = Math.max(0, messages.size() - count);
            List<BaseMessage> removed = new ArrayList<>(messages.subList(start, messages.size()));
            messages.subList(start, messages.size()).clear();
            popped.addAll(removed);
            return removed;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            popped.addAll(messages);
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            messages.add(message);
            return CompletableFuture.completedFuture(getMessages(null, true));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return CompletableFuture.completedFuture(getMessages(null, true));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(
                List<BaseMessage> systemMessages,
                List<ToolInfo> tools,
                Integer windowSize,
                Integer dialogueRound,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public ContextStats statistic() {
            return null;
        }

        @Override
        public String sessionId() {
            return "test-session";
        }

        @Override
        public String contextId() {
            return "test-context";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return messages -> 0;
        }

        @Override
        public ToolPort reloaderTool() {
            return () -> "reload";
        }
    }

    /**
     * In-memory session for alert streaming and auto-confirm state.
     *
     * <p>Mirrors Python's mock session in
     * {@code tests/unit_tests/harness/rails/test_model_call_guard.py}.</p>
     */
    private static final class MockSession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> streams = new ArrayList<>();

        @Override
        public String getSessionId() {
            return "session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            streams.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return streams.iterator();
        }
    }
}
