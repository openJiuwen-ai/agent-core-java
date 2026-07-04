/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Map;

/**
 * Wrapper around another session.
 *
 * <p>Mirrors Python's {@code WrappedSession} in
 * {@code openjiuwen/core/session/internal/wrapper.py}.</p>
 */
public abstract class WrappedSession extends BaseSession {

    protected final BaseSession innerSession;

    protected WrappedSession(BaseSession innerSession) {
        this.innerSession = innerSession;
    }

    public Object getWorkflowConfig(String workflowId) {
        return innerSession.config().getWorkflowConfig(workflowId);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAgentConfig() {
        return (T) innerSession.config().getAgentConfig();
    }

    public Object getEnv(String key) {
        return innerSession.config().getEnv(key);
    }

    public BaseSession base() {
        return innerSession;
    }

    public BaseSession innerSession() {
        return innerSession;
    }

    @Override
    public SessionConfigAccess config() {
        return innerSession.config();
    }

    @Override
    public SessionStateAccess state() {
        return innerSession.state();
    }

    @Override
    public Object tracer() {
        return innerSession.tracer();
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return innerSession.streamWriterManager();
    }

    @Override
    public Object checkpointer() {
        return innerSession.checkpointer();
    }

    @Override
    public Object actorManager() {
        return innerSession.actorManager();
    }

    @Override
    public CallbackManager callbackManager() {
        return innerSession.callbackManager();
    }

    public abstract String executableId();

    @Override
    public abstract String sessionId();

    public String userId() {
        return "";
    }

    @Override
    public abstract void updateState(Map<String, Object> data);

    public abstract Object getState(Object key);

    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    public abstract void updateGlobalState(Map<String, Object> data);

    public abstract Object getGlobalState(Object key);

    public abstract StreamWriter<?> streamWriter();

    public abstract StreamWriter<?> customWriter();

    public abstract void writeStream(Object data);

    public abstract void writeCustomStream(Map<String, Object> data);

    public abstract void trace(Map<String, Object> data);

    public abstract void traceError(Throwable error);

    public void traceError(Exception error) {
        traceError((Throwable) error);
    }

    public abstract void interact(Object value);

    public void postRun() {
    }

    public void commit() {
    }

    public void preRun(Map<String, Object> kwargs) {
    }

    public void release(String sessionId) {
    }
}
