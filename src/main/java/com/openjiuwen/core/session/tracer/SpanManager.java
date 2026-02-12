/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages spans during tracer handler session.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class SpanManager {
    
    private final String traceId;
    private final String parentNodeId;
    private final List<String> order = new ArrayList<>();
    private final Map<String, Span> sessionSpans = new HashMap<>();
    
    /**
     * Creates a new SpanManager.
     * 
     * @param traceId the trace ID
     */
    public SpanManager(String traceId) {
        this(traceId, "");
    }
    
    /**
     * Creates a new SpanManager with parent node ID.
     * 
     * @param traceId the trace ID
     * @param parentNodeId the parent node ID
     */
    public SpanManager(String traceId, String parentNodeId) {
        this.traceId = traceId;
        this.parentNodeId = parentNodeId != null ? parentNodeId : "";
    }
    
    /**
     * Gets a span by invoke ID.
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
     * Removes a span by invoke ID.
     * 
     * @param invokeId the invoke ID
     */
    public void popSpan(String invokeId) {
        if (!order.contains(invokeId)) {
            return;
        }
        order.remove(invokeId);
        sessionSpans.remove(invokeId);
    }
    
    /**
     * Refreshes a span record.
     * 
     * @param invokeId the invoke ID
     * @param sessionSpan the span to refresh
     */
    public void refreshSpanRecord(String invokeId, Span sessionSpan) {
        if (!order.contains(invokeId)) {
            order.add(invokeId);
        }
        sessionSpans.put(invokeId, sessionSpan);
    }
    
    /**
     * Refreshes parent-child span relationship.
     */
    private void refreshParentChildSpan(Span span, Span parentSpan) {
        if (parentSpan != null) {
            parentSpan.appendChildInvokeId(span.getInvokeId());
            refreshSpanRecord(parentSpan.getInvokeId(), parentSpan);
        }
        refreshSpanRecord(span.getInvokeId(), span);
    }
    
    /**
     * Creates a new agent span.
     * 
     * @param parentSpan the parent span, or null
     * @return the new agent span
     */
    public TraceAgentSpan createAgentSpan(TraceAgentSpan parentSpan) {
        String invokeId = UUID.randomUUID().toString();
        TraceAgentSpan span = new TraceAgentSpan(traceId);
        span.setInvokeId(invokeId);
        span.setParentInvokeId(parentSpan != null ? parentSpan.getInvokeId() : null);
        
        refreshParentChildSpan(span, parentSpan);
        return span;
    }
    
    /**
     * Creates a new agent span without parent.
     * 
     * @return the new agent span
     */
    public TraceAgentSpan createAgentSpan() {
        return createAgentSpan(null);
    }
    
    /**
     * Creates a new workflow span.
     * 
     * @param invokeId the invoke ID
     * @param parentSpan the parent span, or null
     * @return the new workflow span
     */
    public TraceWorkflowSpan createWorkflowSpan(String invokeId, TraceWorkflowSpan parentSpan) {
        TraceWorkflowSpan span = new TraceWorkflowSpan(traceId);
        span.setInvokeId(invokeId);
        span.setParentInvokeId(parentSpan != null ? parentSpan.getInvokeId() : null);
        span.setParentNodeId(parentNodeId);
        span.setExecutionId(traceId);
        
        refreshParentChildSpan(span, parentSpan);
        return span;
    }
    
    /**
     * Creates a new workflow span without parent.
     * 
     * @param invokeId the invoke ID
     * @return the new workflow span
     */
    public TraceWorkflowSpan createWorkflowSpan(String invokeId) {
        return createWorkflowSpan(invokeId, null);
    }
    
    /**
     * Updates a span with data.
     * 
     * @param span the span to update
     * @param data the data to update with
     */
    public void updateSpan(Span span, Map<String, Object> data) {
        span.update(data);
        refreshSpanRecord(span.getInvokeId(), span);
    }
    
    /**
     * Ends the current span.
     */
    public void endSpan() {
        // No-op
    }
    
    /**
     * Gets the last span in the order.
     * 
     * @return the last span, or null if empty
     */
    public Span getLastSpan() {
        if (order.isEmpty()) {
            return null;
        }
        String lastSpanId = order.get(order.size() - 1);
        return sessionSpans.get(lastSpanId);
    }
    
    /**
     * Gets the trace ID.
     * 
     * @return the trace ID
     */
    public String getTraceId() {
        return traceId;
    }
    
    /**
     * Gets the parent node ID.
     * 
     * @return the parent node ID
     */
    public String getParentNodeId() {
        return parentNodeId;
    }
}

