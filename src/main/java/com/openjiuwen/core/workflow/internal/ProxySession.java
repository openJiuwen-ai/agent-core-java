/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.SessionStateAccess;

/**
 * Mirrors Python's proxy session holder used by
 * {@code openjiuwen/core/workflow/_workflow.py}.
 */
public class ProxySession extends BaseSession {

    private BaseSession session;

    public BaseSession getSession() {
        return session;
    }

    public void setSession(BaseSession session) {
        this.session = session;
    }

    public Object getGlobal(String key) {
        if (session instanceof WorkflowRuntimeSession runtimeSession) {
            return runtimeSession.getGlobalState(key);
        }
        return null;
    }

    @Override
    public <T extends SessionConfigAccess> T config() {
        return session == null ? super.config() : session.config();
    }

    @Override
    public <T extends SessionStateAccess> T state() {
        return session == null ? null : session.state();
    }

    @Override
    public Object tracer() {
        return session == null ? null : session.tracer();
    }

    @Override
    public <T> T streamWriterManager() {
        return session == null ? null : session.streamWriterManager();
    }

    @Override
    public String sessionId() {
        return session == null ? "" : session.sessionId();
    }

    @Override
    public String workflowId() {
        return session == null ? "" : session.workflowId();
    }

    @Override
    public BaseSession parent() {
        return session == null ? null : session.parent();
    }

    @Override
    public Object checkpointer() {
        return session == null ? null : session.checkpointer();
    }

    @Override
    public Object actorManager() {
        return session == null ? null : session.actorManager();
    }

    @Override
    public CallbackManager callbackManager() {
        return session == null ? null : session.callbackManager();
    }
}
