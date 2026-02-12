/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.callback.TriggerEvent;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for tracing workflow execution events.
 * 
 * <p>Handles call start/done, pre/post invoke, and stream events for workflow components.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TraceWorkflowHandler extends TraceBaseHandler {
    
    /**
     * Creates a new TraceWorkflowHandler.
     * 
     * @param owner the owner of this handler
     * @param streamWriterManager the stream writer manager
     * @param spanManager the span manager
     */
    public TraceWorkflowHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager) {
        super(owner, streamWriterManager, spanManager);
    }
    
    @Override
    public String eventName() {
        return TracerHandlerName.TRACER_WORKFLOW.getValue();
    }
    
    @Override
    protected Map<String, Object> formatData(Span span) {
        if (span instanceof TraceWorkflowSpan workflowSpan) {
            workflowSpan.setStatus(getNodeStatus(workflowSpan));
            Map<String, Object> result = new HashMap<>();
            result.put("type", eventName());
            result.put("payload", spanToMap(workflowSpan));
            return result;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("type", eventName());
        result.put("payload", span);
        return result;
    }
    
    /**
     * Gets or creates a tracer workflow span.
     * 
     * @param invokeId the invoke ID
     * @return the trace workflow span
     */
    public TraceWorkflowSpan getTracerWorkflowSpan(String invokeId) {
        Span span = spanManager.getSpan(invokeId);
        if (span instanceof TraceWorkflowSpan workflowSpan) {
            return workflowSpan;
        }
        Span lastSpan = spanManager.getLastSpan();
        TraceWorkflowSpan parentSpan = lastSpan instanceof TraceWorkflowSpan ? (TraceWorkflowSpan) lastSpan : null;
        return spanManager.createWorkflowSpan(invokeId, parentSpan);
    }
    
    /**
     * Converts TraceWorkflowSpan to a Map with alias names, excluding certain fields.
     */
    private Map<String, Object> spanToMap(TraceWorkflowSpan span) {
        Map<String, Object> result = new HashMap<>();
        
        // Base Span fields
        if (span.getTraceId() != null) {
            result.put("traceId", span.getTraceId());
        }
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
        // Exclude childInvokesId and llmInvokeData as per Python implementation
        if (span.getStatus() != null) {
            result.put("status", span.getStatus());
        }
        if (span.getOnInvokeData() != null) {
            result.put("onInvokeData", span.getOnInvokeData());
        }
        
        // TraceWorkflowSpan specific fields
        if (span.getExecutionId() != null) {
            result.put("executionId", span.getExecutionId());
        }
        if (span.getSourceIds() != null) {
            result.put("sourceIds", span.getSourceIds());
        }
        if (span.getWorkflowId() != null) {
            result.put("workflowId", span.getWorkflowId());
        }
        if (span.getWorkflowVersion() != null) {
            result.put("workflowVersion", span.getWorkflowVersion());
        }
        if (span.getWorkflowName() != null) {
            result.put("workflowName", span.getWorkflowName());
        }
        if (span.getComponentId() != null) {
            result.put("componentId", span.getComponentId());
        }
        if (span.getComponentName() != null) {
            result.put("componentName", span.getComponentName());
        }
        if (span.getComponentType() != null) {
            result.put("componentType", span.getComponentType());
        }
        if (span.getLoopNodeId() != null) {
            result.put("loopNodeId", span.getLoopNodeId());
        }
        if (span.getLoopIndex() != null) {
            result.put("loopIndex", span.getLoopIndex());
        }
        if (span.getParentNodeId() != null) {
            result.put("parentNodeId", span.getParentNodeId());
        }
        if (span.getStreamInputs() != null) {
            result.put("streamInputs", span.getStreamInputs());
        }
        if (span.getStreamOutputs() != null) {
            result.put("streamOutputs", span.getStreamOutputs());
        }
        
        return result;
    }
    
    /**
     * Sends span data excluding outputs and streamOutputs fields.
     */
    private CompletableFuture<Void> sendDataExcludeOutputs(TraceWorkflowSpan span) {
        // Create a copy of the span data excluding outputs and streamOutputs
        Map<String, Object> data = spanToMap(span);
        data.remove("outputs");
        data.remove("streamOutputs");
        
        if (streamWriter == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        span.setStatus(getNodeStatus(span));
        Map<String, Object> result = new HashMap<>();
        result.put("type", eventName());
        result.put("payload", data);
        
        return streamWriter.write(result);
    }
    
    // ==================== Call Events ====================
    
    @TriggerEvent
    public CompletableFuture<Void> onCallStart(String invokeId, Map<String, Object> metadata, Object inputs,
                                               boolean needSend, List<String> sourceIds) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("startTime", Instant.now());
        updateData.put("onInvokeData", new ArrayList<>());
        updateData.put("inputs", inputs);
        updateData.put("outputs", null);
        updateData.put("streamOutputs", new ArrayList<>());
        updateData.put("sourceIds", sourceIds);
        
        // Merge metadata into updateData
        if (metadata != null) {
            // Handle workflow-specific fields from metadata
            if (metadata.containsKey("workflow_id")) {
                updateData.put("workflowId", metadata.get("workflow_id"));
            }
            if (metadata.containsKey("workflow_version")) {
                updateData.put("workflowVersion", metadata.get("workflow_version"));
            }
            if (metadata.containsKey("workflow_name")) {
                updateData.put("workflowName", metadata.get("workflow_name"));
            }
            if (metadata.containsKey("component_id")) {
                updateData.put("componentId", metadata.get("component_id"));
            }
            if (metadata.containsKey("component_name")) {
                updateData.put("componentName", metadata.get("component_name"));
            }
            if (metadata.containsKey("component_type")) {
                updateData.put("componentType", metadata.get("component_type"));
            }
            if (metadata.containsKey("loop_node_id")) {
                updateData.put("loopNodeId", metadata.get("loop_node_id"));
            }
            if (metadata.containsKey("loop_index")) {
                updateData.put("loopIndex", metadata.get("loop_index"));
            }
        }
        
        spanManager.updateSpan(span, updateData);
        
        if (needSend) {
            return sendData(span);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                                               boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("inputs", inputs);
        
        // Merge component metadata
        if (componentMetadata != null) {
            if (componentMetadata.containsKey("component_id")) {
                updateData.put("componentId", componentMetadata.get("component_id"));
            }
            if (componentMetadata.containsKey("component_name")) {
                updateData.put("componentName", componentMetadata.get("component_name"));
            }
            if (componentMetadata.containsKey("component_type")) {
                updateData.put("componentType", componentMetadata.get("component_type"));
            }
            if (componentMetadata.containsKey("workflow_id")) {
                updateData.put("workflowId", componentMetadata.get("workflow_id"));
            }
            if (componentMetadata.containsKey("loop_node_id")) {
                updateData.put("loopNodeId", componentMetadata.get("loop_node_id"));
            }
            if (componentMetadata.containsKey("loop_index")) {
                updateData.put("loopIndex", componentMetadata.get("loop_index"));
            }
        }
        
        spanManager.updateSpan(span, updateData);
        
        if (needSend) {
            return sendDataExcludeOutputs(span);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPreStream(String invokeId, Object chunk, boolean needSend) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        
        if (chunk instanceof Map) {
            span.appendStreamInputs(chunk);
        }
        
        if (needSend) {
            return sendDataExcludeOutputs(span);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onInvoke(String invokeId, Map<String, Object> onInvokeData, Exception exception) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        Map<String, Object> updateData = new HashMap<>();
        
        Instant endTime = Instant.now();
        
        if (exception != null) {
            // Handle GraphInterrupt - return early without updating error
            if (exception instanceof GraphInterrupt) {
                return CompletableFuture.completedFuture(null);
            }
            
            Map<String, Object> errorInfo = new HashMap<>();
            if (exception instanceof JiuWenBaseException jiuWenError) {
                errorInfo.put("error_code", jiuWenError.getErrorCode());
                errorInfo.put("message", jiuWenError.getErrorMessage());
            } else {
                errorInfo.put("error_code", StatusCode.WORKFLOW_EXECUTION_RUNTIME_ERROR.getCode());
                errorInfo.put("message", StatusCode.WORKFLOW_EXECUTION_RUNTIME_ERROR.formatMessage(
                    Map.of("error_msg", exception.getMessage() != null ? exception.getMessage() : "")));
            }
            span.setError(errorInfo);
            
            if (onInvokeData != null) {
                List<Map<String, Object>> invokeDataList = span.getOnInvokeData();
                if (invokeDataList == null) {
                    invokeDataList = new ArrayList<>();
                    span.setOnInvokeData(invokeDataList);
                }
                invokeDataList.add(onInvokeData);
            }
            
            updateData.put("endTime", endTime);
            String elapsedTime = span.getStartTime() != null ? getElapsedTime(span.getStartTime(), endTime) : null;
            if (elapsedTime != null) {
                updateData.put("elapsedTime", elapsedTime);
            }
        } else {
            List<Map<String, Object>> invokeDataList = span.getOnInvokeData();
            if (invokeDataList == null) {
                invokeDataList = new ArrayList<>();
                span.setOnInvokeData(invokeDataList);
            }
            if (onInvokeData != null) {
                invokeDataList.add(onInvokeData);
            }
        }
        
        spanManager.updateSpan(span, updateData);
        
        final Exception ex = exception;
        return sendData(span).thenRun(() -> {
            if (ex != null && "LLM".equals(span.getComponentType())) {
                spanManager.updateSpan(span, new HashMap<>());
            }
        });
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onPostStream(String invokeId, Object chunk) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        span.appendStreamOutput(chunk);
        
        return CompletableFuture.completedFuture(null);
    }
    
    @TriggerEvent
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> onPostInvoke(String invokeId, Object outputs, Object inputs) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("outputs", outputs);
        
        // Update inputs for End and Message component types
        if (inputs != null && span.getComponentType() != null) {
            String componentType = span.getComponentType();
            if ("End".equals(componentType) || "Message".equals(componentType)) {
                span.setInputs((Map<String, Object>) inputs);
            }
        }
        
        spanManager.updateSpan(span, updateData);
        
        return CompletableFuture.completedFuture(null);
    }
    
    @TriggerEvent
    public CompletableFuture<Void> onCallDone(String invokeId, Object outputs) {
        TraceWorkflowSpan span = getTracerWorkflowSpan(invokeId);
        
        Instant endTime = Instant.now();
        String elapsedTime = span.getStartTime() != null ? getElapsedTime(span.getStartTime(), endTime) : null;
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("endTime", endTime);
        if (outputs != null) {
            updateData.put("outputs", outputs);
        }
        if (elapsedTime != null) {
            updateData.put("elapsedTime", elapsedTime);
        }
        
        spanManager.updateSpan(span, updateData);
        
        return sendData(span).thenRun(() -> {
            if ("End".equals(span.getComponentType()) && span.getEndTime() != null) {
                spanManager.updateSpan(span, new HashMap<>());
            }
        });
    }
}

