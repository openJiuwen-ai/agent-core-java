/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central tracer coordinating agent and workflow span managers.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.tracer.Tracer}.
 * 
 * @since 0.1.7
 */
public class Tracer {
    private final String traceId;
    private final SpanManager tracerAgentSpanManager;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, SpanManager> tracerWorkflowSpanManagerDict = new ConcurrentHashMap<>();
    private CallbackManager callbackManager;
    private StreamWriterManager streamWriterManager;

    /**
     * Tracer.
     * 
     * @since 0.1.7
     */
    public Tracer() {
        this.traceId = UUID.randomUUID().toString();
        this.tracerAgentSpanManager = new SpanManager(traceId);
    }

    /**
     * Initialize the tracer with stream and callback managers.
     * 
     * @param streamWriterManager the stream writer manager
     * @param callbackManager the callback manager
     * @since 0.1.7
     */
    public void init(StreamWriterManager streamWriterManager, CallbackManager callbackManager) {
        this.streamWriterManager = streamWriterManager;
        this.callbackManager = callbackManager;

        TraceAgentHandler agentHandler =
            new TraceAgentHandler(callbackManager, streamWriterManager, tracerAgentSpanManager);

        SpanManager parentWorkflowSpanManager = new SpanManager(traceId);
        TraceWorkflowHandler workflowHandler =
            new TraceWorkflowHandler(callbackManager, streamWriterManager, parentWorkflowSpanManager);

        tracerWorkflowSpanManagerDict.put("", parentWorkflowSpanManager);

        Map<String, com.openjiuwen.core.session.callback.BaseHandler> agentMap = new HashMap<>();
        agentMap.put(TracerHandlerName.TRACE_AGENT.getValue(), agentHandler);
        callbackManager.register(agentMap);

        Map<String, com.openjiuwen.core.session.callback.BaseHandler> workflowMap = new HashMap<>();
        workflowMap.put(TracerHandlerName.TRACER_WORKFLOW.getValue(), workflowHandler);
        callbackManager.register(workflowMap);

        // Inject traceId into externally registered extension handlers so they can
        // bridge OTel traces with the tracer UUID.
        for (TraceExtAgentHandler ext : TracerHandlerRegistry.getAgentHandlers().values()) {
            ext.setTraceId(traceId);
        }
        for (TraceExtWorkflowHandler ext : TracerHandlerRegistry.getWorkflowHandlers().values()) {
            ext.setTraceId(traceId);
        }
    }

    /**
     * Register a workflow span manager for a parent node.
     * 
     * @param parentNodeId the parent node ID
     * @since 0.1.7
     */
    public void registerWorkflowSpanManager(String parentNodeId) {
        SpanManager spanManager = new SpanManager(traceId, parentNodeId);
        tracerWorkflowSpanManagerDict.put(parentNodeId, spanManager);

        TraceWorkflowHandler handler = new TraceWorkflowHandler(callbackManager, streamWriterManager, spanManager);

        Map<String, com.openjiuwen.core.session.callback.BaseHandler> handlerMap = new HashMap<>();
        handlerMap.put(TracerHandlerName.TRACER_WORKFLOW.getValue() + "." + parentNodeId, handler);
        callbackManager.register(handlerMap);
    }

    /**
     * Get a workflow span by invoke ID and parent node ID.
     * 
     * @param invokeId invokeId
     * @param parentNodeId parentNodeId
     * @return the result
     * @since 0.1.7
     */
    public TraceWorkflowSpan getWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager manager = tracerWorkflowSpanManagerDict.get(parentNodeId);
        if (manager == null) {
            return null;
        }
        Span span = manager.getSpan(invokeId);
        return span instanceof TraceWorkflowSpan ? (TraceWorkflowSpan) span : null;
    }

    /**
     * Trigger a tracer event through the callback manager.
     * 
     * @param handlerClassName the handler class name
     * @param eventName the event name
     * @param kwargs the event arguments
     * @since 0.1.7
     */
    public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        String parentNodeId = kwargs != null ? (String) kwargs.get("parent_node_id") : null;
        if (parentNodeId != null && !parentNodeId.isEmpty()) {
            handlerClassName += "." + parentNodeId;
        }
        callbackManager.trigger(handlerClassName, eventName, kwargs);
    }

    /**
     * Pop (remove) a workflow span.
     * 
     * @param invokeId invokeId
     * @param parentNodeId parentNodeId
     * @since 0.1.7
     */
    public void popWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager manager = tracerWorkflowSpanManagerDict.get(parentNodeId);
        if (manager != null) {
            manager.popSpan(invokeId);
        }
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
     * getTracerAgentSpanManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SpanManager getTracerAgentSpanManager() {
        return tracerAgentSpanManager;
    }

    /**
     * getTracerWorkflowSpanManagerDict.
     *
     * @return the result
     * @since 0.1.7
     */
    public Map<String, SpanManager> getTracerWorkflowSpanManagerDict() {
        return tracerWorkflowSpanManagerDict;
    }

    /**
     * Update the stream writer manager reference.
     * <p>
     * Called when a session is reused across multiple invocations. The tracer is
     * preserved (maintaining trace_id consistency), but the stream writer manager
     * is recreated. This method updates the tracer's reference so that newly
     * registered handlers use the correct stream writer manager.
     * </p>
     *
     * @param streamWriterManager the new stream writer manager
     * @since 0.1.7
     */
    public void updateStreamWriterManager(StreamWriterManager streamWriterManager) {
        this.streamWriterManager = streamWriterManager;
    }

    /**
     * Clear all span managers, releasing all span references.
     * <p>
     * Called when the owning session is cleaned up to prevent span
     * accumulation across sessions. After clearing, the tracer should
     * not be reused for new invocations.
     * </p>
     *
     * @since 0.1.15
     */
    public void clear() {
        tracerAgentSpanManager.clear();
        for (SpanManager manager : tracerWorkflowSpanManagerDict.values()) {
            manager.clear();
        }
        tracerWorkflowSpanManagerDict.clear();
    }
}
