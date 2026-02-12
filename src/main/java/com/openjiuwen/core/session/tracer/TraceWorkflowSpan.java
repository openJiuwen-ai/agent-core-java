/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Span for tracing workflow execution.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TraceWorkflowSpan extends Span {
    
    private String executionId;
    private List<String> sourceIds;
    private String workflowId;
    private String workflowVersion;
    private String workflowName;
    private String componentId;
    private String componentName;
    private String componentType;
    private String loopNodeId;
    private Integer loopIndex;
    private Map<String, Map<String, Object>> llmInvokeData;
    private String parentNodeId;
    private List<Object> streamInputs;
    private List<Object> streamOutputs;
    
    /**
     * Creates a new TraceWorkflowSpan.
     */
    public TraceWorkflowSpan() {
        super();
    }
    
    /**
     * Creates a new TraceWorkflowSpan with trace ID.
     * 
     * @param traceId the trace ID
     */
    public TraceWorkflowSpan(String traceId) {
        super(traceId);
    }
    
    /**
     * Appends a stream output chunk.
     * 
     * @param chunk the chunk to append
     */
    public void appendStreamOutput(Object chunk) {
        if (streamOutputs == null) {
            streamOutputs = new ArrayList<>();
        }
        streamOutputs.add(chunk);
    }
    
    /**
     * Appends a stream input chunk.
     * 
     * @param chunk the chunk to append
     */
    public void appendStreamInputs(Object chunk) {
        if (streamInputs == null) {
            streamInputs = new ArrayList<>();
        }
        streamInputs.add(chunk);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void update(Map<String, Object> data) {
        super.update(data);
        if (data == null) return;
        
        if (data.containsKey("executionId")) {
            this.executionId = (String) data.get("executionId");
        }
        if (data.containsKey("sourceIds")) {
            this.sourceIds = (List<String>) data.get("sourceIds");
        }
        if (data.containsKey("workflowId")) {
            this.workflowId = (String) data.get("workflowId");
        }
        if (data.containsKey("workflowVersion")) {
            this.workflowVersion = (String) data.get("workflowVersion");
        }
        if (data.containsKey("workflowName")) {
            this.workflowName = (String) data.get("workflowName");
        }
        if (data.containsKey("componentId")) {
            this.componentId = (String) data.get("componentId");
        }
        if (data.containsKey("componentName")) {
            this.componentName = (String) data.get("componentName");
        }
        if (data.containsKey("componentType")) {
            this.componentType = (String) data.get("componentType");
        }
        if (data.containsKey("loopNodeId")) {
            this.loopNodeId = (String) data.get("loopNodeId");
        }
        if (data.containsKey("loopIndex")) {
            this.loopIndex = (Integer) data.get("loopIndex");
        }
        if (data.containsKey("parentNodeId")) {
            this.parentNodeId = (String) data.get("parentNodeId");
        }
    }
    
    // Getters and Setters
    
    public String getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }
    
    public List<String> getSourceIds() {
        return sourceIds;
    }
    
    public void setSourceIds(List<String> sourceIds) {
        this.sourceIds = sourceIds;
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
    
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
    
    public String getWorkflowVersion() {
        return workflowVersion;
    }
    
    public void setWorkflowVersion(String workflowVersion) {
        this.workflowVersion = workflowVersion;
    }
    
    public String getWorkflowName() {
        return workflowName;
    }
    
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }
    
    public String getComponentId() {
        return componentId;
    }
    
    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }
    
    public String getComponentName() {
        return componentName;
    }
    
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }
    
    public String getComponentType() {
        return componentType;
    }
    
    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }
    
    public String getLoopNodeId() {
        return loopNodeId;
    }
    
    public void setLoopNodeId(String loopNodeId) {
        this.loopNodeId = loopNodeId;
    }
    
    public Integer getLoopIndex() {
        return loopIndex;
    }
    
    public void setLoopIndex(Integer loopIndex) {
        this.loopIndex = loopIndex;
    }
    
    public Map<String, Map<String, Object>> getLlmInvokeData() {
        return llmInvokeData;
    }
    
    public void setLlmInvokeData(Map<String, Map<String, Object>> llmInvokeData) {
        this.llmInvokeData = llmInvokeData;
    }
    
    public String getParentNodeId() {
        return parentNodeId;
    }
    
    public void setParentNodeId(String parentNodeId) {
        this.parentNodeId = parentNodeId;
    }
    
    public List<Object> getStreamInputs() {
        return streamInputs;
    }
    
    public void setStreamInputs(List<Object> streamInputs) {
        this.streamInputs = streamInputs;
    }
    
    public List<Object> getStreamOutputs() {
        return streamOutputs;
    }
    
    public void setStreamOutputs(List<Object> streamOutputs) {
        this.streamOutputs = streamOutputs;
    }
}

