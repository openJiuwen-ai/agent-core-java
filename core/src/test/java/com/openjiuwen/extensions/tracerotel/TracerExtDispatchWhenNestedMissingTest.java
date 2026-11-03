/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerHandlerRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures Tracer still fans out to OTel workflow handlers when a nested
 * TraceWorkflowHandler (parent_node_id) is missing — Python parity.
 */
@DisplayName("Tracer ext dispatch when nested builtin missing")
class TracerExtDispatchWhenNestedMissingTest {

    private OtelTracerConfig config;

    @BeforeEach
    void setUp() {
        ConftestOtel.clearExporter();
        TracerHandlerRegistry.clear();
        config = OtelTracerConfig.builder().isRedactionEnabled(false).build();
    }

    @AfterEach
    void tearDown() {
        TracerHandlerRegistry.clear();
        ConftestOtel.clearExporter();
    }

    @Test
    @DisplayName("nested parent_node_id without builtin still creates OTel span")
    void nestedMissingBuiltinStillDispatchesOtel() {
        OtelWorkflowHandler workflowHandler = new OtelWorkflowHandler(ConftestOtel.OTEL_TRACER, config);
        TracerHandlerRegistry.registerHandler("otel_workflow", workflowHandler);

        Tracer tracer = new Tracer("session-nested-missing");
        tracer.init(new StreamWriterManager(new StreamEmitter()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflow_id", "wf1");
        metadata.put("component_id", "loop_body_node");
        metadata.put("component_type", "ToolComponent");
        metadata.put("component_name", "loop_body_node");
        metadata.put("parent_node_id", "advanced_loop_1");

        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("invoke_id", "loop_body_node");
        kwargs.put("parent_node_id", "advanced_loop_1");
        kwargs.put("metadata", metadata);
        kwargs.put("inputs", Map.of("x", 1));
        kwargs.put("need_send", true);

        // Nested builtin key tracer_workflow.advanced_loop_1 was never registered.
        tracer.trigger(Tracer.TRACE_WORKFLOW, "on_call_start", kwargs);
        tracer.trigger(Tracer.TRACE_WORKFLOW, "on_call_done",
                Map.of("invoke_id", "loop_body_node", "parent_node_id", "advanced_loop_1",
                        "outputs", Map.of("y", 2)));

        List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
        assertThat(spans).isNotEmpty();
        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_INVOKE_ID)))
                .isEqualTo("loop_body_node");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_PARENT_NODE_ID)))
                .isEqualTo("advanced_loop_1");
        assertThat(span.getAttributes().get(AttributeKey.stringKey(SemConv.OJ_SESSION_ID)))
                .isEqualTo("session-nested-missing");
    }

    @Test
    @DisplayName("OtelAgentHandler sets OJ_SESSION_ID when setSessionId is called")
    void agentHandlerSetsSessionIdAttribute() {
        OtelAgentHandler agentHandler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER, config);
        agentHandler.setSessionId("agent-session-1");
        agentHandler.setTraceId("trace-1");

        Tracer tracer = new Tracer("agent-session-1");
        TracerHandlerRegistry.registerHandler("otel_agent", agentHandler);
        tracer.init(new StreamWriterManager(new StreamEmitter()));

        var span = tracer.getTracerAgentSpanManager().createAgentSpan(null);
        Map<String, Object> instanceInfo = new LinkedHashMap<>();
        instanceInfo.put("class_name", "TestAgent");
        instanceInfo.put("type", "agent");

        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("span", span);
        kwargs.put("inputs", Map.of("query", "hi"));
        kwargs.put("instance_info", instanceInfo);
        tracer.trigger(Tracer.TRACE_AGENT, "on_chain_start", kwargs);
        tracer.trigger(Tracer.TRACE_AGENT, "on_chain_end",
                Map.of("span", span, "outputs", Map.of("outputs", "ok")));

        List<SpanData> spans = ConftestOtel.EXPORTER.getFinishedSpanItems();
        assertThat(spans).isNotEmpty();
        assertThat(spans.get(0).getAttributes().get(AttributeKey.stringKey(SemConv.OJ_SESSION_ID)))
                .isEqualTo("agent-session-1");
        assertThat(SemConv.OJ_SESSION_ID).isEqualTo("openjiuwen.session_id");
    }
}
