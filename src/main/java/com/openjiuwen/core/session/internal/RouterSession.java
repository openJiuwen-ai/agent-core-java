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

    public RouterSession(BaseSession inner) {
        super(inner);
    }

    @Override
    public void interact(Object value) {
        // no-op
    }

    @Override
    public void trace(Map<String, Object> data) {
        TracerWorkflowUtils.trace(inner, data);
    }

    @Override
    public void traceError(Exception error) {
        TracerWorkflowUtils.traceError(inner, error);
    }

    @Override
    public StreamWriter<?> streamWriter() {
        return null;
    }

    @Override
    public StreamWriter<?> customWriter() {
        return null;
    }

    @Override
    public void writeStream(Object data) {
        // no-op
    }

    @Override
    public void writeCustomStream(Map<String, Object> data) {
        // no-op
    }

    @Override
    public void updateGlobalState(Map<String, Object> data) {
        // no-op
    }

    @Override
    public void updateState(Map<String, Object> data) {
        // no-op
    }

    @Override
    public Object getWorkflowConfig(String workflowId) {
        return null;
    }

    @Override
    public Config.MetadataLike getAgentConfig() {
        return null;
    }

    @Override
    public Object getEnv(String key) {
        return null;
    }

    @Override
    public BaseSession base() {
        return null;
    }
}
