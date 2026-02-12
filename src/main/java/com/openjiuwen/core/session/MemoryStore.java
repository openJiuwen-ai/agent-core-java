/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory implementation of Store.
 * 
 * <p>Stores data in a HashMap and provides read/write operations with schema support.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class MemoryStore implements Store {
    
    /**
     * Internal data storage.
     */
    Map<String, Object> data;
    
    /**
     * Creates a new empty MemoryStore.
     */
    public MemoryStore() {
        this.data = new HashMap<>();
    }
    
    @Override
    public Object read(Object key) {
        return SessionUtils.getBySchema(key, this.data);
    }
    
    @Override
    public void write(Map<String, Object> value) {
        SessionUtils.updateDict(value, this.data);
    }
    
    /**
     * Gets the internal data map (for testing purposes).
     * 
     * @return the internal data map
     */
    public Map<String, Object> getData() {
        return this.data;
    }
}

