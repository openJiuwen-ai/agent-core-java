/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.Map;

/**
 * Configuration class for HTTPX connector pools.
 *
 * <p>Mirrors Python's {@code HttpXConnectorPoolConfig} in
 * {@code openjiuwen/core/common/clients/llm_client.py}.</p>
 */
public class HttpXConnectorPoolConfig extends ConnectorPoolConfig {

    private int maxKeepaliveConnections = 20;
    private String localAddress;
    private String proxy;
    private boolean needAsync = true;

    public HttpXConnectorPoolConfig() {
        super();
    }

    public HttpXConnectorPoolConfig(Map<String, Object> values) {
        super();
        apply(values);
    }

    public final void apply(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (values.containsKey("limit")) {
            setLimit(intValue(values.get("limit"), getLimit()));
        }
        if (values.containsKey("limit_per_host") || values.containsKey("limitPerHost")) {
            setLimitPerHost(intValue(first(values, "limit_per_host", "limitPerHost"), getLimitPerHost()));
        }
        if (values.containsKey("ssl_verify") || values.containsKey("sslVerify")) {
            setSslVerify(booleanValue(first(values, "ssl_verify", "sslVerify"), isSslVerify()));
        }
        if (values.containsKey("ssl_cert") || values.containsKey("sslCert")) {
            setSslCert(stringValue(first(values, "ssl_cert", "sslCert")));
        }
        if (values.containsKey("force_close") || values.containsKey("forceClose")) {
            setForceClose(booleanValue(first(values, "force_close", "forceClose"), isForceClose()));
        }
        if (values.containsKey("keepalive_timeout") || values.containsKey("keepaliveTimeout")) {
            setKeepaliveTimeout(doubleValue(first(values, "keepalive_timeout", "keepaliveTimeout")));
        }
        if (values.containsKey("max_keepalive_connections") || values.containsKey("maxKeepaliveConnections")) {
            setMaxKeepaliveConnections(intValue(
                    first(values, "max_keepalive_connections", "maxKeepaliveConnections"),
                    maxKeepaliveConnections));
        }
        if (values.containsKey("local_address") || values.containsKey("localAddress")) {
            setLocalAddress(stringValue(first(values, "local_address", "localAddress")));
        }
        if (values.containsKey("proxy")) {
            setProxy(stringValue(values.get("proxy")));
        }
        if (values.containsKey("need_async") || values.containsKey("needAsync")) {
            setNeedAsync(booleanValue(first(values, "need_async", "needAsync"), needAsync));
        }
        if (values.containsKey("extend_params") || values.containsKey("extendParams")) {
            Object extend = first(values, "extend_params", "extendParams");
            if (extend instanceof Map<?, ?> map) {
                setExtendParams(toObjectMap(map));
            }
        }
    }

    @Override
    public String generateKey() {
        return super.generateKey()
                + "&max_keepalive_connections:" + maxKeepaliveConnections
                + (localAddress == null ? "" : "&local_address:" + localAddress)
                + (proxy == null ? "" : "&proxy:" + proxy)
                + "&need_async:" + String.valueOf(needAsync).toLowerCase();
    }

    public int getMaxKeepaliveConnections() {
        return maxKeepaliveConnections;
    }

    public void setMaxKeepaliveConnections(int maxKeepaliveConnections) {
        if (maxKeepaliveConnections < 1) {
            throw new IllegalArgumentException("max_keepalive_connections must be >= 1");
        }
        this.maxKeepaliveConnections = maxKeepaliveConnections;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public boolean isNeedAsync() {
        return needAsync;
    }

    public void setNeedAsync(boolean needAsync) {
        this.needAsync = needAsync;
    }

    private static Object first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> toObjectMap(Map<?, ?> values) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }
}
