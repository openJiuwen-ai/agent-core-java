/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.callback.BaseHandler;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tracer for managing trace spans during session execution.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class Tracer {
    
    private final String traceId;
    private final SpanManager tracerAgentSpanManager;
    private final Map<String, SpanManager> tracerWorkflowSpanManagerDict = new HashMap<>();
    private CallbackManager callbackManager;
    private StreamWriterManager streamWriterManager;
    
    /**
     * Creates a new Tracer.
     */
    public Tracer() {
        this.traceId = UUID.randomUUID().toString();
        this.tracerAgentSpanManager = new SpanManager(traceId);
    }
    
    /**
     * Initializes the tracer with stream writer and callback managers.
     * 
     * @param streamWriterManager the stream writer manager
     * @param callbackManager the callback manager
     */
    public void init(StreamWriterManager streamWriterManager, CallbackManager callbackManager) {
        // Create trace agent handler
        TraceAgentHandler traceAgentHandler = new TraceAgentHandler(callbackManager, streamWriterManager, 
                                                                     tracerAgentSpanManager);
        
        // Create parent workflow span manager and handler
        SpanManager parentTracerWorkflowSpanManager = new SpanManager(traceId);
        TraceWorkflowHandler traceWorkflowHandler = new TraceWorkflowHandler(callbackManager, streamWriterManager, 
                                                                              parentTracerWorkflowSpanManager);
        
        tracerWorkflowSpanManagerDict.put("", parentTracerWorkflowSpanManager);
        
        // Register handlers
        Map<String, BaseHandler> agentHandlerMap = new HashMap<>();
        agentHandlerMap.put(TracerHandlerName.TRACE_AGENT.getValue(), traceAgentHandler);
        callbackManager.register(agentHandlerMap);
        
        Map<String, BaseHandler> workflowHandlerMap = new HashMap<>();
        workflowHandlerMap.put(TracerHandlerName.TRACER_WORKFLOW.getValue(), traceWorkflowHandler);
        callbackManager.register(workflowHandlerMap);
        
        this.callbackManager = callbackManager;
        this.streamWriterManager = streamWriterManager;
    }
    
    /**
     * Registers a workflow span manager for a parent node.
     * 
     * @param parentNodeId the parent node ID
     */
    public void registerWorkflowSpanManager(String parentNodeId) {
        SpanManager tracerWorkflowSpanManager = new SpanManager(traceId, parentNodeId);
        tracerWorkflowSpanManagerDict.put(parentNodeId, tracerWorkflowSpanManager);
        
        // Create and register handler for this workflow span manager
        TraceWorkflowHandler traceWorkflowHandler = new TraceWorkflowHandler(callbackManager, streamWriterManager, 
                                                                              tracerWorkflowSpanManager);
        
        Map<String, BaseHandler> handlerMap = new HashMap<>();
        handlerMap.put(TracerHandlerName.TRACER_WORKFLOW.getValue() + "." + parentNodeId, traceWorkflowHandler);
        callbackManager.register(handlerMap);
    }
    
    /**
     * Gets a workflow span by invoke ID and parent node ID.
     * 
     * @param invokeId the invoke ID
     * @param parentNodeId the parent node ID
     * @return the span, or null if not found
     */
    public Span getWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager workflowSpanManager = tracerWorkflowSpanManagerDict.get(parentNodeId);
        if (workflowSpanManager == null) {
            return null;
        }
        return workflowSpanManager.getSpan(invokeId);
    }
    
    /**
     * Triggers an event on a handler.
     * 
     * @param handlerClassName the handler class name
     * @param eventName the event name
     * @param kwargs the event arguments
     * @return a CompletableFuture that completes when the event is triggered
     */
    public CompletableFuture<Void> trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        String parentNodeId = kwargs != null ? (String) kwargs.get("parent_node_id") : null;
        if (parentNodeId != null && !parentNodeId.isEmpty()) {
            handlerClassName = handlerClassName + "." + parentNodeId;
        }
        
        if (callbackManager != null) {
            return callbackManager.trigger(handlerClassName, eventName, kwargs);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Synchronously triggers an event on a handler.
     * 
     * @param handlerClassName the handler class name
     * @param eventName the event name
     * @param kwargs the event arguments
     */
    public void syncTrigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        trigger(handlerClassName, eventName, kwargs).join();
    }
    
    /**
     * Removes a workflow span by invoke ID and parent node ID.
     * 
     * @param invokeId the invoke ID
     * @param parentNodeId the parent node ID
     */
    public void popWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager manager = tracerWorkflowSpanManagerDict.get(parentNodeId);
        if (manager != null) {
            manager.popSpan(invokeId);
        }
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
     * Gets the agent span manager.
     * 
     * @return the agent span manager
     */
    public SpanManager getTracerAgentSpanManager() {
        return tracerAgentSpanManager;
    }
    
    /**
     * Gets the workflow span manager dictionary.
     * 
     * @return the workflow span manager dictionary
     */
    public Map<String, SpanManager> getTracerWorkflowSpanManagerDict() {
        return tracerWorkflowSpanManagerDict;
    }
    
    /**
     * Gets the callback manager.
     * 
     * @return the callback manager
     */
    public CallbackManager getCallbackManager() {
        return callbackManager;
    }
    
    /**
     * Gets the stream writer manager.
     * 
     * @return the stream writer manager
     */
    public StreamWriterManager getStreamWriterManager() {
        return streamWriterManager;
    }
}

