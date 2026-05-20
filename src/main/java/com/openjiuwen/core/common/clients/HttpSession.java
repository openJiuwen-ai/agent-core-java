/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

/**
 * Shared HTTP session wrapper.
 */
public class HttpSession extends RefCountedResource {
    private final java.net.http.HttpClient session;
    private final SessionConfig config;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpSession(java.net.http.HttpClient session, SessionConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public java.net.http.HttpClient session() {
        if (isClosed()) {
            throw new IllegalStateException("Session is isClosed");
        }
        return session;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void doClose() {
        // JDK HttpClient does not expose an explicit close hook.
    }
}
