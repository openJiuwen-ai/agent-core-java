/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LLM call spans via {@link ObservabilityRail}.
 *
 * <p>Translates the following Python tests from
 * {@code test_observability.py} into JUnit 5:</p>
 * <ul>
 *   <li>{@code test_streaming_llm_call_records_ttft_and_reasoning} —
 *       LLM call records model, prompt, completion, usage tokens,
 *       and finish_reason.</li>
 *   <li>{@code test_llm_response_with_content_and_tool_calls} —
 *       LLM response with both content and tool_calls is recorded.</li>
 *   <li>{@code test_llm_call_error_marks_span_error} —
 *       {@code onModelException} closes the open span with ERROR status.</li>
 * </ul>
 *
 * <p>Span tree exercised:</p>
 * <pre>
 * team.{name}                        [startTeamTrace]
 * └── agent.{member}.invoke          [beforeInvoke / afterInvoke]
 *     └── llm.call                   [beforeModelCall / afterModelCall]
 * </pre>
 *
 * @since 0.1.7
 */
@DisplayName("LLM Span tests via ObservabilityRail")
class LlmSpanTest extends ObservabilityTestBase {

    // ================================================================
    // LLM call records model, prompt, completion, usage, finish_reason
    // Mirrors: test_streaming_llm_call_records_ttft_and_reasoning
    // ================================================================

    @Test
    @DisplayName("llm.call span records model, prompt, completion, usage tokens")
    void test_llm_call_records_model_prompt_completion_usage() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "Compute 6 * 7.");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // LLM call input with messages.
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a friendly helper."));
        messages.add(Map.of("role", "user", "content", "Compute 6 * 7."));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // LLM response with usage metadata.
        UsageMetadata usage = new UsageMetadata();
        usage.setInputTokens(12);
        usage.setOutputTokens(7);
        usage.setTotalTokens(19);
        usage.setModelName("fake-llm-1");

        AssistantMessage response = AssistantMessage.builder()
                .role("assistant")
                .content("42")
                .usageMetadata(usage)
                .finishReason("stop")
                .build();

        ctx.setInputs(modelCallInputs(messages, response));
        rail.afterModelCall(ctx);

        // Close agent span.
        ctx.setInputs(invokeInputsWithResult("Compute 6 * 7.", simpleResult("42")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // --- Verify llm.call span ---
        List<SpanData> llmSpans = spansByName("llm.call");
        assertThat(llmSpans).as("llm.call span should exist").isNotEmpty();
        SpanData llmSpan = llmSpans.get(0);

        // gen_ai.system = openjiuwen.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_SYSTEM))
                .isEqualTo("openjiuwen");

        // gen_ai.operation.name = chat.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_OPERATION_NAME))
                .isEqualTo("chat");

        // gen_ai.request.model — falls back to "LLM" when config has no model name.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_REQUEST_MODEL))
                .isEqualTo("LLM");

        // gen_ai.usage.prompt_tokens = 12.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_PROMPT_TOKENS))
                .isEqualTo("12");

        // gen_ai.usage.completion_tokens = 7.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_COMPLETION_TOKENS))
                .isEqualTo("7");

        // gen_ai.usage.total_tokens = 19.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_TOTAL_TOKENS))
                .isEqualTo("19");

        // gen_ai.response.finish_reason = stop.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_RESPONSE_FINISH_REASON))
                .isEqualTo("stop");

        // gen_ai.response.model = fake-llm-1 (from usage metadata).
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_RESPONSE_MODEL))
                .isEqualTo("fake-llm-1");
    }

    // ================================================================
    // LLM call records per-message prompt attributes
    // ================================================================

    @Test
    @DisplayName("llm.call span records per-message prompt role and content")
    void test_llm_call_records_per_message_prompt_attrs() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helper."));
        messages.add(Map.of("role", "user", "content", "What is 2+2?"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Close with a simple response.
        ctx.setInputs(modelCallInputs(messages, simpleResult("4")));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("4")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // gen_ai.prompt.0.role and gen_ai.prompt.0.content.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_PROMPT + ".0.role"))
                .isEqualTo("system");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_PROMPT + ".0.content"))
                .isEqualTo("You are a helper.");

        // gen_ai.prompt.1.role and gen_ai.prompt.1.content.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_PROMPT + ".1.role"))
                .isEqualTo("user");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_PROMPT + ".1.content"))
                .isEqualTo("What is 2+2?");

        // gen_ai.request.message_count = 2.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_REQUEST_MESSAGE_COUNT))
                .isEqualTo("2");
    }

    // ================================================================
    // LLM response with both content and tool_calls
    // Mirrors: test_llm_response_with_content_and_tool_calls
    // ================================================================

    @Test
    @DisplayName("llm.call records both content and tool_calls in response")
    void test_llm_response_with_content_and_tool_calls() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "What is the weather?");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "What is the weather?"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Build response with both content and tool_calls.
        ToolCall toolCall = ToolCall.builder()
                .id("call_123")
                .type("function")
                .name("get_weather")
                .arguments("{\"location\": \"Beijing\"}")
                .build();

        AssistantMessage response = AssistantMessage.builder()
                .role("assistant")
                .content("Let me check the weather for you.")
                .toolCalls(List.of(toolCall))
                .finishReason("tool_calls")
                .build();

        ctx.setInputs(modelCallInputs(messages, response));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("What is the weather?", simpleResult("checking")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // Completion should contain the content.
        String completion = attr(llmSpan, ObservabilitySemConv.GEN_AI_COMPLETION);
        assertThat(completion).as("completion should be recorded").isNotNull();
        assertThat(completion).contains("Let me check the weather for you.");

        // Tool calls should appear in the serialized completion.
        assertThat(completion).contains("get_weather");
        assertThat(completion).contains("Beijing");

        // finish_reason = tool_calls.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_RESPONSE_FINISH_REASON))
                .isEqualTo("tool_calls");
    }

    // ================================================================
    // LLM call error marks span ERROR
    // Mirrors: test_llm_call_error_marks_span_error
    // ================================================================

    @Test
    @DisplayName("onModelException closes span with ERROR status and exception")
    void test_llm_call_error_marks_span_error() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "fail please");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Open LLM span.
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "fail please"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Simulate LLM error.
        ctx.setException(new RuntimeException("provider down"));
        rail.onModelException(ctx);

        // Close agent span (exception still set).
        ctx.setInputs(invokeInputsWithResult("fail please", null));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        List<SpanData> llmSpans = spansByName("llm.call");
        assertThat(llmSpans).as("llm.call span should exist after error").isNotEmpty();
        SpanData llmSpan = llmSpans.get(0);

        // Status should be ERROR.
        assertThat(llmSpan.getStatus().getStatusCode())
                .as("llm span should have ERROR status after onModelException")
                .isEqualTo(StatusCode.ERROR);

        // Exception event should be recorded.
        assertThat(llmSpan.getEvents())
                .as("exception event should be recorded on llm span")
                .isNotEmpty();
    }

    // ================================================================
    // LLM call with Map-based usage metadata
    // ================================================================

    @Test
    @DisplayName("llm.call extracts tokens from Map-based usage_metadata")
    void test_llm_call_extracts_tokens_from_map_usage() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Response as a Map with usage_metadata as a nested Map.
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("input_tokens", 100);
        usageMap.put("output_tokens", 50);
        usageMap.put("total_tokens", 150);
        usageMap.put("model_name", "gpt-test");

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("content", "hi there");
        responseMap.put("usage_metadata", usageMap);
        responseMap.put("finish_reason", "stop");

        ctx.setInputs(modelCallInputs(messages, responseMap));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("hi there")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // Tokens extracted from Map-based usage.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_PROMPT_TOKENS))
                .isEqualTo("100");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_COMPLETION_TOKENS))
                .isEqualTo("50");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_TOTAL_TOKENS))
                .isEqualTo("150");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_RESPONSE_MODEL))
                .isEqualTo("gpt-test");
    }

    // ================================================================
    // LLM call computes total when only input+output present
    // ================================================================

    @Test
    @DisplayName("llm.call computes total tokens when only input and output present")
    void test_llm_call_computes_total_tokens() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // UsageMetadata with total_tokens=0 (should compute from input+output).
        UsageMetadata usage = new UsageMetadata();
        usage.setInputTokens(30);
        usage.setOutputTokens(20);
        usage.setTotalTokens(0);
        usage.setModelName("compute-model");

        AssistantMessage response = AssistantMessage.builder()
                .role("assistant")
                .content("response")
                .usageMetadata(usage)
                .finishReason("stop")
                .build();

        ctx.setInputs(modelCallInputs(messages, response));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("response")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // Total should be computed as 30 + 20 = 50.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_PROMPT_TOKENS))
                .isEqualTo("30");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_COMPLETION_TOKENS))
                .isEqualTo("20");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_TOTAL_TOKENS))
                .isEqualTo("50");
    }

    // ================================================================
    // LLM span is child of agent span
    // ================================================================

    @Test
    @DisplayName("llm.call span parent is agent span")
    void test_llm_span_parent_is_agent_span() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        ctx.setInputs(modelCallInputs(messages, simpleResult("hi")));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("hi")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData agentSpan = spansByPrefix("agent.leader.invoke").get(0);
        SpanData llmSpan = spansByName("llm.call").get(0);

        assertThat(llmSpan.getParentSpanId())
                .as("llm span parent should be agent span")
                .isEqualTo(agentSpan.getSpanId());
    }

    // ================================================================
    // LLM call with no response still closes span with OK
    // ================================================================

    @Test
    @DisplayName("llm.call with null response closes span with OK status")
    void test_llm_call_null_response_ok_status() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Close with null response.
        ctx.setInputs(modelCallInputs(messages, null));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", null));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);
        assertThat(llmSpan.getStatus().getStatusCode())
                .as("llm span should have OK status even with null response")
                .isEqualTo(StatusCode.OK);
    }

    // ================================================================
    // LLM call with redaction enabled produces hashed prompt
    // ================================================================

    @Test
    @DisplayName("llm.call with redaction enabled produces hashed prompt content")
    void test_llm_call_redaction_produces_hashed_prompt() {
        // Override the config from the base class to enable redaction.
        ObservabilityConfig redactedConfig = ObservabilityConfig.builder()
                .isEnabled(true)
                .serviceName("openjiuwen-test")
                .sampleRate(1.0)
                .shouldRedactPrompts(true)
                .shouldRedactCompletions(true)
                .build();
        try {
            java.lang.reflect.Field configField =
                    ObservabilitySetup.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(null, redactedConfig);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to inject redacted config", e);
        }

        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "secret prompt");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "secret prompt"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        ctx.setInputs(modelCallInputs(messages, simpleResult("secret answer")));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("secret prompt", simpleResult("secret answer")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // Per-message content should be hashed.
        String promptContent = attr(llmSpan,
                ObservabilitySemConv.GEN_AI_PROMPT + ".0.content");
        assertThat(promptContent)
                .as("prompt content should be hashed when redaction is enabled")
                .startsWith("sha256:");
        assertThat(promptContent).doesNotContain("secret");

        // Completion should also be hashed.
        String completion = attr(llmSpan, ObservabilitySemConv.GEN_AI_COMPLETION);
        assertThat(completion)
                .as("completion should be hashed when redaction is enabled")
                .startsWith("sha256:");
        assertThat(completion).doesNotContain("secret");
    }

    // ================================================================
    // LLM call with BaseMessage objects (not Maps)
    // ================================================================

    @Test
    @DisplayName("llm.call serializes BaseMessage objects correctly without hashcode")
    void test_llm_call_serializes_base_message_objects() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        // Use BaseMessage subclasses instead of Maps.
        com.openjiuwen.core.foundation.llm.schema.UserMessage userMsg =
                new com.openjiuwen.core.foundation.llm.schema.UserMessage("hello world");
        List<Object> messages = new ArrayList<>();
        messages.add(userMsg);
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        ctx.setInputs(modelCallInputs(messages, simpleResult("response")));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("response")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // Per-message content should be the actual string, not a hashcode.
        String promptContent = attr(llmSpan,
                ObservabilitySemConv.GEN_AI_PROMPT + ".0.content");
        assertThat(promptContent)
                .as("prompt content should be the actual message content")
                .isEqualTo("hello world");

        // The prompt attribute should be valid JSON, not contain @hashcode.
        String prompt = attr(llmSpan, ObservabilitySemConv.GEN_AI_PROMPT);
        assertThat(prompt)
                .as("prompt should be valid JSON without hashcode")
                .doesNotContain("@");
    }

    // ================================================================
    // afterModelCall skips when exception is set
    // ================================================================

    @Test
    @DisplayName("afterModelCall skips when exception is set (handled by onModelException)")
    void test_after_model_call_skips_when_exception_set() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "test");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "test"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Exception path: onModelException ends the span with ERROR.
        ctx.setException(new IllegalStateException("model error"));
        rail.onModelException(ctx);

        // Now call afterModelCall — it should skip because exception is set.
        rail.afterModelCall(ctx);

        // Close agent span.
        ctx.setInputs(invokeInputsWithResult("test", null));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        // Exactly 1 llm.call span (not 2 — no double creation).
        List<SpanData> llmSpans = spansByName("llm.call");
        assertThat(llmSpans)
                .as("should have exactly 1 llm.call span (no double-end)")
                .hasSize(1);

        // Status is ERROR (set by onModelException).
        SpanData llmSpan = llmSpans.get(0);
        assertThat(llmSpan.getStatus().getStatusCode())
                .as("llm span status should remain ERROR (not overwritten to OK)")
                .isEqualTo(StatusCode.ERROR);
    }

    // ================================================================
    // LLM span has CLIENT kind
    // ================================================================

    @Test
    @DisplayName("llm.call span has CLIENT span kind")
    void test_llm_span_has_client_kind() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        ctx.setInputs(modelCallInputs(messages, simpleResult("hi")));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("hi")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);
        assertThat(llmSpan.getKind())
                .as("llm.call span should have CLIENT kind")
                .isEqualTo(io.opentelemetry.api.trace.SpanKind.CLIENT);
    }

    // ================================================================
    // LLM call with OpenAI-compatible usage field names
    // ================================================================

    @Test
    @DisplayName("llm.call extracts tokens from OpenAI-compatible usage field names")
    void test_llm_call_openai_compatible_usage_fields() {
        ObservabilitySetup.startTeamTrace("test_team", "test_session");

        StubAgent agent = stubAgent("test_team", "leader");
        AgentCallbackContext ctx = invokeContext(agent, "hello");
        ObservabilityRail rail = new ObservabilityRail();
        rail.beforeInvoke(ctx);

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));
        ctx.setInputs(modelCallInputs(messages, null));
        rail.beforeModelCall(ctx);

        // Response with OpenAI-style usage field (prompt_tokens/completion_tokens).
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("prompt_tokens", 200);
        usageMap.put("completion_tokens", 100);
        // No total_tokens — should be computed.

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("content", "response");
        responseMap.put("usage", usageMap);
        responseMap.put("finish_reason", "stop");
        responseMap.put("model", "gpt-4o");

        ctx.setInputs(modelCallInputs(messages, responseMap));
        rail.afterModelCall(ctx);

        ctx.setInputs(invokeInputsWithResult("hello", simpleResult("response")));
        rail.afterInvoke(ctx);
        ObservabilitySetup.finalizeTeamTrace("test_team");

        SpanData llmSpan = spansByName("llm.call").get(0);

        // Tokens extracted from OpenAI-style usage.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_PROMPT_TOKENS))
                .isEqualTo("200");
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_COMPLETION_TOKENS))
                .isEqualTo("100");
        // Total computed as 200 + 100 = 300.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_USAGE_TOTAL_TOKENS))
                .isEqualTo("300");
        // Model from response "model" field.
        assertThat(attr(llmSpan, ObservabilitySemConv.GEN_AI_RESPONSE_MODEL))
                .isEqualTo("gpt-4o");
    }
}
