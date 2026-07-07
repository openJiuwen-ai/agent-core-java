/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseRefResourceMgr;
import com.openjiuwen.core.common.clients.SessionConfig;
import com.openjiuwen.core.common.utils.Singleton;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Manager for HTTP sessions that handles creation, reuse, and cleanup.
 *
 * <p>Mirrors Python's {@code HttpSessionManager} in
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 */
public class HttpSessionManager extends BaseRefResourceMgr<HttpSession> {

    private final SessionConfig defaultConfig = new SessionConfig();

    public static HttpSessionManager getInstance() {
        return Singleton.getInstance(HttpSessionManager.class, HttpSessionManager::new);
    }

    public static HttpSessionManager getHttpSessionManager() {
        return getInstance();
    }

    public SessionConfig getDefaultConfig() {
        return defaultConfig;
    }

    public String getResourceKey(SessionConfig config) {
        return normalizeConfig(config).generateKey();
    }

    @Override
    protected String getResourceKey(Object config) {
        return getResourceKey(config instanceof SessionConfig sessionConfig ? sessionConfig : defaultConfig);
    }

    public HttpSession acquire() {
        return acquire(defaultConfig);
    }

    public HttpSession acquire(SessionConfig config) {
        ResourceLease<HttpSession> lease = acquireLease(config).join();
        lease.resource().setLastLease(lease);
        return lease.resource();
    }

    public CompletableFuture<ResourceLease<HttpSession>> acquireLease(SessionConfig config) {
        return super.acquire(normalizeConfig(config));
    }

    @Override
    protected CompletableFuture<HttpSession> createResource(Object config) {
        SessionConfig sessionConfig = normalizeConfig(config instanceof SessionConfig candidate ? candidate : defaultConfig);
        return CompletableFuture.completedFuture(new HttpSession(buildClient(sessionConfig), sessionConfig, true));
    }

    public <T> CompletableFuture<T> withSession(
            SessionConfig config,
            Function<HttpSession, CompletableFuture<T>> body) {
        SessionConfig effectiveConfig = normalizeConfig(config);
        return acquireLease(effectiveConfig).thenCompose(lease -> {
            CompletableFuture<T> result;
            try {
                result = body.apply(lease.resource());
            } catch (Exception exception) {
                result = CompletableFuture.failedFuture(exception);
            }
            return result.whenComplete((ignored, error) -> releaseSession(effectiveConfig).join());
        });
    }

    public CompletableFuture<Void> releaseSession(SessionConfig config) {
        return release((Object) normalizeConfig(config));
    }

    public void release(SessionConfig config) {
        release((Object) normalizeConfig(config)).join();
    }

    public void clear() {
        closeAll().join();
    }

    private java.net.http.HttpClient buildClient(SessionConfig config) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
        if (config.getConnectTimeout() != null) {
            builder.connectTimeout(seconds(config.getConnectTimeout()));
        }
        if (config.getProxy() != null && !config.getProxy().isBlank()) {
            builder.proxy(proxySelector(config.getProxy()));
        }
        return builder.build();
    }

    private SessionConfig normalizeConfig(SessionConfig config) {
        return config == null ? defaultConfig : config;
    }

    private static Duration seconds(Double value) {
        return Duration.ofMillis(Math.max(0L, Math.round(value * 1000.0d)));
    }

    private static ProxySelector proxySelector(String proxy) {
        URI uri = proxy.contains("://") ? URI.create(proxy) : URI.create("http://" + proxy);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
    }
}
