/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema for custom stream data.
 * 
 * <p>Accepts arbitrary fields and stores them in a map.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class CustomSchema {
    
    private final Map<String, Object> data;
    
    /**
     * Creates a new CustomSchema with the given data.
     * 
     * @param data the data map
     */
    public CustomSchema(Map<String, Object> data) {
        this.data = new HashMap<>(data != null ? data : Map.of());
    }
    
    /**
     * Creates an empty CustomSchema.
     */
    public CustomSchema() {
        this(null);
    }
    
    /**
     * Gets a field value.
     * 
     * @param key the field key
     * @return the field value, or null if not found
     */
    public Object get(String key) {
        return data.get(key);
    }
    
    /**
     * Gets all data.
     * 
     * @return the data map
     */
    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }
    
    /**
     * Creates a CustomSchema from a map.
     * 
     * @param data the data map
     * @return the CustomSchema
     */
    public static CustomSchema fromMap(Map<String, Object> data) {
        return new CustomSchema(data);
    }
    
    @Override
    public String toString() {
        return "CustomSchema(" + data + ")";
    }
}

