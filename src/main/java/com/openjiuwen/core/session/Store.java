/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

/**
 * Store is the abstract base class for data storage.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Store {
    
    /**
     * Reads a value from the store by key.
     * 
     * @param key the key to read, can be a string, map schema, or list schema
     * @return the value, or null if not found
     */
    Object read(Object key);
    
    /**
     * Writes data to the store.
     * 
     * @param value the data to write
     */
    void write(java.util.Map<String, Object> value);
}

