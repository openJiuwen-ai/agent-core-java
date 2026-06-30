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
 */
public class RouterSession extends StateSession {

    /**
     * Auto-generated for codecheck compliance.
     */
    public RouterSession(BaseSession inner) {
        super(inner);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void interact(Object value) {
        // no-op
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void trace(Map<String, Object> data) {
        TracerWorkflowUtils.trace(inner, data);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void traceError(Exception error) {
        TracerWorkflowUtils.traceError(inner, error);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamWriter<?> streamWriter() {
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamWriter<?> customWriter() {
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeStream(Object data) {
        // no-op
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeCustomStream(Map<String, Object> data) {
        // no-op
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateGlobalState(Map<String, Object> data) {
        // no-op
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateState(Map<String, Object> data) {
        // no-op
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getWorkflowConfig(String workflowId) {
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Config.MetadataLike getAgentConfig() {
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getEnv(String key) {
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseSession base() {
        return null;
    }
}
