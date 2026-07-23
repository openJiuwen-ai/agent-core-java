/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.session.tracer.NodeStatus;
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
 * Unit tests for {@link OtelWorkflowHandler}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/extensions/tracer_otel/test_handler.py}
 * (workflow handler portion).</p>
 */
@DisplayName("OtelWorkflowHandler tests")
class OtelWorkflowHandlerTest {

    private OtelTracerConfig config;

    @BeforeEach
    void setUp() {
        ConftestOtel.clearExporter();
        config = OtelTracerConfig.builder().isRedactionEnabled(false).build();
    }

    private Map<String, Object> workflowRootMeta(String workflowId, String workflowName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workflow_id", workflowId);
        meta.put("workflow_name", workflowName);
        return meta;
    }

    private Map<String, Object> componentMeta(String workflowId, String componentId,
                                              String componentType, String componentName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workflow_id", workflowId);
        meta.put("component_id", componentId);
        meta.put("component_type", componentType);
        meta.put("component_name", componentName);
        return meta;
    }

    private SpanData firstSpan() {
        List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
        assertThat(spans).isNotEmpty();
        return spans.get(0);
    }

    // ================================================================
    // Span creation & lifecycle
    // ================================================================

    @Nested
    @DisplayName("Span lifecycle")
    class TestLifecycle {

        @Test
        @DisplayName("onCallStart creates span for workflow root")
        void testCallStartCreatesWorkflowRootSpan() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            Map<String, Object> meta = workflowRootMeta("wf1", "MyWorkflow");
            handler.onCallStart("root_invoke", meta, "input", true, null);
            handler.onCallDone("root_invoke", "output");

            SpanData s = firstSpan();
            assertThat(s.getKind()).isEqualTo(SpanKind.INTERNAL);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_ID)))
                    .isEqualTo("wf1");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_NAME)))
                    .isEqualTo("MyWorkflow");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_INVOKE_ID)))
                    .isEqualTo("root_invoke");
            // span name for workflow root is the invokeId
            assertThat(s.getName()).isEqualTo("root_invoke");
        }

        @Test
        @DisplayName("onCallDone closes span with OK status and outputs")
        void testCallDoneClosesSpan() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);
            handler.onCallDone("inv1", "result");

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.OK);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_OUTPUTS)))
                    .isEqualTo("result");
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STATUS)))
                    .isEqualTo(NodeStatus.FINISH.getValue());
        }

        @Test
        @DisplayName("source_ids are serialized as attribute")
        void testSourceIdsSerialized() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true,
                    List.of("src1", "src2"));
            handler.onCallDone("inv1", null);

            SpanData s = firstSpan();
            String sourceIds = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_SOURCE_IDS));
            assertThat(sourceIds).contains("src1").contains("src2");
        }
    }

    // ================================================================
    // Component type mapping
    // ================================================================

    @Nested
    @DisplayName("Component type mapping")
    class TestComponentType {

        @Test
        @DisplayName("LLM component uses SpanKind.CLIENT")
        void testLlmComponentUsesClientKind() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            Map<String, Object> meta = componentMeta("wf1", "comp1", "LLM", "MyLLM");
            // parent workflow root must exist first
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            handler.onCallStart("comp_invoke", meta, "input", true, null);
            handler.onCallDone("comp_invoke", "output");
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();
            assertThat(comp.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(comp.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_OPERATION_NAME)))
                    .isEqualTo("chat");
        }

        @Test
        @DisplayName("IntentDetection component uses SpanKind.CLIENT")
        void testIntentDetectionComponentUsesClientKind() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            Map<String, Object> meta = componentMeta("wf1", "comp1", "IntentDetection", "Intent");
            handler.onCallStart("comp_invoke", meta, "input", true, null);
            handler.onCallDone("comp_invoke", null);
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();
            assertThat(comp.getKind()).isEqualTo(SpanKind.CLIENT);
        }

        @Test
        @DisplayName("Tool component sets execute_tool operation")
        void testToolComponentSetsExecuteTool() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            Map<String, Object> meta = componentMeta("wf1", "comp1", "Tool", "SearchTool");
            handler.onCallStart("comp_invoke", meta, "input", true, null);
            handler.onCallDone("comp_invoke", null);
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();
            assertThat(comp.getKind()).isEqualTo(SpanKind.INTERNAL);
            assertThat(comp.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_OPERATION_NAME)))
                    .isEqualTo("execute_tool");
        }

        @Test
        @DisplayName("non-LLM/non-Tool component uses SpanKind.INTERNAL with no operation name")
        void testNonLlmComponentUsesInternalKind() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            Map<String, Object> meta = componentMeta("wf1", "comp1", "Branch", "BranchComp");
            handler.onCallStart("comp_invoke", meta, "input", true, null);
            handler.onCallDone("comp_invoke", null);
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();
            assertThat(comp.getKind()).isEqualTo(SpanKind.INTERNAL);
            assertThat(comp.getAttributes().get(AttributeKey.stringKey(SemConv.GEN_AI_OPERATION_NAME)))
                    .isNull();
        }

        @Test
        @DisplayName("component attributes (id, type, name) are set")
        void testComponentAttributesSet() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            Map<String, Object> meta = componentMeta("wf1", "comp1", "LLM", "MyLLM");
            handler.onCallStart("comp_invoke", meta, "input", true, null);
            handler.onCallDone("comp_invoke", null);
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();
            assertThat(comp.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_COMPONENT_ID)))
                    .isEqualTo("comp1");
            assertThat(comp.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_COMPONENT_TYPE)))
                    .isEqualTo("LLM");
            assertThat(comp.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_COMPONENT_NAME)))
                    .isEqualTo("MyLLM");
        }
    }

    // ================================================================
    // Invoke exception handling
    // ================================================================

    @Nested
    @DisplayName("Invoke exception handling")
    class TestInvokeException {

        @Test
        @DisplayName("onInvoke with BaseError marks ERROR and records error_code")
        void testInvokeBaseErrorMarksError() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            BaseError error = new BaseError(StatusCode.WORKFLOW_EXECUTION_ERROR, "exec failed", null, null);
            handler.onInvoke("inv1", null, error);

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STATUS)))
                    .isEqualTo(NodeStatus.ERROR.getValue());
            String ojError = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_ERROR));
            assertThat(ojError).contains(String.valueOf(StatusCode.WORKFLOW_EXECUTION_ERROR.getCode()));
            assertThat(s.getEvents()).isNotEmpty();
        }

        @Test
        @DisplayName("onInvoke with GraphInterrupt marks INTERRUPTED without ERROR")
        void testInvokeGraphInterruptMarksInterrupted() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            GraphInterrupt interrupt = new GraphInterrupt(new Interrupt("pause"));
            handler.onInvoke("inv1", null, interrupt);

            SpanData s = firstSpan();
            // GraphInterrupt should NOT set ERROR status
            assertThat(s.getStatus().getStatusCode()).isNotEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STATUS)))
                    .isEqualTo(NodeStatus.INTERRUPTED.getValue());
            // OJ_ERROR should not be set for GraphInterrupt
            assertThat(s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_ERROR))).isNull();
        }

        @Test
        @DisplayName("onInvoke with generic Throwable uses WORKFLOW_EXECUTION_ERROR")
        void testInvokeGenericThrowable() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            handler.onInvoke("inv1", null, new RuntimeException("unexpected"));

            SpanData s = firstSpan();
            assertThat(s.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            String ojError = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_ERROR));
            assertThat(ojError).contains(String.valueOf(StatusCode.WORKFLOW_EXECUTION_ERROR.getCode()));
        }

        @Test
        @DisplayName("onInvoke with inner_error in onInvokeData sets OJ_INNER_ERROR")
        void testInvokeInnerError() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            Map<String, Object> onInvokeData = new LinkedHashMap<>();
            onInvokeData.put("inner_error", "something went wrong inside");
            handler.onInvoke("inv1", onInvokeData, new RuntimeException("outer"));

            SpanData s = firstSpan();
            String innerError = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_INNER_ERROR));
            assertThat(innerError).contains("something went wrong inside");
        }
    }

    // ================================================================
    // Data buffering
    // ================================================================

    @Nested
    @DisplayName("Data buffering")
    class TestDataBuffering {

        @Test
        @DisplayName("onInvoke buffers data, flushed on onCallDone")
        void testInvokeBuffersDataFlushedOnCallDone() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            Map<String, Object> data1 = new LinkedHashMap<>();
            data1.put("step", 1);
            Map<String, Object> data2 = new LinkedHashMap<>();
            data2.put("step", 2);
            handler.onInvoke("inv1", data1, null);
            handler.onInvoke("inv1", data2, null);

            handler.onCallDone("inv1", "output");

            SpanData s = firstSpan();
            String invokeData = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_INVOKE_DATA));
            assertThat(invokeData).isNotNull();
            assertThat(invokeData).contains("step").contains("1").contains("2");
        }

        @Test
        @DisplayName("onPreStream / onPostStream buffer chunks, flushed on onCallDone")
        void testStreamBufferingFlushedOnCallDone() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            Map<String, Object> inChunk = new LinkedHashMap<>();
            inChunk.put("text", "hello");
            handler.onPreStream("inv1", inChunk, true);

            Map<String, Object> outChunk = new LinkedHashMap<>();
            outChunk.put("result", "ok");
            handler.onPostStream("inv1", outChunk);

            handler.onCallDone("inv1", null);

            SpanData s = firstSpan();
            String streamInputs = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STREAM_INPUTS));
            assertThat(streamInputs).isNotNull().contains("hello");
            String streamOutputs = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_STREAM_OUTPUTS));
            assertThat(streamOutputs).isNotNull().contains("ok");
        }

        @Test
        @DisplayName("onPreInvoke sets inputs attribute")
        void testPreInvokeSetsInputs() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "initial", true, null);

            handler.onPreInvoke("inv1", "updated_input", null, true);
            handler.onCallDone("inv1", null);

            SpanData s = firstSpan();
            String inputs = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_INPUTS));
            assertThat(inputs).isEqualTo("updated_input");
        }

        @Test
        @DisplayName("onPostInvoke sets outputs attribute")
        void testPostInvokeSetsOutputs() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            handler.onPostInvoke("inv1", "post_output", "post_input");
            handler.onCallDone("inv1", null);

            SpanData s = firstSpan();
            String outputs = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_WORKFLOW_OUTPUTS));
            assertThat(outputs).isEqualTo("post_output");
        }

        @Test
        @DisplayName("onInteract sets interactive_inputs attribute")
        void testInteractSetsInteractiveInputs() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", workflowRootMeta("wf1", "MyWf"), "input", true, null);

            handler.onInteract("inv1", "user_input", null, true);
            handler.onCallDone("inv1", null);

            SpanData s = firstSpan();
            String interactive = s.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_INTERACTIVE_INPUTS));
            assertThat(interactive).isEqualTo("user_input");
        }
    }

    // ================================================================
    // Parent-child (sub-workflow)
    // ================================================================

    @Nested
    @DisplayName("Sub-workflow parent-child")
    class TestSubWorkflow {

        @Test
        @DisplayName("component in root workflow is child of root span")
        void testComponentChildOfRoot() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);

            // Root workflow
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            // Component in root workflow (parentNodeId="")
            Map<String, Object> compMeta = componentMeta("wf1", "comp1", "LLM", "MyLLM");
            handler.onCallStart("comp_invoke", compMeta, "input", true, null);

            handler.onCallDone("comp_invoke", "output");
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            assertThat(spans).hasSize(2);

            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();
            SpanData root = spans.stream()
                    .filter(s -> !s.getName().startsWith("component."))
                    .findFirst().orElseThrow();

            // component's parentSpanId should equal root's spanId
            assertThat(comp.getParentSpanId()).isEqualTo(root.getSpanId());
        }

        @Test
        @DisplayName("sub-workflow root is child of host component span")
        void testSubWorkflowRootChildOfHostComponent() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);

            // Root workflow
            handler.onCallStart("root", workflowRootMeta("wf1", "MyWf"), null, true, null);
            // Host component (component_id="comp1")
            Map<String, Object> compMeta = componentMeta("wf1", "comp1", "SubWorkflow", "SubWfComp");
            handler.onCallStart("comp_invoke", compMeta, "input", true, null);
            // Sub-workflow root (parent_node_id="comp1" matches host component_id)
            Map<String, Object> subWfMeta = workflowRootMeta("wf2", "SubWorkflow");
            subWfMeta.put("parent_node_id", "comp1");
            handler.onCallStart("sub_wf", subWfMeta, "input", true, null);

            handler.onCallDone("sub_wf", null);
            handler.onCallDone("comp_invoke", null);
            handler.onCallDone("root", null);

            List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
            assertThat(spans).hasSize(3);

            // sub_wf's parentSpanId should equal comp_invoke's spanId
            SpanData subWf = spans.stream()
                    .filter(s -> "sub_wf".equals(s.getName()))
                    .findFirst().orElseThrow();
            SpanData comp = spans.stream()
                    .filter(s -> s.getName().startsWith("component."))
                    .findFirst().orElseThrow();

            assertThat(subWf.getParentSpanId()).isEqualTo(comp.getSpanId());
        }
    }

    // ================================================================
    // Robustness
    // ================================================================

    @Nested
    @DisplayName("Robustness")
    class TestRobustness {

        @Test
        @DisplayName("onCallDone without prior onCallStart is a no-op")
        void testCallDoneWithoutStartIsNoOp() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallDone("nonexistent", "output");
            assertThat(ConftestOtel.EXPORTER.getFinishedSpanItems()).isEmpty();
        }

        @Test
        @DisplayName("onInvoke without prior onCallStart does not throw")
        void testInvokeWithoutStartDoesNotThrow() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onInvoke("nonexistent", Map.of("step", 1), null);
            assertThat(ConftestOtel.EXPORTER.getFinishedSpanItems()).isEmpty();
        }

        @Test
        @DisplayName("null metadata does not throw")
        void testNullMetadataDoesNotThrow() {
            OtelWorkflowHandler handler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
            handler.onCallStart("inv1", null, "input", true, null);
            handler.onCallDone("inv1", null);

            SpanData s = firstSpan();
            assertThat(s).isNotNull();
        }
    }
}
