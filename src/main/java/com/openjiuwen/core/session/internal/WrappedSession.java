/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;

/**
 * Abstract wrapped session providing convenience accessors around a {@link BaseSession}.
 * <p>
 * Subclasses implement the abstract methods to define state access, streaming, and tracing behavior.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.wrapper.WrappedSession}.
 * 
 * @since 0.1.7
 */
public abstract class WrappedSession {
    /**
     * inner.
     * 
     * @since 0.1.7
     */
    protected final BaseSession inner;

    /**
     * WrappedSession.
     * 
     * @param inner inner
     * @since 0.1.7
     */
    protected WrappedSession(BaseSession inner) {
        this.inner = inner;
    }

    /**
     * Get workflow config for the given workflow ID.
     * 
     * @param workflowId workflowId
     * @return the result
     * @since 0.1.7
     */
    public Object getWorkflowConfig(String workflowId) {
        return inner.config() != null ? inner.config().getWorkflowConfig(workflowId) : null;
    }

    /**
     * getAgentConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Config.MetadataLike getAgentConfig() {
        return inner.config() != null ? (Config.MetadataLike) inner.config().getAgentConfig() : null;
    }

    /**
     * Get environment variable from config.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    /**
     * Get the underlying base session.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseSession base() {
        return inner;
    }

    /**
     * Get the executable ID for this wrapped session.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String executableId();

    /**
     * Get the session ID.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String sessionId();

    /**
     * Get user ID (default empty).
     * 
     * @return the result
     * @since 0.1.7
     */
    public String userId() {
        return "";
    }

    /**
     * Update the session state.
     * 
     * @param data data
     * @since 0.1.7
     */
    public abstract void updateState(Map<String, Object> data);

    /**
     * Get session state by key.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public abstract Object getState(Object key);

    /**
     * Update global state.
     * 
     * @param data data
     * @since 0.1.7
     */
    public abstract void updateGlobalState(Map<String, Object> data);

    /**
     * Get global state by key.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public abstract Object getGlobalState(Object key);

    /**
     * Get output stream writer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract StreamWriter<?> streamWriter();

    /**
     * Get custom stream writer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract StreamWriter<?> customWriter();

    /**
     * Write data to the output stream.
     * 
     * @param data data
     * @since 0.1.7
     */
    public abstract void writeStream(Object data);

    /**
     * Write data to the custom stream.
     * 
     * @param data data
     * @since 0.1.7
     */
    public abstract void writeCustomStream(Map<String, Object> data);

    /**
     * Trace data.
     * 
     * @param data data
     * @since 0.1.7
     */
    public abstract void trace(Map<String, Object> data);

    /**
     * Trace an error.
     * 
     * @param error error
     * @since 0.1.7
     */
    public abstract void traceError(Exception error);

    /**
     * Trigger an interaction.
     * 
     * @param value value
     * @since 0.1.7
     */
    public abstract void interact(Object value);

    /**
     * Post-run hook (default no-op).
     * 
     * @since 0.1.7
     */
    public void postRun() {
        // no-op
    }

    /**
     * Pre-run hook (default no-op).
     * 
     * @param kwargs kwargs
     * @since 0.1.7
     */
    public void preRun(Map<String, Object> kwargs) {
        // no-op
    }

    /**
     * Release session resources (default no-op).
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void release(String sessionId) {
        // no-op
    }
}
