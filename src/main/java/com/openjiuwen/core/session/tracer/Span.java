/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base span class for tracing.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class Span {
    
    private String traceId;
    private Instant startTime;
    private Instant endTime;
    private Map<String, Object> inputs;
    private Object outputs;
    private Map<String, Object> error;
    private String invokeId;
    private String parentInvokeId;
    private List<String> childInvokesId;
    private String status;
    private List<Map<String, Object>> onInvokeData;
    
    /**
     * Creates a new Span.
     */
    public Span() {
    }
    
    /**
     * Creates a new Span with trace ID.
     * 
     * @param traceId the trace ID
     */
    public Span(String traceId) {
        this.traceId = traceId;
    }
    
    /**
     * Updates span with data from a map.
     * 
     * @param data the data to update
     */
    @SuppressWarnings("unchecked")
    public void update(Map<String, Object> data) {
        if (data == null) return;
        
        if (data.containsKey("startTime")) {
            this.startTime = (Instant) data.get("startTime");
        }
        if (data.containsKey("endTime")) {
            this.endTime = (Instant) data.get("endTime");
        }
        if (data.containsKey("inputs")) {
            this.inputs = (Map<String, Object>) data.get("inputs");
        }
        if (data.containsKey("outputs")) {
            this.outputs = data.get("outputs");
        }
        if (data.containsKey("error")) {
            this.error = (Map<String, Object>) data.get("error");
        }
        if (data.containsKey("status")) {
            this.status = (String) data.get("status");
        }
        if (data.containsKey("onInvokeData")) {
            this.onInvokeData = (List<Map<String, Object>>) data.get("onInvokeData");
        }
    }
    
    /**
     * Appends a child invoke ID.
     * 
     * @param invokeId the child invoke ID
     */
    public void appendChildInvokeId(String invokeId) {
        if (childInvokesId == null) {
            childInvokesId = new ArrayList<>();
        }
        childInvokesId.add(invokeId);
    }
    
    // Getters and Setters
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
    
    public Map<String, Object> getInputs() {
        return inputs;
    }
    
    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
    
    public Object getOutputs() {
        return outputs;
    }
    
    public void setOutputs(Object outputs) {
        this.outputs = outputs;
    }
    
    public Map<String, Object> getError() {
        return error;
    }
    
    public void setError(Map<String, Object> error) {
        this.error = error;
    }
    
    public String getInvokeId() {
        return invokeId;
    }
    
    public void setInvokeId(String invokeId) {
        this.invokeId = invokeId;
    }
    
    public String getParentInvokeId() {
        return parentInvokeId;
    }
    
    public void setParentInvokeId(String parentInvokeId) {
        this.parentInvokeId = parentInvokeId;
    }
    
    public List<String> getChildInvokesId() {
        return childInvokesId;
    }
    
    public void setChildInvokesId(List<String> childInvokesId) {
        this.childInvokesId = childInvokesId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public List<Map<String, Object>> getOnInvokeData() {
        return onInvokeData;
    }
    
    public void setOnInvokeData(List<Map<String, Object>> onInvokeData) {
        this.onInvokeData = onInvokeData;
    }
}

