/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.stream.StreamWriterManager;

/**
 * Base session abstraction shared by workflow, agent, and graph runtime sessions.
 *
 * <p>Mirrors Python's {@code BaseSession} in
 * {@code openjiuwen/core/session/session.py}.</p>
 */
public abstract class BaseSession {

    private String currentOperatorId;

    @SuppressWarnings("unchecked")
    public <T extends SessionConfigAccess> T config() {
        return (T) new Config();
    }

    public <T extends SessionStateAccess> T state() {
        return null;
    }

    public Object tracer() {
        return null;
    }

    public <T> T streamWriterManager() {
        return null;
    }

    public String sessionId() {
        return "";
    }

    public String workflowId() {
        return sessionId();
    }

    public String mainWorkflowId() {
        return workflowId();
    }

    public int workflowNestingDepth() {
        return 0;
    }

    public String agentId() {
        return sessionId();
    }

    public String teamId() {
        return sessionId();
    }

    public BaseSession parent() {
        return null;
    }

    public Object checkpointer() {
        return null;
    }

    public Object actorManager() {
        return null;
    }

    public <T> T callbackManager() {
        return null;
    }

    public String getSessionId() {
        return sessionId();
    }

    public Object getState(String key) {
        State currentState = state();
        return currentState == null ? null : currentState.get(key);
    }

    public void updateState(java.util.Map<String, Object> data) {
        State currentState = state();
        if (currentState != null && data != null) {
            currentState.update(data);
        }
    }

    public void setCurrentOperatorId(String operatorId) {
        this.currentOperatorId = operatorId;
    }

    public String getCurrentOperatorId() {
        return currentOperatorId;
    }

    public void close() {
    }
}
