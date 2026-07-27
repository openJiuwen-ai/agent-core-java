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
    private final com.openjiuwen.core.common.clients.http.HttpSession canonicalDelegate;
    private BaseRefResourceMgr.ResourceLease<HttpSession> lastLease;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpSession(java.net.http.HttpClient session, SessionConfig config) {
        this.session = session;
        this.config = config;
        this.canonicalDelegate = null;
    }

    private HttpSession(com.openjiuwen.core.common.clients.http.HttpSession canonicalDelegate) {
        this.session = null;
        this.config = canonicalDelegate.config();
        this.canonicalDelegate = canonicalDelegate;
    }

    static HttpSession wrap(com.openjiuwen.core.common.clients.http.HttpSession session) {
        return new HttpSession(session);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionConfig getConfig() {
        return canonicalDelegate != null ? canonicalDelegate.config() : config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public java.net.http.HttpClient session() {
        if (canonicalDelegate != null) {
            return canonicalDelegate.session();
        }
        if (isClosed()) {
            throw new IllegalStateException("Session is closed");
        }
        return session;
    }

    @Override
    public boolean isClosed() {
        return canonicalDelegate != null ? canonicalDelegate.isClosed() : super.isClosed();
    }

    @Override
    public int getRefCount() {
        return canonicalDelegate != null ? canonicalDelegate.getRefCount() : super.getRefCount();
    }

    @Override
    public Map<String, Object> getStats() {
        return canonicalDelegate != null ? canonicalDelegate.getStats() : super.getStats();
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
        if (canonicalDelegate != null) {
            return canonicalDelegate.close(kwargs);
        }
        // JDK HttpClient does not expose an explicit close hook.
        return CompletableFuture.completedFuture(null);
    }
}
