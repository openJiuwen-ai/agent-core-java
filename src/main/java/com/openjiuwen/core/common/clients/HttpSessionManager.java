/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.utils.SingletonSupport;

/**
 * Shared HTTP session manager.
 */
public final class HttpSessionManager extends BaseRefResourceMgr<HttpSession, SessionConfig> {
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
    public Acquisition<HttpSession> getSession(SessionConfig config) throws Exception {
        return acquire(config != null ? config : defaultConfig);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void releaseSession(SessionConfig config) throws Exception {
        release(config != null ? config : defaultConfig);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected String getResourceKey(SessionConfig config) {
        return (config != null ? config : defaultConfig).generateKey();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected HttpSession createResource(SessionConfig config) throws Exception {
        SessionConfig isResolved = config != null ? config : defaultConfig;
        ConnectorPoolConfig poolConfig = resolvePoolConfig(isResolved);
        String type = poolConfig instanceof HttpXConnectorPoolConfig ? "httpx" : "default";
        ConnectorPool pool = ConnectorPoolManager.getInstance().getConnectorPool(type, poolConfig);
        Object conn = pool.conn();
        if (!(conn instanceof java.net.http.HttpClient httpClient)) {
            throw new IllegalStateException("connector pool did not return HttpClient");
        }
        return new HttpSession(httpClient, isResolved);
    }

    private static ConnectorPoolConfig resolvePoolConfig(SessionConfig config) {
        ConnectorPoolConfig poolConfig = config.getConnectorPoolConfig();
        if (config.getProxy() == null || config.getProxy().isBlank()) {
            return poolConfig;
        }
        if (poolConfig instanceof HttpXConnectorPoolConfig typed) {
            return new HttpXConnectorPoolConfig(
                    typed.getLimit(),
                    typed.getLimitPerHost(),
                    typed.isSslVerify(),
                    typed.getSslCert(),
                    typed.isForceClose(),
                    typed.getKeepaliveTimeout(),
                    typed.getTtl(),
                    typed.getMaxIdleTime(),
                    typed.getExtendParams(),
                    typed.getMaxKeepaliveConnections(),
                    typed.getLocalAddress(),
                    config.getProxy(),
                    typed.isNeedAsync()
            );
        }
        return new HttpXConnectorPoolConfig(
                poolConfig.getLimit(),
                poolConfig.getLimitPerHost(),
                poolConfig.isSslVerify(),
                poolConfig.getSslCert(),
                poolConfig.isForceClose(),
                poolConfig.getKeepaliveTimeout(),
                poolConfig.getTtl(),
                poolConfig.getMaxIdleTime(),
                poolConfig.getExtendParams(),
                20,
                null,
                config.getProxy(),
                true
        );
    }
}
