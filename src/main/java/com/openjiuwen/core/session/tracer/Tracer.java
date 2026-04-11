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
 */
public class Tracer {

    private final String traceId;
    private final SpanManager tracerAgentSpanManager;
    private final Map<String, SpanManager> tracerWorkflowSpanManagerDict = new ConcurrentHashMap<>();
    private CallbackManager callbackManager;
    private StreamWriterManager streamWriterManager;

    public Tracer() {
        this.traceId = UUID.randomUUID().toString();
        this.tracerAgentSpanManager = new SpanManager(traceId);
    }

    /**
     * Initialize the tracer with stream and callback managers.
     *
     * @param streamWriterManager the stream writer manager
     * @param callbackManager     the callback manager
     */
    public void init(StreamWriterManager streamWriterManager, CallbackManager callbackManager) {
        this.streamWriterManager = streamWriterManager;
        this.callbackManager = callbackManager;

        TraceAgentHandler agentHandler = new TraceAgentHandler(
                callbackManager, streamWriterManager, tracerAgentSpanManager);

        SpanManager parentWorkflowSpanManager = new SpanManager(traceId);
        TraceWorkflowHandler workflowHandler = new TraceWorkflowHandler(
                callbackManager, streamWriterManager, parentWorkflowSpanManager);

        tracerWorkflowSpanManagerDict.put("", parentWorkflowSpanManager);

        Map<String, com.openjiuwen.core.session.callback.BaseHandler> agentMap = new HashMap<>();
        agentMap.put(TracerHandlerName.TRACE_AGENT.getValue(), agentHandler);
        callbackManager.register(agentMap);

        Map<String, com.openjiuwen.core.session.callback.BaseHandler> workflowMap = new HashMap<>();
        workflowMap.put(TracerHandlerName.TRACER_WORKFLOW.getValue(), workflowHandler);
        callbackManager.register(workflowMap);
    }

    /**
     * Register a workflow span manager for a parent node.
     *
     * @param parentNodeId the parent node ID
     */
    public void registerWorkflowSpanManager(String parentNodeId) {
        SpanManager spanManager = new SpanManager(traceId, parentNodeId);
        tracerWorkflowSpanManagerDict.put(parentNodeId, spanManager);

        TraceWorkflowHandler handler = new TraceWorkflowHandler(
                callbackManager, streamWriterManager, spanManager);

        Map<String, com.openjiuwen.core.session.callback.BaseHandler> handlerMap = new HashMap<>();
        handlerMap.put(TracerHandlerName.TRACER_WORKFLOW.getValue() + "." + parentNodeId, handler);
        callbackManager.register(handlerMap);
    }

    /**
     * Get a workflow span by invoke ID and parent node ID.
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
     * @param eventName        the event name
     * @param kwargs           the event arguments
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
     */
    public void popWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager manager = tracerWorkflowSpanManagerDict.get(parentNodeId);
        if (manager != null) {
            manager.popSpan(invokeId);
        }
    }

    public String getTraceId() {
        return traceId;
    }

    public SpanManager getTracerAgentSpanManager() {
        return tracerAgentSpanManager;
    }

    public Map<String, SpanManager> getTracerWorkflowSpanManagerDict() {
        return tracerWorkflowSpanManagerDict;
    }
}
