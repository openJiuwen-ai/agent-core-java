/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

import java.util.Map;

/**
 * Router session wrapper.
 *
 * <p>Mirrors Python's {@code RouterSession} in
 * {@code openjiuwen/core/session/internal/wrapper.py}.</p>
 */
public class RouterSession extends StateSession {

    public RouterSession(BaseSession innerSession) {
        super(innerSession);
    }

    public RouterSession(BaseSession innerSession, SessionStateAccess stateOverride) {
        super(innerSession, stateOverride);
    }

    @Override
    public void interact(Object value) {
    }

    @Override
    public void trace(Map<String, Object> data) {
        TracerWorkflowUtils.trace(innerSession, data);
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
    }

    @Override
    public void writeCustomStream(Map<String, Object> data) {
    }

    @Override
    public void traceError(Throwable error) {
        TracerWorkflowUtils.traceError(innerSession, error);
    }

    @Override
    public void updateGlobalState(Map<String, Object> data) {
    }

    @Override
    public void updateState(Map<String, Object> data) {
    }

    @Override
    public Object getWorkflowConfig(String workflowId) {
        return null;
    }

    @Override
    public Object getAgentConfig() {
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
