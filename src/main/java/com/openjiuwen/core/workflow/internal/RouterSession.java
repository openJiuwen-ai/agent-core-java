/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.session.BaseSession;

/**
 * Mirrors Python's router session wrapper used by
 * {@code openjiuwen/core/workflow/_workflow.py}.
 */
public class RouterSession extends BaseSession {

    private final ProxySession proxySession;

    public RouterSession(ProxySession proxySession) {
        this.proxySession = proxySession;
    }

    public BaseSession innerSession() {
        return proxySession != null ? proxySession.getSession() : null;
    }

    public Object getGlobal(String key) {
        return proxySession != null ? proxySession.getGlobal(key) : null;
    }
}
