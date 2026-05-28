/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import java.net.http.HttpClient;

/**
 * Wrapper for HTTP session with reference counting support.
 * <p>
 * Mirrors Python's {@code HttpSession} from
 * {@code core/common/clients/http_client.py}.
 */
public class HttpSession implements AutoCloseable {

    private final HttpClient session;
    private final SessionConfig config;
    private volatile boolean closed = false;
    private int refCount = 0;

    public HttpSession(HttpClient session, SessionConfig config) {
        this.session = session;
        this.config = config;
    }

    public SessionConfig getConfig() {
        return config;
    }

    public HttpClient session() {
        if (closed) {
            throw new RuntimeException("Session is closed");
        }
        return session;
    }

    public synchronized void acquire() {
        refCount++;
    }

    public synchronized void release() {
        refCount--;
        if (refCount <= 0) {
            close();
        }
    }

    public synchronized int getRefCount() {
        return refCount;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
