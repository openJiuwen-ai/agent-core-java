/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Map;

/**
 * State-delegating session wrapper.
 *
 * <p>Mirrors Python's {@code StateSession} in
 * {@code openjiuwen/core/session/internal/wrapper.py}.</p>
 */
public abstract class StateSession extends WrappedSession {

    private final SessionStateAccess stateOverride;

    protected StateSession(BaseSession innerSession) {
        this(innerSession, null);
    }

    protected StateSession(BaseSession innerSession, SessionStateAccess stateOverride) {
        super(innerSession);
        this.stateOverride = stateOverride;
    }

    @Override
    public String executableId() {
        Object value = invokeZeroArg(innerSession, "executableId");
        return value == null ? innerSession.sessionId() : String.valueOf(value);
    }

    @Override
    public String sessionId() {
        return innerSession.sessionId();
    }

    @Override
    public SessionStateAccess state() {
        return stateOverride == null ? innerSession.state() : stateOverride;
    }

    @Override
    public void updateState(Map<String, Object> data) {
        state().update(data);
    }

    @Override
    public Object getState(Object key) {
        return state().get(key);
    }

    @Override
    public void updateGlobalState(Map<String, Object> data) {
        state().updateGlobal(data);
    }

    @Override
    public Object getGlobalState(Object key) {
        return state().getGlobal(key);
    }

    @Override
    public StreamWriter<?> streamWriter() {
        StreamWriterManager manager = innerSession.streamWriterManager();
        return manager == null ? null : manager.getOutputWriter();
    }

    @Override
    public StreamWriter<?> customWriter() {
        StreamWriterManager manager = innerSession.streamWriterManager();
        return manager == null ? null : manager.getCustomWriter();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void writeStream(Object data) {
        StreamWriter writer = (StreamWriter) streamWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter writer = (StreamWriter) customWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    private static Object invokeZeroArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
