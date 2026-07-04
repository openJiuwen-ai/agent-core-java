/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.security.SslUtils;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for connector pools.
 * <p>
 * Mirrors Python's {@code ConnectorPoolConfig} in
 * {@code openjiuwen/core/common/clients/connector_pool.py}.
 */
public class ConnectorPoolConfig {

    private int limit = 100;
    private int limitPerHost = 30;
    private boolean sslVerify = true;
    private String sslCert = null;
    private boolean forceClose = false;
    private Double keepaliveTimeout = 60.0;
    private Integer ttl = 3600;
    private Integer maxIdleTime = 300;
    private Map<String, Object> extendParams = new LinkedHashMap<>();

    public ConnectorPoolConfig() {
    }

    public ConnectorPoolConfig(int limit, int limitPerHost) {
        this.limit = validatePositive(limit, "limit");
        this.limitPerHost = validatePositive(limitPerHost, "limitPerHost");
    }

    private int validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private double validatePositive(double value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    /**
     * Generate a unique key for this configuration.
     *
     * @return a MD5 hash key that uniquely identifies this configuration
     */
    public String generateKey() {
        List<String> parts = new ArrayList<>();
        parts.add("extend_params:" + formatExtendParams());
        parts.add("force_close:" + String.valueOf(forceClose).toLowerCase());
        parts.add("keepalive_timeout:" + String.valueOf(keepaliveTimeout));
        parts.add("limit:" + limit);
        parts.add("limit_per_host:" + limitPerHost);
        parts.add("max_idle_time:" + String.valueOf(maxIdleTime));
        parts.add("ssl_verify:" + String.valueOf(sslVerify).toLowerCase());
        parts.add("ttl:" + String.valueOf(ttl));
        if (sslCert != null) {
            parts.add("ssl_cert:" + sslCert);
        }
        String keyStr = String.join("&", parts);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(keyStr.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return Integer.toString(keyStr.hashCode());
        }
    }

    public Object createSslContext() {
        if (!sslVerify) {
            return Boolean.FALSE;
        }
        return SslUtils.createStrictSslContext(sslCert);
    }

    private String formatExtendParams() {
        if (extendParams == null || extendParams.isEmpty()) {
            return "{}";
        }
        return extendParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "(" + entry.getKey() + ", " + entry.getValue() + ")")
                .reduce((left, right) -> left + ", " + right)
                .map(joined -> "[" + joined + "]")
                .orElse("{}");
    }

    // Getters and setters
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = validatePositive(limit, "limit"); }
    
    public int getLimitPerHost() { return limitPerHost; }
    public void setLimitPerHost(int limitPerHost) { 
        this.limitPerHost = validatePositive(limitPerHost, "limitPerHost"); 
    }
    
    public boolean isSslVerify() { return sslVerify; }
    public void setSslVerify(boolean sslVerify) { this.sslVerify = sslVerify; }
    
    public String getSslCert() { return sslCert; }
    public void setSslCert(String sslCert) { this.sslCert = sslCert; }
    
    public boolean isForceClose() { return forceClose; }
    public void setForceClose(boolean forceClose) { this.forceClose = forceClose; }
    
    public Double getKeepaliveTimeout() { return keepaliveTimeout; }
    public void setKeepaliveTimeout(Double keepaliveTimeout) {
        this.keepaliveTimeout = keepaliveTimeout == null ? null
                : validatePositive(keepaliveTimeout, "keepaliveTimeout");
    }

    public void setKeepaliveTimeout(double keepaliveTimeout) {
        setKeepaliveTimeout(Double.valueOf(keepaliveTimeout));
    }
    
    public Integer getTtl() { return ttl; }
    public void setTtl(Integer ttl) { this.ttl = ttl == null ? null : validatePositive(ttl, "ttl"); }

    public void setTtl(int ttl) {
        setTtl(Integer.valueOf(ttl));
    }
    
    public Integer getMaxIdleTime() { return maxIdleTime; }
    public void setMaxIdleTime(Integer maxIdleTime) {
        this.maxIdleTime = maxIdleTime == null ? null : validatePositive(maxIdleTime, "maxIdleTime");
    }

    public void setMaxIdleTime(int maxIdleTime) {
        setMaxIdleTime(Integer.valueOf(maxIdleTime));
    }
    
    public Map<String, Object> getExtendParams() { return extendParams; }
    public void setExtendParams(Map<String, Object> extendParams) { 
        this.extendParams = extendParams != null ? new LinkedHashMap<>(extendParams) : new LinkedHashMap<>(); 
    }
}
