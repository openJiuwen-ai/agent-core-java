/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Session interface for managing workflow/agent execution context.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Session {
    
    /**
     * Gets the executable ID.
     * 
     * @return the executable ID
     */
    String getExecutableId();
    
    /**
     * Gets the session ID.
     * 
     * @return the session ID
     */
    String getSessionId();
    
    /**
     * Gets the user ID.
     * 
     * @return the user ID, empty string by default
     */
    default String getUserId() {
        return "";
    }
    
    /**
     * Updates the session state.
     * 
     * @param data the data to update
     */
    void updateState(Map<String, Object> data);
    
    /**
     * Gets a state value by key.
     * 
     * @param key the key (String, List, or Map)
     * @return the state value
     */
    Object getState(Object key);
    
    /**
     * Gets the entire state.
     * 
     * @return the state
     */
    default Object getState() {
        return getState(null);
    }
    
    /**
     * Updates the global state.
     * 
     * @param data the data to update
     */
    void updateGlobalState(Map<String, Object> data);
    
    /**
     * Gets a global state value by key.
     * 
     * @param key the key (String, List, or Map)
     * @return the global state value
     */
    Object getGlobalState(Object key);
    
    /**
     * Gets the entire global state.
     * 
     * @return the global state
     */
    default Object getGlobalState() {
        return getGlobalState(null);
    }
    
    /**
     * Gets the stream writer.
     * 
     * @return the stream writer, or null if not available
     */
    StreamWriter<?, ?> getStreamWriter();
    
    /**
     * Gets the custom writer.
     * 
     * @return the custom writer, or null if not available
     */
    StreamWriter<?, ?> getCustomWriter();
    
    /**
     * Writes data to the stream.
     * 
     * @param data the data to write (Map or OutputSchema)
     * @return a CompletableFuture that completes when the write is done
     */
    CompletableFuture<Void> writeStream(Object data);
    
    /**
     * Writes data to the custom stream.
     * 
     * @param data the data to write
     * @return a CompletableFuture that completes when the write is done
     */
    CompletableFuture<Void> writeCustomStream(Map<String, Object> data);
    
    /**
     * Writes trace data.
     * 
     * @param data the trace data
     * @return a CompletableFuture that completes when the trace is written
     */
    CompletableFuture<Void> trace(Map<String, Object> data);
    
    /**
     * Writes trace error.
     * 
     * @param error the error
     * @return a CompletableFuture that completes when the trace is written
     */
    CompletableFuture<Void> traceError(Exception error);
    
    /**
     * Interacts with a value.
     * 
     * @param value the value to interact with
     * @return a CompletableFuture that completes when the interaction is done
     */
    CompletableFuture<Void> interact(Object value);
    
    /**
     * Gets workflow configuration.
     * 
     * @param workflowId the workflow ID
     * @return the workflow configuration
     */
    Object getWorkflowConfig(String workflowId);
    
    /**
     * Gets agent configuration.
     * 
     * @return the agent configuration
     */
    Object getAgentConfig();
    
    /**
     * Gets an environment variable.
     * 
     * @param key the key
     * @return the environment value, or null if not found
     */
    Object getEnv(String key);
    
    /**
     * Gets the base session.
     * 
     * @return the base session
     */
    BaseSession getBase();
    
    /**
     * Called after a run completes.
     * 
     * @return a CompletableFuture that completes when post-run is done
     */
    default CompletableFuture<Void> postRun() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Called before a run starts.
     * 
     * @param kwargs the arguments
     * @return a CompletableFuture that completes when pre-run is done
     */
    default CompletableFuture<Void> preRun(Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Releases the session.
     * 
     * @param sessionId the session ID
     * @return a CompletableFuture that completes when the session is released
     */
    default CompletableFuture<Void> release(String sessionId) {
        return CompletableFuture.completedFuture(null);
    }
}
