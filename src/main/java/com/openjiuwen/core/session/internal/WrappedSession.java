/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
 */
public abstract class WrappedSession {

    protected final BaseSession inner;

    protected WrappedSession(BaseSession inner) {
        this.inner = inner;
    }

    /**
     * Get workflow config for the given workflow ID.
     */
    public Object getWorkflowConfig(String workflowId) {
        return inner.config() != null ? inner.config().getWorkflowConfig(workflowId) : null;
    }

    /**
     * Get agent config.
     */
    @SuppressWarnings("unchecked")
    public Config.MetadataLike getAgentConfig() {
        return inner.config() != null ? (Config.MetadataLike) inner.config().getAgentConfig() : null;
    }

    /**
     * Get environment variable from config.
     */
    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    /**
     * Get the underlying base session.
     */
    public BaseSession base() {
        return inner;
    }

    /**
     * Get the executable ID for this wrapped session.
     */
    public abstract String executableId();

    /**
     * Get the session ID.
     */
    public abstract String sessionId();

    /**
     * Get user ID (default empty).
     */
    public String userId() {
        return "";
    }

    /**
     * Update the session state.
     */
    public abstract void updateState(Map<String, Object> data);

    /**
     * Get session state by key.
     */
    public abstract Object getState(Object key);

    /**
     * Update global state.
     */
    public abstract void updateGlobalState(Map<String, Object> data);

    /**
     * Get global state by key.
     */
    public abstract Object getGlobalState(Object key);

    /**
     * Get output stream writer.
     */
    public abstract StreamWriter<?> streamWriter();

    /**
     * Get custom stream writer.
     */
    public abstract StreamWriter<?> customWriter();

    /**
     * Write data to the output stream.
     */
    public abstract void writeStream(Object data);

    /**
     * Write data to the custom stream.
     */
    public abstract void writeCustomStream(Map<String, Object> data);

    /**
     * Trace data.
     */
    public abstract void trace(Map<String, Object> data);

    /**
     * Trace an error.
     */
    public abstract void traceError(Exception error);

    /**
     * Trigger an interaction.
     */
    public abstract void interact(Object value);

    /**
     * Post-run hook (default no-op).
     */
    public void postRun() {
        // no-op
    }

    /**
     * Pre-run hook (default no-op).
     */
    public void preRun(Map<String, Object> kwargs) {
        // no-op
    }

    /**
     * Release session resources (default no-op).
     */
    public void release(String sessionId) {
        // no-op
    }
}
