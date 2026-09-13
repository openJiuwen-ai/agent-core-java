/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.utils.SingletonSupport;

/**
 * Shared HTTP session manager.
 * 
 * @since 0.1.7
 */
public final class HttpSessionManager extends BaseRefResourceMgr<HttpSession, SessionConfig> {
    private final SessionConfig defaultConfig = new SessionConfig();

    /**
     * HttpSessionManager.
     * 
     * @since 0.1.7
     */
    private HttpSessionManager() {
    }

    /**
     * getInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static HttpSessionManager getInstance() {
        return SingletonSupport.getInstance(HttpSessionManager.class, HttpSessionManager::new);
    }

    /**
     * getSession.
     * 
     * @param config config
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Acquisition<HttpSession> getSession(SessionConfig config) throws Exception {
        return acquire(config != null ? config : defaultConfig);
    }

    /**
     * releaseSession.
     * 
     * @param config config
     * @throws Exception Exception
     * @since 0.1.7
     */
    public void releaseSession(SessionConfig config) throws Exception {
        release(config != null ? config : defaultConfig);
    }

    /**
     * getResourceKey.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String getResourceKey(SessionConfig config) {
        return (config != null ? config : defaultConfig).generateKey();
    }

    /**
     * createResource.
     * 
     * @param config config
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
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

    /**
     * resolvePoolConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static ConnectorPoolConfig resolvePoolConfig(SessionConfig config) {
        ConnectorPoolConfig poolConfig = config.getConnectorPoolConfig();
        if (config.getProxy() == null || config.getProxy().isBlank()) {
            return poolConfig;
        }
        if (poolConfig instanceof HttpXConnectorPoolConfig typed) {
            return new HttpXConnectorPoolConfig(typed.getLimit(), typed.getLimitPerHost(), typed.isSslVerify(),
                    typed.getSslCert(), typed.isForceClose(), typed.getKeepaliveTimeout(), typed.getTtl(),
                    typed.getMaxIdleTime(), typed.getExtendParams(), typed.getMaxKeepaliveConnections(),
                    typed.getLocalAddress(), config.getProxy(), typed.isNeedAsync());
        }
        return new HttpXConnectorPoolConfig(poolConfig.getLimit(), poolConfig.getLimitPerHost(),
                poolConfig.isSslVerify(), poolConfig.getSslCert(), poolConfig.isForceClose(),
                poolConfig.getKeepaliveTimeout(), poolConfig.getTtl(), poolConfig.getMaxIdleTime(),
                poolConfig.getExtendParams(), 20, null, config.getProxy(), true);
    }
}
