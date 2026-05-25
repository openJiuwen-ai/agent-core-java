/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for HTTP session.
 * <p>
 * Mirrors Python's {@code SessionConfig} class from
 * <code>common/clients/http_client.py</code>.
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
        parts.append("&trustEnv:").append(trustEnv);

        String keyStr = parts.toString();
        if (keyStr.length() > 256) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(keyStr.getBytes());
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (Exception e) {
                return String.valueOf(keyStr.hashCode());
            }
        }

        return keyStr;
    }

    // Getters and setters
    public ConnectorPoolConfig getConnectorPoolConfig() { return connectorPoolConfig; }
    public void setConnectorPoolConfig(ConnectorPoolConfig config) { this.connectorPoolConfig = config; }
    
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    
    public String getProxy() { return proxy; }
    public void setProxy(String proxy) { this.proxy = proxy; }
    
    public Double getTimeout() { return timeout; }
    public void setTimeout(Double timeout) { this.timeout = timeout; }
    
    public Double getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Double connectTimeout) { this.connectTimeout = connectTimeout; }
    
    public boolean isRaiseForStatus() { return raiseForStatus; }
    public void setRaiseForStatus(boolean raiseForStatus) { this.raiseForStatus = raiseForStatus; }
    
    public boolean isTrustEnv() { return trustEnv; }
    public void setTrustEnv(boolean trustEnv) { this.trustEnv = trustEnv; }
    
    public Map<String, Object> getExtendArgs() { return extendArgs; }
    public void setExtendArgs(Map<String, Object> extendArgs) { this.extendArgs = extendArgs; }
}