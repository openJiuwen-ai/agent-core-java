/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.RefCountedResource;
import com.openjiuwen.core.common.clients.SessionConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Wrapper for an HTTP client session with reference counting support.
 *
 * <p>Mirrors Python's {@code HttpSession} in
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 */
public class HttpSession extends RefCountedResource {

    private final java.net.http.HttpClient session;
    private final SessionConfig config;

    public HttpSession(java.net.http.HttpClient session, SessionConfig config) {
        this.session = session;
        this.config = config;
    }

    public SessionConfig getConfig() {
        return config;
    }

    public SessionConfig config() {
        return config;
    }

    public java.net.http.HttpClient session() {
        if (isClosed()) {
            throw new IllegalStateException("Session is closed");
        }
        return session;
    }

    @Override
    protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }
}
