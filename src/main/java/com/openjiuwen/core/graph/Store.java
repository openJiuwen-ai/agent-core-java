/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Graph store interface for storing and retrieving graph execution state.
 * 
 * <p><strong>Note:</strong> This is a placeholder interface that will be fully
 * implemented when the graph module is converted.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Store {
    
    /**
     * Gets a value from the store.
     * 
     * @param sessionId the session identifier
     * @param key the key to retrieve
     * @return a CompletableFuture containing the value, or null if not found
     */
    CompletableFuture<Object> get(String sessionId, String key);
    
    /**
     * Puts a value into the store.
     * 
     * @param sessionId the session identifier
     * @param key the key
     * @param value the value to store
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> put(String sessionId, String key, Object value);
    
    /**
     * Deletes a value from the store.
     * 
     * @param sessionId the session identifier
     * @param key the key to delete
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> delete(String sessionId, String key);
    
    /**
     * Lists all keys for a session.
     * 
     * @param sessionId the session identifier
     * @return a CompletableFuture containing a map of keys to values
     */
    CompletableFuture<Map<String, Object>> list(String sessionId);
}

