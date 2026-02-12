/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

/**
 * Schema for standard output stream data.
 * 
 * @param type the output type
 * @param index the output index
 * @param payload the output payload
 * @author OpenJiuwen
 * @since 1.0.0
 */
public record OutputSchema(String type, int index, Object payload) {
    
    /**
     * Creates an OutputSchema from a map.
     * 
     * @param data the data map
     * @return the OutputSchema
     * @throws IllegalArgumentException if required fields are missing
     */
    public static OutputSchema fromMap(java.util.Map<String, Object> data) {
        if (!data.containsKey("type") || !data.containsKey("index") || !data.containsKey("payload")) {
            throw new IllegalArgumentException("OutputSchema requires type, index, and payload fields");
        }
        
        String type = data.get("type").toString();
        int index;
        Object indexObj = data.get("index");
        if (indexObj instanceof Number num) {
            index = num.intValue();
        } else {
            index = Integer.parseInt(indexObj.toString());
        }
        Object payload = data.get("payload");
        
        return new OutputSchema(type, index, payload);
    }
}

