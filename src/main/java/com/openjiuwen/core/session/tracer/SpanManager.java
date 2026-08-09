/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mirrors Python's {@code SpanManager} in
 * {@code openjiuwen/core/session/tracer/span.py}.
 */
public class SpanManager {
    private final String traceId;
    private final String parentNodeId;
    private final CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
    private final Map<String, Span> sessionSpans = new ConcurrentHashMap<>();

    public SpanManager(String traceId) {
        this(traceId, "");
    }

    public SpanManager(String traceId, String parentNodeId) {
        this.traceId = traceId;
        this.parentNodeId = parentNodeId == null ? "" : parentNodeId;
    }

    public Span getSpan(String invokeId) {
        if (!order.contains(invokeId)) {
            return null;
        }
        return sessionSpans.get(invokeId);
    }

    public void popSpan(String invokeId) {
        if (!order.contains(invokeId)) {
            return;
        }
        order.remove(invokeId);
        sessionSpans.remove(invokeId);
    }

    public void refreshSpanRecord(String invokeId, Span span) {
        order.addIfAbsent(invokeId);
        sessionSpans.put(invokeId, span);
    }

    public void refreshSpanRecord(String invokeId, Map<String, ? extends Span> sessionSpan) {
        if (sessionSpan == null || !sessionSpan.containsKey(invokeId)) {
            return;
        }
        refreshSpanRecord(invokeId, sessionSpan.get(invokeId));
    }

    public TraceAgentSpan createAgentSpan() {
        return createAgentSpan(null);
    }

    public TraceAgentSpan createAgentSpan(TraceAgentSpan parentSpan) {
        String invokeId = UUID.randomUUID().toString();
        TraceAgentSpan span = new TraceAgentSpan(
                traceId,
                invokeId,
                parentSpan == null ? null : parentSpan.getInvokeId()
        );
        refreshParentChildSpan(span, parentSpan);
        return span;
    }

    public TraceWorkflowSpan createWorkflowSpan(String invokeId) {
        return createWorkflowSpan(invokeId, null);
    }

    public TraceWorkflowSpan createWorkflowSpan(String invokeId, TraceWorkflowSpan parentSpan) {
        TraceWorkflowSpan span = new TraceWorkflowSpan(
                traceId,
                invokeId,
                parentSpan == null ? null : parentSpan.getInvokeId(),
                parentNodeId
        );
        refreshParentChildSpan(span, parentSpan);
        return span;
    }

    public void updateSpan(Span span, Map<String, Object> data) {
        span.update(data);
        refreshSpanRecord(span.getInvokeId(), span);
    }

    public void endSpan() {
    }

    public Span getLastSpan() {
        String[] snapshot = order.toArray(new String[0]);
        if (snapshot.length == 0) {
            return null;
        }
        String lastSpanId = snapshot[snapshot.length - 1];
        return sessionSpans.get(lastSpanId);
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
