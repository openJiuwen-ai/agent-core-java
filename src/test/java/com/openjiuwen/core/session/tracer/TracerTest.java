/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code Tracer} in
 * {@code openjiuwen/core/session/tracer/tracer.py}.
 */
class TracerTest {

    @Test
    void constructorLeavesWorkflowManagersEmptyUntilInit() {
        Tracer tracer = new Tracer();

        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke", Map.of(
                "invoke_id", "invoke-before-init",
                "on_invoke_data", Map.of("value", "ignored")
        ));

        assertTrue(tracer.getTracerWorkflowSpanManagerDict().isEmpty());
        assertNull(tracer.getWorkflowSpan("invoke-before-init", ""));
    }

    @Test
    void initRegistersDefaultWorkflowHandler() {
        Tracer tracer = new Tracer();
        tracer.init(null);

        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke", Map.of(
                "invoke_id", "invoke-default",
                "parent_node_id", "",
                "on_invoke_data", Map.of("inner_error", Map.of("code", "inner"))
        ));

        TraceWorkflowSpan span = tracer.getWorkflowSpan("invoke-default", "");
        assertNotNull(span);
        assertEquals(Map.of("code", "inner"), span.getInnerError());
        assertSame(span, tracer.getWorkflowSpan("invoke-default", ""));
    }

    @Test
    void registerWorkflowSpanManagerAddsParentSpecificHandler() {
        Tracer tracer = new Tracer();
        tracer.init(null);
        tracer.registerWorkflowSpanManager("parent-node");

        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke", Map.of(
                "invoke_id", "invoke-child",
                "parent_node_id", "parent-node",
                "on_invoke_data", Map.of("chunk", "payload")
        ));

        assertNull(tracer.getWorkflowSpan("invoke-child", ""));
        TraceWorkflowSpan childSpan = tracer.getWorkflowSpan("invoke-child", "parent-node");
        assertNotNull(childSpan);
        assertEquals("parent-node", childSpan.getParentNodeId());
        assertEquals(Map.of("chunk", "payload"), childSpan.getOnInvokeData().get(0));
    }

    @Test
    void missingWorkflowManagerReturnsNullAndPopIsNoop() {
        Tracer tracer = new Tracer();
        tracer.init(null);

        assertNull(tracer.getWorkflowSpan("missing", "unregistered-parent"));
        tracer.popWorkflowSpan("missing", "unregistered-parent");
        assertNull(tracer.getWorkflowSpan("missing", "unregistered-parent"));
    }

    @Test
    void unknownHandlerNameDoesNothing() {
        Tracer tracer = new Tracer();
        tracer.init(null);

        tracer.trigger("missing_handler", "on_invoke", Map.of(
                "invoke_id", "invoke-unknown",
                "on_invoke_data", Map.of("value", "ignored")
        ));

        assertNull(tracer.getWorkflowSpan("invoke-unknown", ""));
    }
}
