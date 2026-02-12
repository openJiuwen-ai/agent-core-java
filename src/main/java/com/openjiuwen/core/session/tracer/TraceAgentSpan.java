/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import java.util.Map;

/**
 * Span for tracing agent execution.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TraceAgentSpan extends Span {
    
    private String invokeType;
    private String name;
    private String elapsedTime;
    private Map<String, Object> metaData;
    
    /**
     * Creates a new TraceAgentSpan.
     */
    public TraceAgentSpan() {
        super();
    }
    
    /**
     * Creates a new TraceAgentSpan with trace ID.
     * 
     * @param traceId the trace ID
     */
    public TraceAgentSpan(String traceId) {
        super(traceId);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void update(Map<String, Object> data) {
        super.update(data);
        if (data == null) return;
        
        if (data.containsKey("invokeType")) {
            this.invokeType = (String) data.get("invokeType");
        }
        if (data.containsKey("name")) {
            this.name = (String) data.get("name");
        }
        if (data.containsKey("elapsedTime")) {
            this.elapsedTime = (String) data.get("elapsedTime");
        }
        if (data.containsKey("metaData")) {
            this.metaData = (Map<String, Object>) data.get("metaData");
        }
    }
    
    // Getters and Setters
    
    public String getInvokeType() {
        return invokeType;
    }
    
    public void setInvokeType(String invokeType) {
        this.invokeType = invokeType;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getElapsedTime() {
        return elapsedTime;
    }
    
    public void setElapsedTime(String elapsedTime) {
        this.elapsedTime = elapsedTime;
    }
    
    public Map<String, Object> getMetaData() {
        return metaData;
    }
    
    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }
}

