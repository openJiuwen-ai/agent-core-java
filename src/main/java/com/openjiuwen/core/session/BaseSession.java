/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.concurrent.CompletableFuture;

/**
 * Base session interface defining core session operations.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface BaseSession {
    
    /**
     * Gets the session configuration.
     * 
     * @return the configuration
     */
    Config getConfig();
    
    /**
     * Gets the session state.
     * 
     * @return the state
     */
    State getState();
    
    /**
     * Gets the tracer.
     * 
     * @return the tracer
     */
    Tracer getTracer();
    
    /**
     * Gets the stream writer manager.
     * 
     * @return the stream writer manager
     */
    StreamWriterManager getStreamWriterManager();
    
    /**
     * Gets the callback manager.
     * 
     * @return the callback manager
     */
    CallbackManager getCallbackManager();
    
    /**
     * Gets the session ID.
     * 
     * @return the session ID
     */
    String getSessionId();
    
    /**
     * Gets the checkpointer.
     * 
     * @return the checkpointer
     */
    Checkpointer getCheckpointer();
    
    /**
     * Gets the actor manager.
     * 
     * @return the actor manager, or null if not available
     */
    default Object getActorManager() {
        return null;
    }
    
    /**
     * Closes the session.
     * 
     * @return a CompletableFuture that completes when the session is closed
     */
    default CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }
}

