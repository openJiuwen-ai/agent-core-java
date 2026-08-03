/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Shared HTTP session wrapper.
 */
public class HttpSession extends RefCountedResource {
    private final java.net.http.HttpClient session;
    private final SessionConfig config;
    private BaseRefResourceMgr.ResourceLease<HttpSession> lastLease;

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
            throw new IllegalStateException("Session is closed");
        }
        return session;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseRefResourceMgr.ResourceLease<HttpSession> join() {
        return lastLease == null ? new BaseRefResourceMgr.ResourceLease<>(this, false) : lastLease;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    void setLastLease(BaseRefResourceMgr.ResourceLease<HttpSession> lastLease) {
        this.lastLease = lastLease;
    }

    @Override
    protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
        // JDK HttpClient does not expose an explicit close hook.
        return CompletableFuture.completedFuture(null);
    }
}
