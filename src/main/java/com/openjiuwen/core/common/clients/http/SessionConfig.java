/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.ConnectorPoolConfig;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for HTTP session.
 * <p>
 * Mirrors Python's {@code SessionConfig} class from
 * {@code common/clients/http_client.py}.
 */
public class SessionConfig {

    private ConnectorPoolConfig connectorPoolConfig = new ConnectorPoolConfig();
    private Map<String, String> headers = new HashMap<>();
    private String proxy = null;
    private Double timeout = null;
    private Double connectTimeout = null;
    private Map<String, String> timeoutArgs = new HashMap<>();
    private Object auth = null;
    private boolean raiseForStatus = false;
    private boolean trustEnv = true;
    private Map<String, Object> extendArgs = new HashMap<>();

    public SessionConfig() {
    }

    public SessionConfig(Double timeout, Double connectTimeout) {
        this.timeout = timeout;
        this.connectTimeout = connectTimeout;
    }

    public SessionConfig(Double timeout, Map<String, String> headers) {
        this.timeout = timeout;
        this.headers = headers;
    }

    public SessionConfig(Double timeout, Double connectTimeout, boolean raiseForStatus,
                         Map<String, String> headers, String proxy) {
        this.timeout = timeout;
        this.connectTimeout = connectTimeout;
        this.raiseForStatus = raiseForStatus;
        this.headers = headers;
        this.proxy = proxy;
    }

    // Getters
    public ConnectorPoolConfig getConnectorPoolConfig() { return connectorPoolConfig; }
    public Map<String, String> getHeaders() { return headers; }
    public String getProxy() { return proxy; }
    public Double getTimeout() { return timeout; }
    public Double getConnectTimeout() { return connectTimeout; }
    public Map<String, String> getTimeoutArgs() { return timeoutArgs; }
    public Object getAuth() { return auth; }
    public boolean isRaiseForStatus() { return raiseForStatus; }
    public boolean isTrustEnv() { return trustEnv; }
    public Map<String, Object> getExtendArgs() { return extendArgs; }

    // Setters
    public void setConnectorPoolConfig(ConnectorPoolConfig connectorPoolConfig) { this.connectorPoolConfig = connectorPoolConfig; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public void setProxy(String proxy) { this.proxy = proxy; }
    public void setTimeout(Double timeout) { this.timeout = timeout; }
    public void setConnectTimeout(Double connectTimeout) { this.connectTimeout = connectTimeout; }
    public void setTimeoutArgs(Map<String, String> timeoutArgs) { this.timeoutArgs = timeoutArgs; }
    public void setAuth(Object auth) { this.auth = auth; }
    public void setRaiseForStatus(boolean raiseForStatus) { this.raiseForStatus = raiseForStatus; }
    public void setTrustEnv(boolean trustEnv) { this.trustEnv = trustEnv; }
    public void setExtendArgs(Map<String, Object> extendArgs) { this.extendArgs = extendArgs; }

    /**
     * Generate a unique key based on the session configuration.
     */
    public String generateKey() {
        StringBuilder parts = new StringBuilder();

        if (connectorPoolConfig != null) {
            parts.append(connectorPoolConfig.generateKey());
        }

        if (headers != null && !headers.isEmpty()) {
            parts.append("&headers:").append(headers.toString());
        }

        if (proxy != null) {
            parts.append("&proxy:").append(proxy);
        }

        if (timeout != null) {
            parts.append("&timeout:").append(timeout);
        }

        if (connectTimeout != null) {
            parts.append("&connectTimeout:").append(connectTimeout);
        }

        parts.append("&raiseForStatus:").append(raiseForStatus);

        String keyStr = parts.toString();

        // Hash the key if it's too long
        if (keyStr.length() > 256) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(keyStr.getBytes());
                StringBuilder hexString = new StringBuilder();
                for (byte b : digest) {
                    hexString.append(String.format("%02x", b));
                }
                return hexString.toString();
            } catch (Exception e) {
                return keyStr.substring(0, 256);
            }
        }

        return keyStr;
    }
}