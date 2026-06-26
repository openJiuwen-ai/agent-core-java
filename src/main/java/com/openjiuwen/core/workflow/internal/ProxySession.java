/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.session.BaseSession;

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
}
