/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Mirrors Python's {@code TraceWorkflowSpan} in
 * {@code openjiuwen/core/session/tracer/span.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceWorkflowSpan extends Span {
    @JsonIgnore
    private final Object streamChunksLock = new Object();

    @JsonProperty("executionId")
    private String executionId;

    @JsonProperty("sourceIds")
    private List<String> sourceIds;

    @JsonProperty("workflowId")
    private String workflowId;

    @JsonProperty("workflowVersion")
    private String workflowVersion;

    @JsonProperty("workflowName")
    private String workflowName;

    @JsonProperty("componentId")
    private String componentId;

    @JsonProperty("componentName")
    private String componentName;

    @JsonProperty("componentType")
    private String componentType;

    @JsonProperty("loopNodeId")
    private String loopNodeId;

    @JsonProperty("loopIndex")
    private Integer loopIndex;

    @JsonIgnore
    private Map<String, Map<String, Object>> llmInvokeData;

    @JsonProperty("parentNodeId")
    private String parentNodeId;

    @JsonProperty("streamInputs")
    private List<Object> streamInputs;

    @JsonProperty("streamOutputs")
    private List<Object> streamOutputs;

    @JsonProperty("interactiveInputs")
    private Object interactiveInputs;

    @JsonProperty("innerError")
    private Map<String, Object> innerError;

    public TraceWorkflowSpan() {
    }

    public TraceWorkflowSpan(String traceId, String invokeId, String parentInvokeId, String parentNodeId) {
        super(traceId, invokeId, parentInvokeId);
        this.parentNodeId = parentNodeId;
        this.executionId = traceId;
    }

    /**
     * Records an output frame from a component.
     *
     * @param chunk emitted frame
     */
    public void appendStreamOutput(Object chunk) {
        synchronized (streamChunksLock) {
            if (streamOutputs == null) {
                streamOutputs = new ArrayList<>();
            }
            streamOutputs.add(chunk);
        }
    }

    /**
     * Records an input frame from a component.
     *
     * @param chunk received frame
     */
    public void appendStreamInputs(Object chunk) {
        synchronized (streamChunksLock) {
            if (streamInputs == null) {
                streamInputs = new ArrayList<>();
            }
            streamInputs.add(chunk);
        }
    }

    public void appendStreamInput(Object chunk) {
        appendStreamInputs(chunk);
    }

    @Override
    @SuppressWarnings("unchecked")
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
                if (value instanceof List<?>) {
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
            case "llm_invoke_data":
            case "llmInvokeData":
                if (value instanceof Map<?, ?>) {
                    llmInvokeData = (Map<String, Map<String, Object>>) value;
                }
                break;
            case "parent_node_id":
            case "parentNodeId":
                if (value instanceof String) {
                    parentNodeId = (String) value;
                }
                break;
            case "stream_inputs":
            case "streamInputs":
                if (value instanceof List<?>) {
                    setStreamInputs((List<Object>) value);
                }
                break;
            case "stream_outputs":
            case "streamOutputs":
                if (value instanceof List<?>) {
                    setStreamOutputs((List<Object>) value);
                }
                break;
            case "interactive_inputs":
            case "interactiveInputs":
                interactiveInputs = value;
                break;
            case "inner_error":
            case "innerError":
                if (value instanceof Map<?, ?>) {
                    innerError = (Map<String, Object>) value;
                }
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
        copyStreamChunksTo(copy);
        copy.interactiveInputs = deepCopyValue(interactiveInputs);
        copy.innerError = deepCopyMap(innerError);
        return copy;
    }

    private void copyStreamChunksTo(TraceWorkflowSpan copy) {
        List<Object> inputs;
        List<Object> outputs;
        synchronized (streamChunksLock) {
            inputs = copyChunks(streamInputs);
            outputs = copyChunks(streamOutputs);
        }

        // A frame may contain another span. Traverse its payload after releasing this span's lock.
        copy.streamInputs = deepCopyList(inputs);
        copy.streamOutputs = deepCopyList(outputs);
    }

    /**
     * Copies a recorded frame list. An absent list stays absent in the serialized trace.
     *
     * @param chunks recorded frames, or {@code null} for an absent field
     * @return a detached list, or {@code null} for an absent field
     */
    @Nullable
    private static List<Object> copyChunks(@Nullable List<Object> chunks) {
        return chunks == null ? null : new ArrayList<>(chunks);
    }

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

    /**
     * Returns the input frames in append order.
     *
     * @return a detached list, or null when the field is absent
     */
    @Nullable
    public List<Object> getStreamInputs() {
        synchronized (streamChunksLock) {
            return copyChunks(streamInputs);
        }
    }

    /**
     * Replaces the input frame list.
     *
     * @param streamInputs input frames, or null to clear the field
     */
    public void setStreamInputs(@Nullable List<Object> streamInputs) {
        synchronized (streamChunksLock) {
            this.streamInputs = copyChunks(streamInputs);
        }
    }

    /**
     * Returns the output frames in append order.
     *
     * @return a detached list, or null when the field is absent
     */
    @Nullable
    public List<Object> getStreamOutputs() {
        synchronized (streamChunksLock) {
            return copyChunks(streamOutputs);
        }
    }

    /**
     * Replaces the output frame list.
     *
     * @param streamOutputs output frames, or null to clear the field
     */
    public void setStreamOutputs(@Nullable List<Object> streamOutputs) {
        synchronized (streamChunksLock) {
            this.streamOutputs = copyChunks(streamOutputs);
        }
    }

    public Object getInteractiveInputs() {
        return interactiveInputs;
    }

    public void setInteractiveInputs(Object interactiveInputs) {
        this.interactiveInputs = interactiveInputs;
    }

    public Map<String, Object> getInnerError() {
        return innerError;
    }

    public void setInnerError(Map<String, Object> innerError) {
        this.innerError = innerError;
    }
}
