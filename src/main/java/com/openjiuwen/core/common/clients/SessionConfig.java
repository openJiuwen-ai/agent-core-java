/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for HTTP sessions.
 *
 * <p>Mirrors Python's {@code SessionConfig} in
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 */
public class SessionConfig {

    private ConnectorPoolConfig connectorPoolConfig = new ConnectorPoolConfig();
    private Map<String, String> headers;
    private String proxy;
    private Double timeout;
    private Double connectTimeout;
    private Map<String, String> timeoutArgs = new LinkedHashMap<>();
    private Object auth;
    private boolean raiseForStatus;
    private boolean trustEnv = true;
    private Map<String, Object> extendArgs = new LinkedHashMap<>();

    public SessionConfig() {
    }

    public SessionConfig(Map<String, Object> values) {
        apply(values);
    }

    public final void apply(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Object connectorConfig = first(values, "connector_pool_config", "connectorPoolConfig");
        if (connectorConfig instanceof ConnectorPoolConfig config) {
            connectorPoolConfig = config;
        } else if (connectorConfig instanceof Map<?, ?> map) {
            ConnectorPoolConfig config = new ConnectorPoolConfig();
            applyConnectorPoolConfig(config, map);
            connectorPoolConfig = config;
        }
        headers = stringMap(first(values, "headers"));
        proxy = stringValue(first(values, "proxy"));
        timeout = doubleValue(first(values, "timeout"));
        connectTimeout = doubleValue(first(values, "connect_timeout", "connectTimeout"));
        Map<String, String> timeoutMap = stringMap(first(values, "timeout_args", "timeoutArgs"));
        timeoutArgs = timeoutMap == null ? new LinkedHashMap<>() : timeoutMap;
        auth = first(values, "auth");
        raiseForStatus = booleanValue(first(values, "raise_for_status", "raiseForStatus"), false);
        trustEnv = booleanValue(first(values, "trust_env", "trustEnv"), true);
        Map<String, Object> extendMap = objectMap(first(values, "extend_args", "extendArgs"));
        extendArgs = extendMap == null ? new LinkedHashMap<>() : extendMap;
    }

    public String generateKey() {
        List<String> parts = new ArrayList<>();
        Map<String, Object> dump = modelDump();
        for (Map.Entry<String, Object> entry : dump.entrySet()) {
            Object fieldValue = entry.getValue();
            if (fieldValue == null) {
                continue;
            }
            if ("connector_pool_config".equals(entry.getKey()) && fieldValue instanceof ConnectorPoolConfig config) {
                parts.add(config.generateKey());
                continue;
            }
            parts.add(entry.getKey() + ":" + formatValue(fieldValue));
        }
        parts.sort(String::compareTo);
        String key = String.join("&", parts);
        if (key.length() > 256) {
            return md5Hex(key);
        }
        return key;
    }

    Map<String, Object> modelDump() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("connector_pool_config", connectorPoolConfig);
        values.put("headers", headers);
        values.put("proxy", proxy);
        values.put("timeout", timeout);
        values.put("connect_timeout", connectTimeout);
        values.put("timeout_args", timeoutArgs);
        values.put("auth", auth);
        values.put("raise_for_status", raiseForStatus);
        values.put("trust_env", trustEnv);
        values.put("extend_args", extendArgs);
        return values;
    }

    private static String formatValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> items = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                items.add("(" + entry.getKey() + ", " + entry.getValue() + ")");
            }
            items.sort(String::compareTo);
            return items.toString();
        }
        if (value instanceof Collection<?> collection) {
            List<String> items = new ArrayList<>();
            for (Object item : collection) {
                items.add(String.valueOf(item));
            }
            items.sort(String::compareTo);
            return items.toString();
        }
        if (value instanceof Boolean bool) {
            return String.valueOf(bool).toLowerCase();
        }
        return String.valueOf(value);
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte current : bytes) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (Exception exception) {
            return Integer.toString(value.hashCode());
        }
    }

    private static void applyConnectorPoolConfig(ConnectorPoolConfig config, Map<?, ?> values) {
        Map<String, Object> normalized = objectMap(values);
        if (normalized.containsKey("limit")) {
            config.setLimit(intValue(normalized.get("limit"), config.getLimit()));
        }
        if (normalized.containsKey("limit_per_host") || normalized.containsKey("limitPerHost")) {
            config.setLimitPerHost(intValue(first(normalized, "limit_per_host", "limitPerHost"), config.getLimitPerHost()));
        }
        if (normalized.containsKey("ssl_verify") || normalized.containsKey("sslVerify")) {
            config.setSslVerify(booleanValue(first(normalized, "ssl_verify", "sslVerify"), config.isSslVerify()));
        }
        if (normalized.containsKey("ssl_cert") || normalized.containsKey("sslCert")) {
            config.setSslCert(stringValue(first(normalized, "ssl_cert", "sslCert")));
        }
        if (normalized.containsKey("force_close") || normalized.containsKey("forceClose")) {
            config.setForceClose(booleanValue(first(normalized, "force_close", "forceClose"), config.isForceClose()));
        }
        if (normalized.containsKey("keepalive_timeout") || normalized.containsKey("keepaliveTimeout")) {
            config.setKeepaliveTimeout(doubleValue(first(normalized, "keepalive_timeout", "keepaliveTimeout")));
        }
        if (normalized.containsKey("ttl")) {
            config.setTtl(integerValue(normalized.get("ttl")));
        }
        if (normalized.containsKey("max_idle_time") || normalized.containsKey("maxIdleTime")) {
            config.setMaxIdleTime(integerValue(first(normalized, "max_idle_time", "maxIdleTime")));
        }
        if (normalized.containsKey("extend_params") || normalized.containsKey("extendParams")) {
            config.setExtendParams(objectMap(first(normalized, "extend_params", "extendParams")));
        }
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

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static int intValue(Object value, int defaultValue) {
        Integer parsed = integerValue(value);
        return parsed == null ? defaultValue : parsed;
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

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    public ConnectorPoolConfig getConnectorPoolConfig() {
        return connectorPoolConfig;
    }

    public void setConnectorPoolConfig(ConnectorPoolConfig connectorPoolConfig) {
        this.connectorPoolConfig = connectorPoolConfig == null ? new ConnectorPoolConfig() : connectorPoolConfig;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? null : new LinkedHashMap<>(headers);
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public Double getTimeout() {
        return timeout;
    }

    public void setTimeout(Double timeout) {
        this.timeout = timeout;
    }

    public Double getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Double connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Map<String, String> getTimeoutArgs() {
        return timeoutArgs;
    }

    public void setTimeoutArgs(Map<String, String> timeoutArgs) {
        this.timeoutArgs = timeoutArgs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(timeoutArgs);
    }

    public Object getAuth() {
        return auth;
    }

    public void setAuth(Object auth) {
        this.auth = auth;
    }

    public boolean isRaiseForStatus() {
        return raiseForStatus;
    }

    public void setRaiseForStatus(boolean raiseForStatus) {
        this.raiseForStatus = raiseForStatus;
    }

    public boolean isTrustEnv() {
        return trustEnv;
    }

    public void setTrustEnv(boolean trustEnv) {
        this.trustEnv = trustEnv;
    }

    public Map<String, Object> getExtendArgs() {
        return extendArgs;
    }

    public void setExtendArgs(Map<String, Object> extendArgs) {
        this.extendArgs = extendArgs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extendArgs);
    }
}
