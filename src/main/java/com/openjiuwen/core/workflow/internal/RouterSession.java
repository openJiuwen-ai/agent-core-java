/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.ProxySession;
import com.openjiuwen.core.session.stream.StreamWriter;

import java.util.Map;

/**
 * Mirrors Python's router session wrapper used by
 * {@code openjiuwen/core/workflow/_workflow.py}.
 */
public class RouterSession extends com.openjiuwen.core.session.internal.RouterSession {

    private final ProxySession proxySession;

    public RouterSession(ProxySession proxySession) {
        super(proxySession);
        this.proxySession = proxySession;
    }

    public BaseSession innerSession() {
        return proxySession != null ? proxySession.getSession() : null;
    }

    public Object getGlobal(String key) {
        return proxySession != null ? proxySession.getGlobal(key) : null;
    }

    @Override
    public String executableId() {
        BaseSession current = innerSession();
        return current == null ? "" : current.sessionId();
    }

    @Override
    public String sessionId() {
        BaseSession current = innerSession();
        return current == null ? "" : current.sessionId();
    }

    @Override
    public void updateState(Map<String, Object> data) {
        BaseSession current = innerSession();
        if (current != null) {
            current.updateState(data);
        }
    }

    @Override
    public Object getState(Object key) {
        BaseSession current = innerSession();
        return current == null || current.state() == null ? null : current.state().get(key);
    }

    @Override
    public void updateGlobalState(Map<String, Object> data) {
        BaseSession current = innerSession();
        if (current != null && current.state() != null) {
            current.state().updateGlobal(data);
        }
    }

    @Override
    public Object getGlobalState(Object key) {
        return proxySession != null ? proxySession.getGlobal(String.valueOf(key)) : null;
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
    public void trace(Map<String, Object> data) {
    }

    @Override
    public void traceError(Throwable error) {
    }

    @Override
    public void interact(Object value) {
    }
}
