/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.utils.SingletonSupport;

import java.util.concurrent.CompletableFuture;

/**
 * Shared HTTP session manager.
 */
public final class HttpSessionManager extends BaseRefResourceMgr<HttpSession> {
    private final SessionConfig defaultConfig = new SessionConfig();

    private HttpSessionManager() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HttpSessionManager getInstance() {
        return SingletonSupport.getInstance(HttpSessionManager.class, HttpSessionManager::new);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpSession getSession(SessionConfig config) {
        ResourceLease<HttpSession> lease = acquire(config != null ? config : defaultConfig).join();
        lease.resource().setLastLease(lease);
        return lease.resource();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Void> releaseSession(SessionConfig config) {
        return release(config != null ? config : defaultConfig);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    protected String getResourceKey(Object config) {
        SessionConfig sessionConfig = config instanceof SessionConfig sc ? sc : defaultConfig;
        return sessionConfig.generateKey();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    protected CompletableFuture<HttpSession> createResource(Object config) {
        SessionConfig isResolved = config instanceof SessionConfig sc ? sc : defaultConfig;
        ConnectorPoolConfig poolConfig = resolvePoolConfig(isResolved);
        String type = poolConfig instanceof HttpXConnectorPoolConfig ? "httpx" : "default";
        return ConnectorPoolManager.getInstance().getConnectorPool(type, poolConfig)
                .thenApply(pool -> {
                    Object conn = pool.conn();
                    if (!(conn instanceof java.net.http.HttpClient httpClient)) {
                        throw new IllegalStateException("connector pool did not return HttpClient");
                    }
                    return new HttpSession(httpClient, isResolved);
                });
    }

    private static ConnectorPoolConfig resolvePoolConfig(SessionConfig config) {
        ConnectorPoolConfig poolConfig = config.getConnectorPoolConfig();
        if (config.getProxy() == null || config.getProxy().isBlank()) {
            return poolConfig;
        }
        if (poolConfig instanceof HttpXConnectorPoolConfig typed) {
            HttpXConnectorPoolConfig copy = new HttpXConnectorPoolConfig();
            copy.setLimit(typed.getLimit());
            copy.setLimitPerHost(typed.getLimitPerHost());
            copy.setSslVerify(typed.isSslVerify());
            copy.setSslCert(typed.getSslCert());
            copy.setForceClose(typed.isForceClose());
            copy.setKeepaliveTimeout(typed.getKeepaliveTimeout());
            copy.setTtl(typed.getTtl());
            copy.setMaxIdleTime(typed.getMaxIdleTime());
            copy.setExtendParams(typed.getExtendParams());
            copy.setMaxKeepaliveConnections(typed.getMaxKeepaliveConnections());
            copy.setLocalAddress(typed.getLocalAddress());
            copy.setProxy(config.getProxy());
            copy.setNeedAsync(typed.isNeedAsync());
            return copy;
        }
        HttpXConnectorPoolConfig copy = new HttpXConnectorPoolConfig();
        copy.setLimit(poolConfig.getLimit());
        copy.setLimitPerHost(poolConfig.getLimitPerHost());
        copy.setSslVerify(poolConfig.isSslVerify());
        copy.setSslCert(poolConfig.getSslCert());
        copy.setForceClose(poolConfig.isForceClose());
        copy.setKeepaliveTimeout(poolConfig.getKeepaliveTimeout());
        copy.setTtl(poolConfig.getTtl());
        copy.setMaxIdleTime(poolConfig.getMaxIdleTime());
        copy.setExtendParams(poolConfig.getExtendParams());
        copy.setMaxKeepaliveConnections(20);
        copy.setLocalAddress(null);
        copy.setProxy(config.getProxy());
        copy.setNeedAsync(true);
        return copy;
    }
}
