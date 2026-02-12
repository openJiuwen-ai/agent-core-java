/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Abstract base class for session state management.
 * 
 * <p>Provides the interface for state operations including global state,
 * trace updates, and standard get/update operations.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class State implements RecoverableStateLike {
    
    /**
     * Gets a value from the global state.
     * 
     * @param key the key or schema to use for retrieval
     * @return the value, or null if not found
     */
    public abstract Object getGlobal(Object key);
    
    /**
     * Updates the global state with the given data.
     * 
     * @param data the update data
     */
    public abstract void updateGlobal(Map<String, Object> data);
    
    /**
     * Updates the trace with the given span.
     * 
     * @param span the span to update
     */
    public abstract void updateTrace(Object span);
    
    /**
     * Updates the state with the given data.
     * 
     * @param data the update data
     */
    public abstract void update(Map<String, Object> data);
    
    /**
     * Gets a value from the state.
     * 
     * @param key the key or schema to use for retrieval (can be null to get all)
     * @return the value, or null if not found
     */
    public abstract Object get(Object key);
    
    /**
     * Gets the state data as a Map.
     * 
     * @return the state data
     */
    public abstract Map<String, Object> getData();
    
    /**
     * Creates a node-specific state.
     * 
     * @param executableId the executable ID
     * @param parentId the parent ID
     * @return the node state
     */
    public State createNodeState(String executableId, String parentId) {
        // Default implementation returns this state
        return this;
    }
    
    /**
     * Commits user inputs.
     * 
     * @param inputs the user inputs
     */
    public void commitUserInputs(Object inputs) {
        throw new UnsupportedOperationException("commitUserInputs not supported by this State implementation");
    }
    
    /**
     * Commits all state changes.
     */
    public void commit() {
        throw new UnsupportedOperationException("commit not supported by this State implementation");
    }
    
    /**
     * Gets inputs using a transformer.
     * 
     * @param <T> the result type
     * @param transformer the transformer
     * @return the transformed inputs
     */
    public <T> T getInputsByTransformer(Transformer<T> transformer) {
        throw new UnsupportedOperationException("getInputsByTransformer not supported by this State implementation");
    }
}

