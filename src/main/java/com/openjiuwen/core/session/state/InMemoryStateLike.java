/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import com.openjiuwen.core.session.SessionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory implementation of StateLike.
 * 
 * <p>Stores state in a HashMap and provides deep copy semantics for all read operations.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InMemoryStateLike implements StateLike {
    
    /**
     * Internal state storage.
     */
    protected Map<String, Object> state;
    
    /**
     * Creates a new empty InMemoryStateLike.
     */
    public InMemoryStateLike() {
        this.state = new HashMap<>();
    }
    
    @Override
    public Object get(Object key) {
        Object result = SessionUtils.getBySchema(key, this.state);
        return deepCopy(result);
    }
    
    @Override
    public Object getByPrefix(Object key, String nestedPrefix) {
        Object result = SessionUtils.getBySchema(key, this.state, nestedPrefix);
        return deepCopy(result);
    }
    
    @Override
    public <T> T getByTransformer(Transformer<T> transformer) {
        return transformer.transform(new MapReadableState(this.state));
    }
    
    @Override
    public void update(Map<String, Object> data) {
        Map<String, Object> copiedData = SessionUtils.deepCopyMap(data);
        SessionUtils.updateDict(copiedData, this.state);
    }
    
    @Override
    public Map<String, Object> getState() {
        return SessionUtils.deepCopyMap(this.state);
    }
    
    @Override
    public void setState(Map<String, Object> state) {
        if (state != null && !state.isEmpty()) {
            this.state = state;
        }
    }
    
    /**
     * Creates a deep copy of an object.
     * 
     * @param obj the object to copy
     * @return a deep copy of the object
     */
    @SuppressWarnings("unchecked")
    private Object deepCopy(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return SessionUtils.deepCopyMap((Map<String, Object>) obj);
        }
        if (obj instanceof java.util.List) {
            return SessionUtils.deepCopyList((java.util.List<Object>) obj);
        }
        return obj;
    }
    
    /**
     * Simple wrapper to provide ReadableStateLike interface over a Map.
     */
    private static class MapReadableState implements ReadableStateLike {
        private final Map<String, Object> data;
        
        MapReadableState(Map<String, Object> data) {
            this.data = data;
        }
        
        @Override
        public Object get(Object key) {
            return SessionUtils.getBySchema(key, this.data);
        }
        
        @Override
        public Object getByPrefix(Object key, String nestedPrefix) {
            return SessionUtils.getBySchema(key, this.data, nestedPrefix);
        }
    }
}

