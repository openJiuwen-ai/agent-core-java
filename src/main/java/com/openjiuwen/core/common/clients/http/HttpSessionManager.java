/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for HTTP sessions that handles creation, reuse, and cleanup.
 * <p>
 * This class maintains a pool of HTTP sessions and ensures that sessions
 * with identical configurations are reused, reducing resource usage.
 * <p>
 * Mirrors Python's {@code HttpSessionManager} from
 * {@code core/common/clients/http_client.py}.
 */
public class HttpSessionManager {

    private static final HttpSessionManager INSTANCE = new HttpSessionManager();
    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();
    private final SessionConfig defaultConfig = new SessionConfig();

    private HttpSessionManager() {
    }

    public static HttpSessionManager getInstance() {
        return INSTANCE;
    }

    public SessionConfig getDefaultConfig() {
        return defaultConfig;
    }

    public String getResourceKey(SessionConfig config) {
        return config.generateKey();
    }

    public HttpSession acquire(SessionConfig config) {
        String key = getResourceKey(config);
        HttpSession existing = sessions.get(key);
        if (existing != null && !existing.isClosed()) {
            existing.acquire();
            return existing;
        }
        if (existing != null) {
            sessions.remove(key);
        }
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
        HttpSession created = new HttpSession(client, config);
        sessions.put(key, created);
        return created;
    }

    public HttpSession acquire() {
        return acquire(defaultConfig);
    }

    public void release(SessionConfig config) {
        String key = getResourceKey(config);
        HttpSession session = sessions.get(key);
        if (session != null) {
            session.release();
            if (session.isClosed()) {
                sessions.remove(key);
            }
        }
    }

    public void releaseSession(SessionConfig config) {
        release(config);
    }

    public void clear() {
        sessions.values().forEach(HttpSession::close);
        sessions.clear();
    }
}
