/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages spans during a tracer session. Maintains ordered collection of spans.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.SpanManager}.
 * 
 * @since 0.1.7
 */
public class SpanManager {
    private final String traceId;
    private final String parentNodeId;

    /**
     * CopyOnWriteArrayList for thread-safe ordered span tracking.
     * 
     * @since 0.1.7
     */
    private final CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Span> sessionSpans = new ConcurrentHashMap<>();

    /**
     * SpanManager.
     * 
     * @param traceId traceId
     * @since 0.1.7
     */
    public SpanManager(String traceId) {
        this(traceId, "");
    }

    /**
     * SpanManager.
     * 
     * @param traceId traceId
     * @param parentNodeId parentNodeId
     * @since 0.1.7
     */
    public SpanManager(String traceId, String parentNodeId) {
        this.traceId = traceId;
        this.parentNodeId = parentNodeId != null ? parentNodeId : "";
    }

    /**
     * Get a span by invoke ID.
     * 
     * @param invokeId the invoke ID
     * @return the span, or null if not found
     * @since 0.1.7
     */
    public Span getSpan(String invokeId) {
        if (!order.contains(invokeId)) {
            return null;
        }
        return sessionSpans.get(invokeId);
    }

    /**
     * Remove a span by invoke ID.
     * 
     * @param invokeId invokeId
     * @since 0.1.7
     */
    public void popSpan(String invokeId) {
        order.remove(invokeId);
        sessionSpans.remove(invokeId);
    }

    /**
     * Clear all spans from this manager.
     * <p>
     * Called when the owning session is cleaned up to release all span
     * references and prevent accumulation across sessions.
     * </p>
     *
     * @since 0.1.15
     */
    public void clear() {
        order.clear();
        sessionSpans.clear();
    }

    /**
     * Add or update a span record.
     * 
     * @param invokeId invokeId
     * @param span span
     * @since 0.1.7
     */
    public void refreshSpanRecord(String invokeId, Span span) {
        order.addIfAbsent(invokeId);
        sessionSpans.put(invokeId, span);
    }

    /**
     * Create an agent span with optional parent.
     * 
     * @param parentSpan parentSpan
     * @return the result
     * @since 0.1.7
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
     * 
     * @param invokeId invokeId
     * @param parentSpan parentSpan
     * @return the result
     * @since 0.1.7
     */
    public TraceWorkflowSpan createWorkflowSpan(String invokeId, Span parentSpan) {
        String parentInvokeId = parentSpan != null ? parentSpan.getInvokeId() : null;
        TraceWorkflowSpan span = new TraceWorkflowSpan(traceId, invokeId, parentInvokeId, parentNodeId);

        refreshParentChildSpan(span, parentSpan);
        return span;
    }

    /**
     * Update a span with data and refresh it in the record.
     * 
     * @param span span
     * @param data data
     * @since 0.1.7
     */
    public void updateSpan(Span span, Map<String, Object> data) {
        span.update(data);
        refreshSpanRecord(span.getInvokeId(), span);
    }

    /**
     * Get the last span in order.
     *
     * @return the result
     * @since 0.1.7
     */
    public Span getLastSpan() {
        String[] snapshot = order.toArray(new String[0]);
        if (snapshot.length == 0) {
            return null;
        }
        String lastId = snapshot[snapshot.length - 1];
        return sessionSpans.get(lastId);
    }

    /**
     * getTraceId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * getParentNodeId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getParentNodeId() {
        return parentNodeId;
    }

    /**
     * refreshParentChildSpan.
     * 
     * @param span span
     * @param parentSpan parentSpan
     * @since 0.1.7
     */
    private void refreshParentChildSpan(Span span, Span parentSpan) {
        if (parentSpan != null) {
            parentSpan.appendChildInvokeId(span.getInvokeId());
            refreshSpanRecord(parentSpan.getInvokeId(), parentSpan);
        }
        refreshSpanRecord(span.getInvokeId(), span);
    }
}
