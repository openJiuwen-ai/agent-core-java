/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for tracing agent execution events.
 * 
 * <p>Handles start, end, error, and running events for various agent invoke types
 * including chain, llm, prompt, plugin, retriever, evaluator, and workflow.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TraceAgentHandler extends TraceBaseHandler {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Creates a new TraceAgentHandler.
     * 
     * @param owner the owner of this handler
     * @param streamWriterManager the stream writer manager
     * @param spanManager the span manager
     */
    public TraceAgentHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner, streamWriterManager, spanManager);
    }
    
    @Override
    public String eventName() {
        return TracerHandlerName.TRACE_AGENT.getValue();
    }
    
    @Override
    protected Map<String, Object> formatData(Span span) {
        if (span instanceof TraceAgentSpan agentSpan) {
            agentSpan.setStatus(getNodeStatus(agentSpan));
            Map<String, Object> result = new HashMap<>();
            result.put("type", eventName());
            result.put("payload", spanToMap(agentSpan));
            return result;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("type", eventName());
        result.put("payload", span);
        return result;
    }
    
    /**
     * Gets or creates a tracer agent span.
     * 
     * @param invokeId the invoke ID
     * @return the trace agent span
     */
    public TraceAgentSpan getTracerAgentSpan(String invokeId) {
        Span span = spanManager.getSpan(invokeId);
        if (span instanceof TraceAgentSpan agentSpan) {
            return agentSpan;
        }
        Span lastSpan = spanManager.getLastSpan();
        TraceAgentSpan parentSpan = lastSpan instanceof TraceAgentSpan ? (TraceAgentSpan) lastSpan : null;
        return spanManager.createAgentSpan(parentSpan);
    }
    
    /**
     * Updates span with start trace data.
     */
    private void updateStartTraceData(TraceAgentSpan span, String invokeType, Object inputs, 
                                       Map<String, Object> instanceInfo) {
        Map<String, Object> metaData;
        try {
            String json = objectMapper.writeValueAsString(instanceInfo);
            metaData = objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("meta_data process error");
            throw new IllegalArgumentException("meta_data error: Decoder error", e);
        }
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("startTime", Instant.now());
        updateData.put("invokeType", invokeType);
        updateData.put("inputs", inputs);
        updateData.put("name", instanceInfo.get("class_name"));
        updateData.put("metaData", metaData);
        
        spanManager.updateSpan(span, updateData);
    }
    
    /**
     * Updates span with end trace data.
     */
    private void updateEndTraceData(TraceAgentSpan span, Object outputs) {
        Instant endTime = Instant.now();
        String elapsedTime = span.getStartTime() != null ? getElapsedTime(span.getStartTime(), endTime) : null;
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("endTime", endTime);
        updateData.put("outputs", outputs);
        if (elapsedTime != null) {
            updateData.put("elapsedTime", elapsedTime);
        }
        
        spanManager.updateSpan(span, updateData);
    }
    
    /**
     * Updates span with error trace data.
     */
    private void updateErrorTraceData(TraceAgentSpan span, Exception error) {
        Instant endTime = Instant.now();
        Map<String, Object> errorInfo = new HashMap<>();
        
        if (error instanceof JiuWenBaseException jiuWenError) {
            errorInfo.put("error_code", jiuWenError.getErrorCode());
            errorInfo.put("message", jiuWenError.getErrorMessage());
        } else {
            errorInfo.put("error_code", StatusCode.ERROR.getCode());
            errorInfo.put("message", error.getMessage());
        }
        
        String elapsedTime = span.getStartTime() != null ? getElapsedTime(span.getStartTime(), endTime) : null;
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("endTime", endTime);
        updateData.put("error", errorInfo);
        if (elapsedTime != null) {
            updateData.put("elapsedTime", elapsedTime);
        }
        
        spanManager.updateSpan(span, updateData);
    }
    
    /**
     * Updates span with running trace data.
     */
    private void updateRunningTraceData(TraceAgentSpan span, Map<String, Object> kwargs) {
        List<Map<String, Object>> onInvokeData = span.getOnInvokeData();
        if (onInvokeData == null) {
            onInvokeData = new ArrayList<>();
            span.setOnInvokeData(onInvokeData);
        }
        onInvokeData.add(kwargs);
        
        spanManager.updateSpan(span, new HashMap<>());
    }
    
    /**
     * Converts TraceAgentSpan to a Map with alias names.
     */
    private Map<String, Object> spanToMap(TraceAgentSpan span) {
        Map<String, Object> result = new HashMap<>();
        result.put("traceId", span.getTraceId());
        if (span.getStartTime() != null) {
            result.put("startTime", span.getStartTime().toString());
        }
        if (span.getEndTime() != null) {
            result.put("endTime", span.getEndTime().toString());
        }
        if (span.getInputs() != null) {
            result.put("inputs", span.getInputs());
        }
        if (span.getOutputs() != null) {
            result.put("outputs", span.getOutputs());
        }
        if (span.getError() != null) {
            result.put("error", span.getError());
        }
        if (span.getInvokeId() != null) {
            result.put("invokeId", span.getInvokeId());
        }
        if (span.getParentInvokeId() != null) {
            result.put("parentInvokeId", span.getParentInvokeId());
        }
        if (span.getChildInvokesId() != null) {
            result.put("childInvokes", span.getChildInvokesId());
        }
        if (span.getStatus() != null) {
            result.put("status", span.getStatus());
        }
        if (span.getOnInvokeData() != null) {
            result.put("onInvokeData", span.getOnInvokeData());
        }
        if (span.getInvokeType() != null) {
            result.put("invokeType", span.getInvokeType());
        }
        if (span.getName() != null) {
            result.put("name", span.getName());
        }
        if (span.getElapsedTime() != null) {
            result.put("elapsedTime", span.getElapsedTime());
        }
        if (span.getMetaData() != null) {
            result.put("metaData", span.getMetaData());
        }
        return result;
    }
    
    // ==================== Chain Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.CHAIN.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onChainEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onChainError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
    
    // ==================== LLM Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.LLM.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onLlmRequest(TraceAgentSpan span, Map<String, Object> kwargs) {
        updateRunningTraceData(span, kwargs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onLlmEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onLlmError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
    
    // ==================== Prompt Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PROMPT.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPromptEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPromptError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
    
    // ==================== Plugin Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.PLUGIN.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPluginEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPluginError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
    
    // ==================== Retriever Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.RETRIEVER.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onRetrieverEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onRetrieverError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
    
    // ==================== Evaluator Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.EVALUATOR.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onEvaluatorEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onEvaluatorError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
    
    // ==================== Workflow Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo) {
        updateStartTraceData(span, InvokeType.WORKFLOW.getValue(), inputs, instanceInfo);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onWorkflowEnd(TraceAgentSpan span, Object outputs) {
        updateEndTraceData(span, outputs);
        return sendData(span);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onWorkflowError(TraceAgentSpan span, Exception error) {
        updateErrorTraceData(span, error);
        return sendData(span);
    }
}

