/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's trace handlers in
 * {@code openjiuwen/core/session/tracer/handler.py}.
 *
 * <p>Also mirrors Python's {@code Tracer.trigger} handler dispatch in
 * {@code openjiuwen/core/session/tracer/tracer.py}.</p>
 */
class TraceHandlerTest {

    @Test
    void nodeStatusTreatsInnerErrorAsError() {
        TraceWorkflowSpan span = new TraceWorkflowSpan("trace-1", "invoke-1", null, "parent-node");
        span.setOnInvokeData(List.of(Map.of("chunk", "partial")));
        span.setInnerError(Map.of("message", "nested failure"));

        assertEquals(NodeStatus.ERROR.getValue(), TraceBaseHandler.getNodeStatus(span));
    }

    @Test
    void workflowOnInvokePersistsInnerErrorAndFormatsErrorStatus() {
        SpanManager manager = new SpanManager("trace-1", "parent-node");
        TraceWorkflowHandler handler = new TraceWorkflowHandler(null, manager);
        handler.onCallStart("invoke-1", Map.of("componentType", "LLM"), Map.of("input", "value"), false,
                List.of("source-1"));

        handler.onInvoke("invoke-1", Map.of(
                "inner_error", Map.of("message", "nested failure"),
                "partial", "payload"
        ), null);

        TraceWorkflowSpan span = (TraceWorkflowSpan) manager.getSpan("invoke-1");
        assertEquals(Map.of("message", "nested failure"), span.getInnerError());
        assertEquals(List.of(Map.of(
                "inner_error", Map.of("message", "nested failure"),
                "partial", "payload"
        )), span.getOnInvokeData());
        assertEquals(NodeStatus.ERROR.getValue(), handler.formatData(span).get("payload") instanceof TraceWorkflowSpan payload
                ? payload.getStatus()
                : null);
    }

    @Test
    void tracerTriggerOnInvokeKeepsInnerErrorOnWorkflowSpan() {
        Tracer tracer = new Tracer();
        tracer.init(null);

        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke", Map.of(
                "invoke_id", "invoke-2",
                "on_invoke_data", Map.of("inner_error", Map.of("code", "inner"))
        ));

        TraceWorkflowSpan span = tracer.getWorkflowSpan("invoke-2", "");
        assertEquals(Map.of("code", "inner"), span.getInnerError());
        assertSame(span, tracer.getWorkflowSpan("invoke-2", ""));
    }
}
