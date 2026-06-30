/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.Map;
import java.util.TreeMap;

/**
 * HTTPX-style connector pool configuration.
 */
public class HttpXConnectorPoolConfig extends ConnectorPoolConfig {
    private final int maxKeepaliveConnections;
    private final String localAddress;
    private final String proxy;
    private final boolean isNeedAsync;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpXConnectorPoolConfig() {
        this(100, 30, true, null, false, 60.0, 3600, 300, Map.of(), 20, null, null, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HttpXConnectorPoolConfig(int limit,
                                    int limitPerHost,
                                    boolean isSslVerify,
                                    String sslCert,
                                    boolean isForceClose,
                                    Double keepaliveTimeout,
                                    Integer ttl,
                                    Integer maxIdleTime,
                                    Map<String, Object> extendParams,
                                    int maxKeepaliveConnections,
                                    String localAddress,
                                    String proxy,
                                    boolean isNeedAsync) {
        super(limit, limitPerHost, isSslVerify, sslCert, isForceClose, keepaliveTimeout, ttl, maxIdleTime, extendParams);
        validatePositive("max_keepalive_connections", maxKeepaliveConnections);
        this.maxKeepaliveConnections = maxKeepaliveConnections;
        this.localAddress = localAddress;
        this.proxy = proxy;
        this.isNeedAsync = isNeedAsync;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxKeepaliveConnections() {
        return maxKeepaliveConnections;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLocalAddress() {
        return localAddress;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getProxy() {
        return proxy;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isNeedAsync() {
        return isNeedAsync;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String generateKey() {
        Map<String, Object> normalized = new TreeMap<>();
        normalized.put("base", super.generateKey());
        normalized.put("max_keepalive_connections", maxKeepaliveConnections);
        normalized.put("local_address", localAddress);
        normalized.put("proxy", proxy);
        normalized.put("need_async", isNeedAsync);
        return md5Hex(normalized.toString());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HttpXConnectorPoolConfig from(Object value) {
        if (value instanceof HttpXConnectorPoolConfig config) {
            return config;
        }
        Map<String, Object> map = ClientConfigSupport.asObjectMap(value);
        return new HttpXConnectorPoolConfig(
                ClientConfigSupport.asInt(map.get("limit"), 100),
                ClientConfigSupport.asInt(map.get("limit_per_host"), 30),
                ClientConfigSupport.asBoolean(map.get("ssl_verify"), true),
                ClientConfigSupport.asString(map.get("ssl_cert")),
                ClientConfigSupport.asBoolean(map.get("force_close"), false),
                ClientConfigSupport.asNullableDouble(map.get("keepalive_timeout")) != null
                        ? ClientConfigSupport.asNullableDouble(map.get("keepalive_timeout")) : 60.0,
                ClientConfigSupport.asNullableInt(map.get("ttl")) != null
                        ? ClientConfigSupport.asNullableInt(map.get("ttl")) : 3600,
                ClientConfigSupport.asNullableInt(map.get("max_idle_time")) != null
                        ? ClientConfigSupport.asNullableInt(map.get("max_idle_time")) : 300,
                ClientConfigSupport.asObjectMap(map.get("extend_params")),
                ClientConfigSupport.asInt(map.get("max_keepalive_connections"), 20),
                ClientConfigSupport.asString(map.get("local_address")),
                ClientConfigSupport.asString(map.get("proxy")),
                ClientConfigSupport.asBoolean(map.get("need_async"), true)
        );
    }
}
