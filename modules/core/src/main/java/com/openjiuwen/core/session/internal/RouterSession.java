/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

import java.util.Map;

/**
 * Router session where most operations are no-ops.
 * Used for routing/branching components that don't need full session functionality.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.wrapper.RouterSession}.
 * 
 * @since 0.1.7
 */
public class RouterSession extends StateSession {
    /**
     * RouterSession.
     * 
     * @param inner inner
     * @since 0.1.7
     */
    public RouterSession(BaseSession inner) {
        super(inner);
    }

    /**
     * interact.
     * 
     * @param value value
     * @since 0.1.7
     */
    @Override
    public void interact(Object value) {
        // no-op
    }

    /**
     * trace.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void trace(Map<String, Object> data) {
        TracerWorkflowUtils.trace(inner, data);
    }

    /**
     * traceError.
     * 
     * @param error error
     * @since 0.1.7
     */
    @Override
    public void traceError(Exception error) {
        TracerWorkflowUtils.traceError(inner, error);
    }

    /**
     * streamWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriter<?> streamWriter() {
        return null;
    }

    /**
     * customWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriter<?> customWriter() {
        return null;
    }

    /**
     * writeStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void writeStream(Object data) {
        // no-op
    }

    /**
     * writeCustomStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void writeCustomStream(Map<String, Object> data) {
        // no-op
    }

    /**
     * updateGlobalState.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void updateGlobalState(Map<String, Object> data) {
        // no-op
    }

    /**
     * updateState.
     * 
     * @param data data
     * @since 0.1.7
     */
    @Override
    public void updateState(Map<String, Object> data) {
        // no-op
    }

    /**
     * getWorkflowConfig.
     * 
     * @param workflowId workflowId
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getWorkflowConfig(String workflowId) {
        return null;
    }

    /**
     * getAgentConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Config.MetadataLike getAgentConfig() {
        return null;
    }

    /**
     * getEnv.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getEnv(String key) {
        return null;
    }

    /**
     * base.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public BaseSession base() {
        return null;
    }
}
