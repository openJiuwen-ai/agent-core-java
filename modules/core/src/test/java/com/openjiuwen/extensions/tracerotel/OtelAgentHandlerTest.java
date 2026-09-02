/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.tracer.InvokeType;
import com.openjiuwen.core.session.tracer.NodeStatus;
import com.openjiuwen.core.session.tracer.SpanManager;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OtelAgentHandler}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/extensions/tracer_otel/test_handler.py}
 * (agent handler portion).</p>
 */
@DisplayName("OtelAgentHandler tests")
class OtelAgentHandlerTest {

    private OtelTracerConfig config;
    private OtelTracerConfig noRedactConfig;

    @BeforeEach
    void setUp() {
        ConftestOtel.clearExporter();
        config = OtelTracerConfig.builder().isRedactionEnabled(true).build();
        noRedactConfig = OtelTracerConfig.builder().isRedactionEnabled(false).build();
    }

    private TraceAgentSpan newSpan() {
        return new SpanManager("test-trace-id").createAgentSpan(null);
    }

    private TraceAgentSpan childSpan(TraceAgentSpan parent) {
        return new SpanManager("test-trace-id").createAgentSpan(parent);
    }

    private Map<String, Object> instanceInfo(String className) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("class_name", className);
        return info;
    }

    private SpanData firstSpan() {
        List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
        assertThat(spans).isNotEmpty();
        return spans.get(0);
    }

    // ================================================================
    // LLM events
    // ================================================================

    @Nested
    @DisplayName("LLM events")
    class TestLlmEvents {

        @Test
        @DisplayName("onLlmStart creates CLIENT span with gen_ai attributes")
        void testLlmStartCreatesClientSpan() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            span.setInvokeType(InvokeType.LLM.getValue());
            span.setName("MyModel");

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("prompt", "hello");
            handler.onLlmStart(span, inputs, instanceInfo("MyModel"));
            handler.onLlmEnd(span, Map.of());

            SpanData s = firstSpan();
            assertThat(s.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_SYSTEM)))
                    .isEqualTo(SemConv.GEN_AI_SYSTEM_VALUE);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_OPERATION_NAME)))
                    .isEqualTo("chat");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_REQUEST_MODEL)))
                    .isEqualTo("MyModel");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_INVOKE_TYPE)))
                    .isEqualTo("llm");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_NAME)))
                    .isEqualTo("MyModel");
        }

        @Test
        @DisplayName("onLlmEnd sets completion and closes span")
        void testLlmEndSetsCompletion() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            handler.onLlmStart(span, Map.of("prompt", "hi"), instanceInfo("MyModel"));

            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("content", "world");
            handler.onLlmEnd(span, outputs);

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.OK);
            String completion = s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_COMPLETION));
            assertThat(completion).isNotNull();
            // redaction enabled → sha256 prefix
            assertThat(completion).startsWith("sha256:");
        }

        @Test
        @DisplayName("onLlmError marks ERROR status and records exception")
        void testLlmErrorMarksError() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            handler.onLlmStart(span, Map.of("prompt", "hi"), instanceInfo("MyModel"));

            BaseError error = new BaseError(StatusCode.MODEL_CALL_FAILED, "boom", null, null);
            handler.onLlmError(span, error);

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STATUS)))
                    .isEqualTo(NodeStatus.ERROR.getValue());
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_ERROR_MESSAGE)))
                    .contains("boom");
            // OJ_ERROR should contain the error_code
            String ojError = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_ERROR));
            assertThat(ojError).contains(String.valueOf(StatusCode.MODEL_CALL_FAILED.getCode()));
            // exception should be recorded as an event
            assertThat(s.getEvents()).isNotEmpty();
        }

        @Test
        @DisplayName("onLlmError with generic Throwable uses WORKFLOW_EXECUTION_ERROR")
        void testLlmErrorGenericThrowable() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            handler.onLlmStart(span, Map.of("prompt", "hi"), instanceInfo("MyModel"));

            handler.onLlmError(span, new RuntimeException("unexpected"));

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            String ojError = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_ERROR));
            assertThat(ojError).contains(String.valueOf(StatusCode.WORKFLOW_EXECUTION_ERROR.getCode()));
        }
    }

    // ================================================================
    // Redaction
    // ================================================================

    @Nested
    @DisplayName("Redaction")
    class TestRedaction {

        @Test
        @DisplayName("prompt is redacted (sha256) when redactionEnabled=true")
        void testPromptRedactedWhenEnabled() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            handler.onLlmStart(span, Map.of("prompt", "secret"), instanceInfo("MyModel"));

            // onLlmStart does not end the span; manually end to inspect attrs via a different path:
            // we can inspect the in-progress span attributes only after end. So end it.
            handler.onLlmEnd(span, Map.of("content", "reply"));

            SpanData s = firstSpan();
            String prompt = s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_PROMPT));
            assertThat(prompt).isNotNull().startsWith("sha256:");
        }

        @Test
        @DisplayName("prompt is NOT redacted when redactionEnabled=false")
        void testPromptNotRedactedWhenDisabled() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();
            handler.onLlmStart(span, Map.of("prompt", "secret"), instanceInfo("MyModel"));
            handler.onLlmEnd(span, Map.of("content", "reply"));

            SpanData s = firstSpan();
            String prompt = s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_PROMPT));
            assertThat(prompt).isNotNull();
            assertThat(prompt).doesNotStartWith("sha256:");
            // the serialized payload should contain "secret"
            assertThat(prompt).contains("secret");
        }

        @Test
        @DisplayName("fine-grained redactPrompts=false overrides redactionEnabled=true")
        void testRedactPromptsOverrideFalse() {
            OtelTracerConfig overrideConfig = OtelTracerConfig.builder()
                    .isRedactionEnabled(true)
                    .shouldRedactPrompts(false)
                    .build();
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, overrideConfig);
            TraceAgentSpan span = newSpan();
            handler.onLlmStart(span, Map.of("prompt", "secret"), instanceInfo("MyModel"));
            handler.onLlmEnd(span, Map.of("content", "reply"));

            SpanData s = firstSpan();
            String prompt = s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_PROMPT));
            assertThat(prompt).doesNotStartWith("sha256:");
            // completion still redacted (redactCompletions=null → falls back to redactionEnabled=true)
            String completion = s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_COMPLETION));
            assertThat(completion).startsWith("sha256:");
        }

        @Test
        @DisplayName("non-LLM inputs are redacted via legacy flag")
        void testNonLlmInputsRedacted() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            handler.onChainStart(span, "sensitive-data", instanceInfo("MyChain"));
            handler.onChainEnd(span, "result-data");

            SpanData s = firstSpan();
            String inputs = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_INPUTS));
            assertThat(inputs).startsWith("sha256:");
        }
    }

    // ================================================================
    // Non-LLM events (Chain / Plugin)
    // ================================================================

    @Nested
    @DisplayName("Non-LLM events")
    class TestNonLlmEvents {

        @Test
        @DisplayName("onChainStart creates INTERNAL span with invoke_type=chain")
        void testChainStartCreatesInternalSpan() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();
            handler.onChainStart(span, "input", instanceInfo("MyChain"));

            // close it to flush to exporter
            handler.onChainEnd(span, "output");

            SpanData s = firstSpan();
            assertThat(s.getKind()).isEqualTo(SpanKind.INTERNAL);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_INVOKE_TYPE)))
                    .isEqualTo("chain");
            assertThat(s.getName()).startsWith("chain.");
        }

        @Test
        @DisplayName("onChainEnd sets outputs and closes span with OK status")
        void testChainEndSetsOutputs() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();
            handler.onChainStart(span, "input", instanceInfo("MyChain"));
            handler.onChainEnd(span, "output");

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.OK);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_OUTPUTS)))
                    .isEqualTo("output");
        }

        @Test
        @DisplayName("onPluginStart sets gen_ai.tool.name and execute_tool operation")
        void testPluginStartSetsToolName() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();
            handler.onPluginStart(span, "query", instanceInfo("SearchTool"));
            handler.onPluginEnd(span, "result");

            SpanData s = firstSpan();
            assertThat(s.getKind()).isEqualTo(SpanKind.INTERNAL);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_TOOL_NAME)))
                    .isEqualTo("SearchTool");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_OPERATION_NAME)))
                    .isEqualTo("execute_tool");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_INVOKE_TYPE)))
                    .isEqualTo("plugin");
        }

        @Test
        @DisplayName("onChainError marks ERROR status")
        void testChainErrorMarksError() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            handler.onChainStart(span, "input", instanceInfo("MyChain"));

            handler.onChainError(span, new RuntimeException("chain failed"));

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STATUS)))
                    .isEqualTo(NodeStatus.ERROR.getValue());
        }
    }

    // ================================================================
    // Parent-child relation
    // ================================================================

    @Nested
    @DisplayName("Parent-child relation")
    class TestParentChild {

        @Test
        @DisplayName("LLM span is child of chain span")
        void testLlmChildOfChain() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);

            // Start parent chain span
            TraceAgentSpan parentSpan = newSpan();
            handler.onChainStart(parentSpan, "input", instanceInfo("MyChain"));

            // Create child LLM span whose parentInvokeId == parent's invokeId
            TraceAgentSpan childSpan = childSpan(parentSpan);
            assertThat(childSpan.getParentInvokeId()).isEqualTo(parentSpan.getInvokeId());

            handler.onLlmStart(childSpan, Map.of("prompt", "hi"), instanceInfo("MyModel"));
            handler.onLlmEnd(childSpan, Map.of("content", "reply"));
            handler.onChainEnd(parentSpan, "output");

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            assertThat(spans).hasSize(2);

            // Find child (CLIENT) and parent (INTERNAL)
            SpanData child = spans.stream()
                    .filter(s -> s.getKind() == SpanKind.CLIENT)
                    .findFirst().orElseThrow();
            SpanData parent = spans.stream()
                    .filter(s -> s.getKind() == SpanKind.INTERNAL)
                    .findFirst().orElseThrow();

            // child's parentSpanId should equal parent's spanId
            assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
        }
    }

    // ================================================================
    // Normalization & field resolution
    // ================================================================

    @Nested
    @DisplayName("Normalization & field resolution")
    class TestNormalization {

        @Test
        @DisplayName("LLM payload is serialized to JSON string")
        void testLlmPayloadSerialized() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("messages", List.of(Map.of("role", "user", "content", "hi")));
            handler.onLlmStart(span, inputs, instanceInfo("MyModel"));
            handler.onLlmEnd(span, Map.of("content", "reply"));

            SpanData s = firstSpan();
            String prompt = s.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_PROMPT));
            assertThat(prompt).contains("messages");
            assertThat(prompt).contains("hi");
        }

        @Test
        @DisplayName("span fields (invokeType, name, metaData) used when present")
        void testSpanFieldsUsedWhenPresent() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();
            span.setInvokeType("custom_type");
            span.setName("CustomName");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("key", "value");
            span.setMetaData(meta);

            handler.onChainStart(span, "input", instanceInfo("MyChain"));
            handler.onChainEnd(span, "output");

            SpanData s = firstSpan();
            // span.getInvokeType() takes precedence over the handler's default
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_INVOKE_TYPE)))
                    .isEqualTo("custom_type");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_NAME)))
                    .isEqualTo("CustomName");
            String metaData = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_META_DATA));
            assertThat(metaData).contains("key").contains("value");
        }

        @Test
        @DisplayName("instanceInfo used as fallback when span fields empty")
        void testInstanceInfoFallback() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, noRedactConfig);
            TraceAgentSpan span = newSpan();
            // Do NOT set invokeType / name / metaData on span
            handler.onChainStart(span, "input", instanceInfo("FallbackClass"));
            handler.onChainEnd(span, "output");

            SpanData s = firstSpan();
            // invokeType falls back to handler default ("chain")
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_INVOKE_TYPE)))
                    .isEqualTo("chain");
            // name falls back to instanceInfo.class_name
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_AGENT_NAME)))
                    .isEqualTo("FallbackClass");
        }
    }

    // ================================================================
    // Error handling robustness
    // ================================================================

    @Nested
    @DisplayName("Robustness")
    class TestRobustness {

        @Test
        @DisplayName("handler does not throw when inputs are null")
        void testNullInputsDoNotThrow() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            // Should not throw
            handler.onLlmStart(span, null, null);
            handler.onLlmEnd(span, null);

            SpanData s = firstSpan();
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("onLlmEnd without prior onLlmStart is a no-op")
        void testLlmEndWithoutStartIsNoOp() {
            OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
            TraceAgentSpan span = newSpan();
            // Should not throw and should not produce a span
            handler.onLlmEnd(span, Map.of("content", "reply"));
            assertThat(ConftestOtel.EXPORTER.getFinishedSpanItems()).isEmpty();
        }
    }
}
