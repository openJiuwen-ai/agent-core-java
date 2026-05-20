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

    /**
     * Auto-generated for codecheck compliance.
     */
    public TraceWorkflowSpan() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void setField(String fieldName, Object value) {
        switch (fieldName) {
            case "execution_id":
            case "executionId":
                if (value instanceof String) {
                    executionId = (String) value;
                }
                break;
            case "source_ids":
            case "sourceIds":
                if (value instanceof List) {
                    sourceIds = (List<String>) value;
                }
                break;
            case "workflow_id":
            case "workflowId":
                if (value instanceof String) {
                    workflowId = (String) value;
                }
                break;
            case "workflow_version":
            case "workflowVersion":
                if (value instanceof String) {
                    workflowVersion = (String) value;
                }
                break;
            case "workflow_name":
            case "workflowName":
                if (value instanceof String) {
                    workflowName = (String) value;
                }
                break;
            case "component_id":
            case "componentId":
                if (value instanceof String) {
                    componentId = (String) value;
                }
                break;
            case "component_name":
            case "componentName":
                if (value instanceof String) {
                    componentName = (String) value;
                }
                break;
            case "component_type":
            case "componentType":
                if (value instanceof String) {
                    componentType = (String) value;
                }
                break;
            case "loop_node_id":
            case "loopNodeId":
                if (value instanceof String) {
                    loopNodeId = (String) value;
                }
                break;
            case "loop_index":
            case "loopIndex":
                if (value instanceof Number) {
                    loopIndex = ((Number) value).intValue();
                }
                break;
            case "parent_node_id":
            case "parentNodeId":
                if (value instanceof String) {
                    parentNodeId = (String) value;
                }
                break;
            case "interactive_inputs":
            case "interactiveInputs":
                interactiveInputs = value;
                break;
            case "stream_inputs":
            case "streamInputs":
                if (value instanceof List) {
                    streamInputs = (List<Object>) value;
                }
                break;
            case "stream_outputs":
            case "streamOutputs":
                if (value instanceof List) {
                    streamOutputs = (List<Object>) value;
                }
                break;
            default:
                super.setField(fieldName, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getSourceIds() {
        return sourceIds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSourceIds(List<String> sourceIds) {
        this.sourceIds = sourceIds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWorkflowVersion() {
        return workflowVersion;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkflowVersion(String workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWorkflowName() {
        return workflowName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getComponentId() {
        return componentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getComponentName() {
        return componentName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getComponentType() {
        return componentType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLoopNodeId() {
        return loopNodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLoopNodeId(String loopNodeId) {
        this.loopNodeId = loopNodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getLoopIndex() {
        return loopIndex;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLoopIndex(Integer loopIndex) {
        this.loopIndex = loopIndex;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Map<String, Object>> getLlmInvokeData() {
        return llmInvokeData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLlmInvokeData(Map<String, Map<String, Object>> llmInvokeData) {
        this.llmInvokeData = llmInvokeData;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getParentNodeId() {
        return parentNodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setParentNodeId(String parentNodeId) {
        this.parentNodeId = parentNodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getInteractiveInputs() {
        return interactiveInputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInteractiveInputs(Object interactiveInputs) {
        this.interactiveInputs = interactiveInputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getStreamInputs() {
        return streamInputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStreamInputs(List<Object> streamInputs) {
        this.streamInputs = streamInputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getStreamOutputs() {
        return streamOutputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStreamOutputs(List<Object> streamOutputs) {
        this.streamOutputs = streamOutputs;
    }
}
