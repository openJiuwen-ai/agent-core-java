/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base trace span class holding common trace properties.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.span.Span}.
 */
public class Span {

    private String traceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> inputs;
    private Object outputs;
    private Map<String, Object> error;
    private String invokeId;
    private String parentInvokeId;
    private List<String> childInvokesId;
    private String status;
    private List<Map<String, Object>> onInvokeData;

    public Span() {
    }

    public Span(String traceId, String invokeId, String parentInvokeId) {
        this.traceId = traceId;
        this.invokeId = invokeId;
        this.parentInvokeId = parentInvokeId;
    }

    /**
     * Update span attributes from a data map.
     */
    public void update(Map<String, Object> data) {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            setField(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Append a child invoke ID.
     */
    public void appendChildInvokeId(String invokeId) {
        if (childInvokesId == null) {
            childInvokesId = new ArrayList<>();
        }
        childInvokesId.add(invokeId);
    }

    // -- field setters for reflection-like update --
    @SuppressWarnings("unchecked")
    protected void setField(String name, Object value) {
        switch (name) {
            case "start_time":
            case "startTime":
                if (value instanceof LocalDateTime) startTime = (LocalDateTime) value;
                break;
            case "end_time":
            case "endTime":
                if (value instanceof LocalDateTime) endTime = (LocalDateTime) value;
                break;
            case "inputs":
                if (value instanceof Map) inputs = (Map<String, Object>) value;
                break;
            case "outputs":
                outputs = value;
                break;
            case "error":
                if (value instanceof Map) error = (Map<String, Object>) value;
                break;
            case "invoke_id":
            case "invokeId":
                if (value instanceof String) invokeId = (String) value;
                break;
            case "status":
                if (value instanceof String) status = (String) value;
                break;
            case "on_invoke_data":
            case "onInvokeData":
                if (value instanceof List) onInvokeData = (List<Map<String, Object>>) value;
                break;
            default:
                // subclasses can override
                break;
        }
    }

    // -- Getters and Setters --

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Map<String, Object> getInputs() { return inputs; }
    public void setInputs(Map<String, Object> inputs) { this.inputs = inputs; }
    public Object getOutputs() { return outputs; }
    public void setOutputs(Object outputs) { this.outputs = outputs; }
    public Map<String, Object> getError() { return error; }
    public void setError(Map<String, Object> error) { this.error = error; }
    public String getInvokeId() { return invokeId; }
    public void setInvokeId(String invokeId) { this.invokeId = invokeId; }
    public String getParentInvokeId() { return parentInvokeId; }
    public void setParentInvokeId(String parentInvokeId) { this.parentInvokeId = parentInvokeId; }
    public List<String> getChildInvokesId() { return childInvokesId; }
    public void setChildInvokesId(List<String> childInvokesId) { this.childInvokesId = childInvokesId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Map<String, Object>> getOnInvokeData() { return onInvokeData; }
    public void setOnInvokeData(List<Map<String, Object>> onInvokeData) { this.onInvokeData = onInvokeData; }
}
