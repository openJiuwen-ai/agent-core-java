/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Workflow trace span with workflow/component metadata and stream data.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.TraceWorkflowSpan}.
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
    private Object interactiveInputs;
    private List<Object> streamInputs;
    private List<Object> streamOutputs;

    public TraceWorkflowSpan() {
    }

    public TraceWorkflowSpan(String traceId, String invokeId, String parentInvokeId, String parentNodeId) {
        super(traceId, invokeId, parentInvokeId);
        this.parentNodeId = parentNodeId;
        this.executionId = traceId;
    }

    /**
     * Append a stream output chunk.
     */
    public void appendStreamOutput(Object chunk) {
        if (streamOutputs == null) {
            streamOutputs = new ArrayList<>();
        }
        streamOutputs.add(chunk);
    }

    /**
     * Append a stream input chunk.
     */
    public void appendStreamInput(Object chunk) {
        if (streamInputs == null) {
            streamInputs = new ArrayList<>();
        }
        streamInputs.add(chunk);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setField(String fieldName, Object value) {
        switch (fieldName) {
            case "execution_id":
            case "executionId":
                if (value instanceof String) executionId = (String) value;
                break;
            case "source_ids":
            case "sourceIds":
                if (value instanceof List) sourceIds = (List<String>) value;
                break;
            case "workflow_id":
            case "workflowId":
                if (value instanceof String) workflowId = (String) value;
                break;
            case "workflow_version":
            case "workflowVersion":
                if (value instanceof String) workflowVersion = (String) value;
                break;
            case "workflow_name":
            case "workflowName":
                if (value instanceof String) workflowName = (String) value;
                break;
            case "component_id":
            case "componentId":
                if (value instanceof String) componentId = (String) value;
                break;
            case "component_name":
            case "componentName":
                if (value instanceof String) componentName = (String) value;
                break;
            case "component_type":
            case "componentType":
                if (value instanceof String) componentType = (String) value;
                break;
            case "loop_node_id":
            case "loopNodeId":
                if (value instanceof String) loopNodeId = (String) value;
                break;
            case "loop_index":
            case "loopIndex":
                if (value instanceof Number) loopIndex = ((Number) value).intValue();
                break;
            case "parent_node_id":
            case "parentNodeId":
                if (value instanceof String) parentNodeId = (String) value;
                break;
            case "interactive_inputs":
            case "interactiveInputs":
                interactiveInputs = value;
                break;
            case "stream_inputs":
            case "streamInputs":
                if (value instanceof List) streamInputs = (List<Object>) value;
                break;
            case "stream_outputs":
            case "streamOutputs":
                if (value instanceof List) streamOutputs = (List<Object>) value;
                break;
            default:
                super.setField(fieldName, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public TraceWorkflowSpan snapshot() {
        TraceWorkflowSpan copy = new TraceWorkflowSpan();
        copyBaseFields(copy);
        copy.executionId = executionId;
        copy.sourceIds = sourceIds == null ? null : new ArrayList<>(sourceIds);
        copy.workflowId = workflowId;
        copy.workflowVersion = workflowVersion;
        copy.workflowName = workflowName;
        copy.componentId = componentId;
        copy.componentName = componentName;
        copy.componentType = componentType;
        copy.loopNodeId = loopNodeId;
        copy.loopIndex = loopIndex;
        copy.llmInvokeData = llmInvokeData == null ? null
                : (Map<String, Map<String, Object>>) (Map<?, ?>) deepCopyMap(llmInvokeData);
        copy.parentNodeId = parentNodeId;
        copy.interactiveInputs = deepCopyValue(interactiveInputs);
        copy.streamInputs = streamInputs == null ? null : deepCopyList(streamInputs);
        copy.streamOutputs = streamOutputs == null ? null : deepCopyList(streamOutputs);
        return copy;
    }

    // Getters and setters
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public List<String> getSourceIds() { return sourceIds; }
    public void setSourceIds(List<String> sourceIds) { this.sourceIds = sourceIds; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(String workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
    public String getComponentId() { return componentId; }
    public void setComponentId(String componentId) { this.componentId = componentId; }
    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }
    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public String getLoopNodeId() { return loopNodeId; }
    public void setLoopNodeId(String loopNodeId) { this.loopNodeId = loopNodeId; }
    public Integer getLoopIndex() { return loopIndex; }
    public void setLoopIndex(Integer loopIndex) { this.loopIndex = loopIndex; }
    public Map<String, Map<String, Object>> getLlmInvokeData() { return llmInvokeData; }
    public void setLlmInvokeData(Map<String, Map<String, Object>> llmInvokeData) { this.llmInvokeData = llmInvokeData; }
    public String getParentNodeId() { return parentNodeId; }
    public void setParentNodeId(String parentNodeId) { this.parentNodeId = parentNodeId; }
    public Object getInteractiveInputs() { return interactiveInputs; }
    public void setInteractiveInputs(Object interactiveInputs) { this.interactiveInputs = interactiveInputs; }
    public List<Object> getStreamInputs() { return streamInputs; }
    public void setStreamInputs(List<Object> streamInputs) { this.streamInputs = streamInputs; }
    public List<Object> getStreamOutputs() { return streamOutputs; }
    public void setStreamOutputs(List<Object> streamOutputs) { this.streamOutputs = streamOutputs; }
}
