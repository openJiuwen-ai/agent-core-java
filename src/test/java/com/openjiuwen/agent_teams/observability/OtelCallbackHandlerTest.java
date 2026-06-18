/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtelCallbackHandlerTest {

    @AfterEach
    void tearDown() {
        SpanContext.resetAll();
    }

    @Test
    void streamingLlmCallRecordsTtftUsageCompletionAndReasoningSpan() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are a friendly helper."),
                Map.of("role", "user", "content", "Compute 6 * 7.")
        );

        handler.onLlmStreamInput(Map.of(
                "messages", messages,
                "temperature", 0.5,
                "top_p", 0.9,
                "max_tokens", 512,
                "model", "fake-llm-1"
        ));
        handler.onLlmStreamOutput(Map.of("result", new FakeChunk("4")));
        handler.onLlmStreamOutput(Map.of("result", new FakeChunk("2")));
        handler.onLlmInvokeOutput(Map.of(
                "result",
                new FakeAssistantMessage("42", "Six times seven equals forty-two.", "stop")
        ));

        TelemetrySpan llmSpan = findSpan(tracer, "llm.call");
        assertThat(llmSpan.getAttributes())
                .containsEntry(ObservabilitySemconv.GEN_AI_SYSTEM, "openjiuwen")
                .containsEntry(ObservabilitySemconv.GEN_AI_REQUEST_MODEL, "fake-llm-1")
                .containsEntry(ObservabilitySemconv.GEN_AI_REQUEST_TEMPERATURE, 0.5)
                .containsEntry(ObservabilitySemconv.GEN_AI_USAGE_PROMPT_TOKENS, 12L)
                .containsEntry(ObservabilitySemconv.GEN_AI_USAGE_COMPLETION_TOKENS, 7L)
                .containsEntry(ObservabilitySemconv.GEN_AI_USAGE_TOTAL_TOKENS, 19L)
                .containsEntry(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.content", "42")
                .containsEntry(ObservabilitySemconv.GEN_AI_RESPONSE_FINISH_REASON, "stop");
        assertThat(llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_PROMPT + ".1.content"))
                .asString()
                .contains("Compute 6 * 7");
        assertThat((Double) llmSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_RESPONSE_TTFT_MS))
                .isGreaterThanOrEqualTo(0.0);
        assertThat(llmSpan.getEvents())
                .extracting(TelemetrySpan.Event::name)
                .containsExactly("llm.chunk", "llm.chunk");
        assertThat(llmSpan.isEnded()).isTrue();

        TelemetrySpan reasoningSpan = findSpan(tracer, "llm.reasoning");
        assertThat(reasoningSpan.getParent()).isSameAs(llmSpan);
        assertThat(reasoningSpan.getAttributes())
                .containsEntry(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.is_reasoning", true);
        assertThat(reasoningSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.content"))
                .asString()
                .contains("forty-two");
    }

    @Test
    void toolCallInsideLlmSpanRecordsAttributesAndParent() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "Use the calc tool."));

        handler.onLlmInvokeInput(Map.of("messages", messages, "model", "fake-llm-1"));
        handler.onToolCallStarted(Map.of(
                "tool_name", "calc",
                "tool_id", "calc-1",
                "inputs", List.of(List.of(), Map.of("expr", "6*7"))
        ));
        handler.onToolCallFinished(Map.of("tool_name", "calc", "result", 42));
        handler.onLlmInvokeOutput(Map.of("result", new FakeAssistantMessage("42", "", "stop")));

        TelemetrySpan llmSpan = findSpan(tracer, "llm.call");
        TelemetrySpan toolSpan = findSpan(tracer, "tool.calc");

        assertThat(toolSpan.getParent()).isSameAs(llmSpan);
        assertThat(toolSpan.getAttributes())
                .containsEntry(ObservabilitySemconv.GEN_AI_TOOL_NAME, "calc")
                .containsEntry("gen_ai.tool.id", "calc-1")
                .containsEntry(ObservabilitySemconv.GEN_AI_TOOL_OUTPUT, "42");
        assertThat(toolSpan.getAttributes().get(ObservabilitySemconv.GEN_AI_TOOL_INPUT))
                .asString()
                .contains("6*7");
        assertThat(toolSpan.isEnded()).isTrue();
    }

    @Test
    void llmCallErrorMarksOpenSpanErrorAndRecordsException() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        RuntimeException error = new RuntimeException("provider down");

        handler.onLlmInvokeInput(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "fail please")),
                "model", "fake-llm-1"
        ));
        handler.onLlmCallError(Map.of("error", error));

        TelemetrySpan span = findSpan(tracer, "llm.call");
        assertThat(span.getStatusCode()).isEqualTo(TelemetrySpan.StatusCode.ERROR);
        assertThat(span.getStatusDescription()).contains("provider down");
        assertThat(span.getExceptions()).containsExactly(error);
        assertThat(span.isEnded()).isTrue();
    }

    @Test
    void agentInvokeEmitsNamedSpanWithInputAndOutput() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelCallbackHandler handler = new OtelCallbackHandler(new ObservabilityConfig(), tracer);
        Map<String, String> input = Map.of("agent_id", "leader", "user_input", "hello");

        handler.onAgentInvokeInput(Map.of("_args", List.of(input)));
        handler.onAgentInvokeOutput(Map.of("_args", List.of(input), "result", "acknowledged"));

        TelemetrySpan span = findSpan(tracer, "agent.leader");
        assertThat(span.getAttributes())
                .containsEntry(ObservabilitySemconv.AT_AGENT_ID, "leader")
                .containsEntry(ObservabilitySemconv.AT_AGENT_INPUT, "hello")
                .containsEntry(ObservabilitySemconv.AT_AGENT_OUTPUT, "acknowledged");
        assertThat(span.getStatusCode()).isEqualTo(TelemetrySpan.StatusCode.OK);
        assertThat(span.isEnded()).isTrue();
    }

    @Test
    void redactionReplacesPromptAndCompletionText() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        ObservabilityConfig config = new ObservabilityConfig();
        config.setRedactPrompts(true);
        config.setRedactCompletions(true);
        OtelCallbackHandler handler = new OtelCallbackHandler(config, tracer);

        handler.onLlmInvokeInput(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "secret prompt")),
                "model", "fake-llm-1"
        ));
        handler.onLlmInvokeOutput(Map.of("result", new FakeAssistantMessage("secret answer", "", "stop")));

        TelemetrySpan span = findSpan(tracer, "llm.call");
        String prompt = String.valueOf(span.getAttributes().get(ObservabilitySemconv.GEN_AI_PROMPT + ".0.content"));
        String completion = String.valueOf(
                span.getAttributes().get(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.content")
        );
        assertThat(prompt).startsWith("sha256:").doesNotContain("secret");
        assertThat(completion).startsWith("sha256:").doesNotContain("secret");
    }

    private static TelemetrySpan findSpan(TelemetryTracer.InMemory tracer, String name) {
        return tracer.getSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }

    private record FakeUsage(
            long inputTokens,
            long outputTokens,
            long totalTokens,
            String modelName
    ) {
        private FakeUsage() {
            this(12, 7, 19, "fake-llm-1");
        }
    }

    private record FakeAssistantMessage(
            String content,
            String reasoningContent,
            String finishReason,
            FakeUsage usageMetadata
    ) {
        private FakeAssistantMessage(String content, String reasoningContent, String finishReason) {
            this(content, reasoningContent, finishReason, new FakeUsage());
        }
    }

    private record FakeChunk(String content) {
    }
}
