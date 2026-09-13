/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

/**
 * Shared HTTP session wrapper.
 * 
 * @since 0.1.7
 */
public class HttpSession extends RefCountedResource {
    private final java.net.http.HttpClient session;
    private final SessionConfig config;

    /**
     * HttpSession.
     * 
     * @param session session
     * @param config config
     * @since 0.1.7
     */
    public HttpSession(java.net.http.HttpClient session, SessionConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SessionConfig getConfig() {
        return config;
    }

    /**
     * session.
     *
     * @return HttpClient
     * @since 0.1.7
     */
    public java.net.http.HttpClient session() {
        if (isClosed()) {
            throw new IllegalStateException("Session is isClosed");
        }
        return session;
    }

    /**
     * doClose.
     * 
     * @since 0.1.7
     */
    @Override
    protected void doClose() {
        // JDK HttpClient does not expose an explicit close hook.
    }
}
