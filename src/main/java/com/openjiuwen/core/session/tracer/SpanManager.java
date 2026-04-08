/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages spans during a tracer session. Maintains ordered collection of spans.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.SpanManager}.
 */
public class SpanManager {

    private final String traceId;
    private final String parentNodeId;
    private final List<String> order = new ArrayList<>();
    private final Map<String, Span> sessionSpans = new ConcurrentHashMap<>();

    public SpanManager(String traceId) {
        this(traceId, "");
    }

    public SpanManager(String traceId, String parentNodeId) {
        this.traceId = traceId;
        this.parentNodeId = parentNodeId != null ? parentNodeId : "";
    }

    /**
     * Get a span by invoke ID.
     *
     * @param invokeId the invoke ID
     * @return the span, or null if not found
     */
    public Span getSpan(String invokeId) {
        if (!order.contains(invokeId)) {
            return null;
        }
        return sessionSpans.get(invokeId);
    }

    /**
     * Remove a span by invoke ID.
     */
    public void popSpan(String invokeId) {
        order.remove(invokeId);
        sessionSpans.remove(invokeId);
    }

    /**
     * Add or update a span record.
     */
    public void refreshSpanRecord(String invokeId, Span span) {
        if (!order.contains(invokeId)) {
            order.add(invokeId);
        }
        sessionSpans.put(invokeId, span);
    }

    /**
     * Create an agent span with optional parent.
     */
    public TraceAgentSpan createAgentSpan(Span parentSpan) {
        String invokeId = UUID.randomUUID().toString();
        String parentInvokeId = parentSpan != null ? parentSpan.getInvokeId() : null;
        TraceAgentSpan span = new TraceAgentSpan(traceId, invokeId, parentInvokeId);

        refreshParentChildSpan(span, parentSpan);
        return span;
    }

    /**
     * Create a workflow span with explicit invoke ID and optional parent.
     */
    public TraceWorkflowSpan createWorkflowSpan(String invokeId, Span parentSpan) {
        String parentInvokeId = parentSpan != null ? parentSpan.getInvokeId() : null;
        TraceWorkflowSpan span = new TraceWorkflowSpan(traceId, invokeId, parentInvokeId, parentNodeId);

        refreshParentChildSpan(span, parentSpan);
        return span;
    }

    /**
     * Update a span with data and refresh it in the record.
     */
    public void updateSpan(Span span, Map<String, Object> data) {
        span.update(data);
        refreshSpanRecord(span.getInvokeId(), span);
    }

    /**
     * Get the last span in order.
     */
    public Span getLastSpan() {
        if (order.isEmpty()) {
            return null;
        }
        String lastId = order.get(order.size() - 1);
        return sessionSpans.get(lastId);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getParentNodeId() {
        return parentNodeId;
    }

    private void refreshParentChildSpan(Span span, Span parentSpan) {
        if (parentSpan != null) {
            parentSpan.appendChildInvokeId(span.getInvokeId());
            refreshSpanRecord(parentSpan.getInvokeId(), parentSpan);
        }
        refreshSpanRecord(span.getInvokeId(), span);
    }
}
