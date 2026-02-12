/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

/**
 * Schema for trace stream data.
 * 
 * @param type the trace type
 * @param payload the trace payload
 * @author OpenJiuwen
 * @since 1.0.0
 */
public record TraceSchema(String type, Object payload) {
    
    /**
     * Creates a TraceSchema from a map.
     * 
     * @param data the data map
     * @return the TraceSchema
     * @throws IllegalArgumentException if required fields are missing
     */
    public static TraceSchema fromMap(java.util.Map<String, Object> data) {
        if (!data.containsKey("type") || !data.containsKey("payload")) {
            throw new IllegalArgumentException("TraceSchema requires type and payload fields");
        }
        
        String type = data.get("type").toString();
        Object payload = data.get("payload");
        
        return new TraceSchema(type, payload);
    }
}

