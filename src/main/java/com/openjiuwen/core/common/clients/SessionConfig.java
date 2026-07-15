/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * HTTP session configuration.
 * 
 * @since 0.1.7
 */
public class SessionConfig {
    private final ConnectorPoolConfig connectorPoolConfig;
    private final Map<String, String> headers;
    private final String proxy;
    private final Double timeout;
    private final Double connectTimeout;
    private final Map<String, Object> timeoutArgs;
    private final Object auth;
    private final boolean isRaiseForStatusEnabled;
    private final boolean isTrustEnvEnabled;
    private final Map<String, Object> extendArgs;

    /**
     * SessionConfig.
     * 
     * @since 0.1.7
     */
    public SessionConfig() {
        this(new ConnectorPoolConfig(), Map.of(), null, null, null, Map.of(), null, false, true, Map.of());
    }

    /**
     * SessionConfig.
     * 
     * @param connectorPoolConfig connectorPoolConfig
     * @param headers headers
     * @param proxy proxy
     * @param timeout timeout
     * @param connectTimeout connectTimeout
     * @param timeoutArgs timeoutArgs
     * @param auth auth
     * @param isRaiseForStatusEnabled isRaiseForStatusEnabled
     * @param isTrustEnvEnabled isTrustEnvEnabled
     * @param extendArgs extendArgs
     * @since 0.1.7
     */
    public SessionConfig(ConnectorPoolConfig connectorPoolConfig, Map<String, String> headers, String proxy,
            Double timeout, Double connectTimeout, Map<String, Object> timeoutArgs, Object auth,
            boolean isRaiseForStatusEnabled, boolean isTrustEnvEnabled, Map<String, Object> extendArgs) {
        this.connectorPoolConfig = connectorPoolConfig != null ? connectorPoolConfig : new ConnectorPoolConfig();
        this.headers = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>();
        this.proxy = proxy;
        this.timeout = timeout;
        this.connectTimeout = connectTimeout;
        this.timeoutArgs = timeoutArgs != null ? new LinkedHashMap<>(timeoutArgs) : new LinkedHashMap<>();
        this.auth = auth;
        this.isRaiseForStatusEnabled = isRaiseForStatusEnabled;
        this.isTrustEnvEnabled = isTrustEnvEnabled;
        this.extendArgs = extendArgs != null ? new LinkedHashMap<>(extendArgs) : new LinkedHashMap<>();
    }

    /**
     * getConnectorPoolConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ConnectorPoolConfig getConnectorPoolConfig() {
        return connectorPoolConfig;
    }

    /**
     * getHeaders.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, String> getHeaders() {
        return new LinkedHashMap<>(headers);
    }

    /**
     * getProxy.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getProxy() {
        return proxy;
    }

    /**
     * getTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getTimeout() {
        return timeout;
    }

    /**
     * getConnectTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * getTimeoutArgs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getTimeoutArgs() {
        return new LinkedHashMap<>(timeoutArgs);
    }

    /**
     * getAuth.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getAuth() {
        return auth;
    }

    /**
     * isRaiseForStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRaiseForStatus() {
        return isRaiseForStatusEnabled;
    }

    /**
     * isTrustEnv.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isTrustEnv() {
        return isTrustEnvEnabled;
    }

    /**
     * getExtendArgs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtendArgs() {
        return new LinkedHashMap<>(extendArgs);
    }

    /**
     * generateKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String generateKey() {
        Map<String, Object> normalized = new TreeMap<>();
        normalized.put("connector_pool_config", connectorPoolConfig.generateKey());
        normalized.put("headers", new TreeMap<>(headers));
        normalized.put("proxy", proxy);
        normalized.put("timeout", timeout);
        normalized.put("connect_timeout", connectTimeout);
        normalized.put("timeout_args", new TreeMap<>(timeoutArgs));
        normalized.put("auth", auth == null ? null : String.valueOf(auth));
        normalized.put("raise_for_status", isRaiseForStatusEnabled);
        normalized.put("trust_env", isTrustEnvEnabled);
        normalized.put("extend_args", new TreeMap<>(extendArgs));
        return ConnectorPoolConfig.md5Hex(normalized.toString());
    }

    /**
     * from.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static SessionConfig from(Object value) {
        if (value instanceof SessionConfig config) {
            return config;
        }
        Map<String, Object> map = ClientConfigSupport.asObjectMap(value);
        return new SessionConfig(resolveConnectorPoolConfig(map.get("connector_pool_config")),
                ClientConfigSupport.asStringMap(map.get("headers")), ClientConfigSupport.asString(map.get("proxy")),
                ClientConfigSupport.asNullableDouble(map.get("timeout")),
                ClientConfigSupport.asNullableDouble(map.get("connect_timeout")),
                ClientConfigSupport.asObjectMap(map.get("timeout_args")), map.get("auth"),
                ClientConfigSupport.asBoolean(map.get("raise_for_status"), false),
                ClientConfigSupport.asBoolean(map.get("trust_env"), true),
                ClientConfigSupport.asObjectMap(map.get("extend_args")));
    }

    /**
     * resolveConnectorPoolConfig.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static ConnectorPoolConfig resolveConnectorPoolConfig(Object value) {
        if (value instanceof HttpXConnectorPoolConfig httpx) {
            return httpx;
        }
        if (value instanceof ConnectorPoolConfig config) {
            return config;
        }
        Map<String, Object> map = ClientConfigSupport.asObjectMap(value);
        if (map.containsKey("proxy") || map.containsKey("max_keepalive_connections") || map.containsKey("local_address")
                || map.containsKey("need_async")) {
            return HttpXConnectorPoolConfig.from(map);
        }
        return ConnectorPoolConfig.from(map);
    }
}
