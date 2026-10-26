/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Span-production tests for {@link TrajectoryRail} using a mocked DeepAgent.
 *
 * @since 0.1.18
 */
class TrajectoryRailTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TrajectoryRail rail;
    private DeepAgent agent;
    private RunSpans.RunSpan run;
    private List<OtlpSpanRecord> finals;

    @BeforeEach
    void setUp() {
        TrajectoryObservability.init(new TrajectoryObservabilityConfig(
                true, "rail-test-service", 1.0d));
        TrajectoryObservability observability = TrajectoryObservability.active();
        observability.getSpanRecordProcessor().registerConsumer(new TrajectoryRecordConsumer() {
            @Override
            public void consume(OtlpSpanRecord record) {
                finals.add(record);
            }
        });
        rail = new TrajectoryRail(observability.getTracer());
        agent = mock(DeepAgent.class);
        finals = new CopyOnWriteArrayList<>();
        run = RunSpans.open(runInfo());
        rail.bindSession(agent, run);
    }

    @AfterEach
    void tearDown() {
        rail.unbindSession(agent);
        run.complete();
        TrajectoryObservability.shutdown();
        TrajectorySpanContext.clearThreadState();
    }

    @Test
    void modelCallProducesInferenceSpanChildOfRunRoot() throws Exception {
        rail.beforeModelCall(modelContext(ModelCallInputs.builder()
                .messages(List.of(
                        new BaseMessage("system", "be nice"),
                        new BaseMessage("user", "do the thing")))
                .build()));
        rail.afterModelCall(callbackContext(Map.of("response",
                new AssistantMessage("done"))));

        assertThat(finals).hasSize(1);
        OtlpSpanRecord record = finals.get(0);
        assertThat(record.recordKind()).isEqualTo("inference");
        assertThat(record.sessionId()).isEqualTo("rail-session");
        assertThat(record.executionSubjectId()).isEqualTo("main");
        assertThat(record.lifecycle()).isEqualTo("final");

        JsonNode spanNode = MAPPER.readTree(record.otlpJson())
                .at("/resourceSpans/0/scopeSpans/0/spans/0");
        assertThat(spanNode.get("kind").asInt()).isEqualTo(3);
        assertThat(spanNode.get("parentSpanId").asText())
                .isEqualTo(run.span().getSpanContext().getSpanId());
        JsonNode inputMessages = MAPPER.readTree(
                attribute(spanNode, TrajectorySemConv.GEN_AI_INPUT_MESSAGES).orElseThrow());
        assertThat(inputMessages).hasSize(2);
        assertThat(inputMessages.get(0).get("role").asText()).isEqualTo("system");
        JsonNode outputMessages = MAPPER.readTree(
                attribute(spanNode, TrajectorySemConv.GEN_AI_OUTPUT_MESSAGES).orElseThrow());
        assertThat(outputMessages.get(0).get("role").asText()).isEqualTo("assistant");
        assertThat(outputMessages.get(0).get("parts").get(0).get("content").asText())
                .isEqualTo("done");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_SYSTEM_INSTRUCTIONS))
                .hasValueSatisfying(value -> assertThat(value).contains("be nice"));
    }

    @Test
    void modelCallWithoutBindingProducesNoSpan() {
        rail.unbindSession(agent);
        rail.beforeModelCall(modelContext(ModelCallInputs.builder()
                .messages(List.of("hi")).build()));
        rail.afterModelCall(callbackContext(Map.of("response",
                new AssistantMessage("ignored"))));

        assertThat(finals).isEmpty();
    }

    @Test
    void modelExceptionMarksErrorSpan() {
        rail.beforeModelCall(modelContext(ModelCallInputs.builder()
                .messages(List.of("hi")).build()));
        AgentCallbackContext errorContext = new AgentCallbackContext();
        errorContext.setException(new IllegalStateException("provider down"));
        rail.onModelException(errorContext);

        assertThat(finals).hasSize(1);
        assertThat(finals.get(0).hasError()).isTrue();
    }

    @Test
    void toolCallProducesToolSpanWithArgumentsAndResult() throws Exception {
        ToolCallInputs start = new ToolCallInputs();
        start.setToolName("bash");
        start.setToolCall(ToolCall.builder().id("call-9").name("bash")
                .arguments("{}").build());
        start.setToolArgs("{\"command\": \"ls\"}");
        rail.beforeToolCall(toolContext(start));

        ToolCallInputs done = new ToolCallInputs();
        done.setToolName("bash");
        done.setToolResult("{\"exit\": 0}");
        rail.afterToolCall(toolContext(done));

        assertThat(finals).hasSize(1);
        OtlpSpanRecord record = finals.get(0);
        assertThat(record.recordKind()).isEqualTo("tool");

        JsonNode spanNode = MAPPER.readTree(record.otlpJson())
                .at("/resourceSpans/0/scopeSpans/0/spans/0");
        assertThat(spanNode.get("name").asText()).isEqualTo("execute_tool bash");
        assertThat(spanNode.get("kind").asInt()).isEqualTo(1);
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_TOOL_NAME)).hasValue("bash");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_TOOL_CALL_ID))
                .hasValue("call-9");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_TOOL_CALL_ARGUMENTS))
                .hasValueSatisfying(value -> assertThat(value).contains("ls"));
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_TOOL_CALL_RESULT))
                .hasValueSatisfying(value -> assertThat(value).contains("exit"));
    }

    @Test
    void usageAttributesCarryTokenCounts() throws Exception {
        AssistantMessage response = new AssistantMessage("ok");
        response.setUsageMetadata(UsageMetadata.builder()
                .inputTokens(20).outputTokens(6).totalTokens(26).cacheTokens(8).build());
        rail.beforeModelCall(modelContext(ModelCallInputs.builder()
                .messages(List.of("hi")).build()));
        rail.afterModelCall(callbackContext(Map.of("response", response)));

        JsonNode spanNode = MAPPER.readTree(finals.get(0).otlpJson())
                .at("/resourceSpans/0/scopeSpans/0/spans/0");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_USAGE_INPUT_TOKENS))
                .hasValue("20");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_USAGE_OUTPUT_TOKENS))
                .hasValue("6");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_USAGE_TOTAL_TOKENS))
                .hasValue("26");
        assertThat(attribute(spanNode, TrajectorySemConv.GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS))
                .hasValue("8");
    }

    private static TrajectorySpanContext.RunInfo runInfo() {
        return TrajectorySpanContext.RunInfo.builder()
                .sessionId("rail-session")
                .mode("work")
                .requestId("req-1")
                .runId("run-1")
                .turnId("turn-1")
                .turnNumber(1L)
                .requestNumber(1L)
                .subjectId("main")
                .subjectDisplayName("Assistant")
                .subjectKind("main_agent")
                .subjectSessionId("rail-session")
                .build();
    }

    private CallbackContext modelContext(ModelCallInputs inputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("messages", inputs.getMessages());
        values.put("tools", inputs.getTools());
        values.put("response", inputs.getResponse());
        return new CallbackContext(agent, values);
    }

    private CallbackContext toolContext(ToolCallInputs inputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_call", inputs.getToolCall());
        values.put("tool_name", inputs.getToolName());
        values.put("tool_args", inputs.getToolArgs());
        values.put("tool_result", inputs.getToolResult());
        return new CallbackContext(agent, values);
    }

    private CallbackContext callbackContext(Map<String, Object> values) {
        return new CallbackContext(agent, values);
    }

    private static Optional<String> attribute(JsonNode spanNode, String key) {
        for (JsonNode attribute : spanNode.get("attributes")) {
            if (key.equals(attribute.get("key").asText())) {
                JsonNode value = attribute.get("value");
                if (value.has("stringValue")) {
                    return Optional.of(value.get("stringValue").asText());
                }
                if (value.has("intValue")) {
                    return Optional.of(value.get("intValue").asText());
                }
                return Optional.of(value.toString());
            }
        }
        return Optional.empty();
    }
}
