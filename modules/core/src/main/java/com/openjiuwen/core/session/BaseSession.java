/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;

/**
 * Base session abstraction providing access to all session subsystems.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.session.BaseSession}.
 * Extends the minimal {@link Session} interface for backward compatibility with ContextEngine.
 * 
 * @since 0.1.7
 */
public abstract class BaseSession implements Session {
    private volatile String currentOperatorId;

    /**
     * Get the session configuration.
     * 
     * @return config instance
     * @since 0.1.7
     */
    public abstract Config config();

    /**
     * Get the session state.
     * 
     * @return state instance
     * @since 0.1.7
     */
    public abstract State state();

    /**
     * Get the tracer instance.
     * 
     * @return tracer or null if not configured
     * @since 0.1.7
     */
    public abstract Object tracer();

    /**
     * Get the stream writer manager.
     * 
     * @return stream writer manager
     * @since 0.1.7
     */
    public abstract StreamWriterManager streamWriterManager();

    /**
     * Get the callback manager.
     * 
     * @return callback manager
     * @since 0.1.7
     */
    public abstract CallbackManager callbackManager();

    /**
     * Get the unique session identifier.
     * 
     * @return session ID
     * @since 0.1.7
     */
    public abstract String sessionId();

    /**
     * Get the checkpointer instance.
     * 
     * @return checkpointer or null
     * @since 0.1.7
     */
    public abstract Object checkpointer();

    /**
     * Get the actor manager for this session.
     * <p>
     * Mirrors Python's {@code BaseSession.actor_manager()}.
     * 
     * @return actor manager or null if not applicable
     * @since 0.1.7
     */
    public Object actorManager() {
        return null;
    }

    // ---- Session interface compatibility ----

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getSessionId() {
        return sessionId();
    }

    /**
     * getState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getState(String key) {
        if (state() != null) {
            return state().get(key);
        }
        return null;
    }

    /**
     * updateState.
     * 
     * @param stateMap stateMap
     * @since 0.1.7
     */
    @Override
    public void updateState(java.util.Map<String, Object> stateMap) {
        if (state() != null && stateMap != null) {
            state().update(stateMap);
        }
    }

    /**
     * setCurrentOperatorId.
     * 
     * @param operatorId operatorId
     * @since 0.1.7
     */
    @Override
    public void setCurrentOperatorId(String operatorId) {
        this.currentOperatorId = operatorId;
    }

    /**
     * getCurrentOperatorId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getCurrentOperatorId() {
        return currentOperatorId;
    }

    /**
     * Close the session and release resources.
     * 
     * @since 0.1.7
     */
    public void close() {
        // Default no-op, subclasses can override.
    }
}
