/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for connector pools.
 * <p>
 * Mirrors Python's {@code ConnectorPoolConfig} class from
 * <code>common/clients/connector_pool.py</code>.
 */
public class ConnectorPoolConfig {

    private int limit = 100;
    private int limitPerHost = 30;
    private boolean sslVerify = true;
    private String sslCert = null;
    private boolean forceClose = false;
    private double keepaliveTimeout = 60.0;
    private int ttl = 3600;
    private int maxIdleTime = 300;
    private Map<String, Object> extendParams = new HashMap<>();

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
        StringBuilder parts = new StringBuilder();
        parts.append("limit:").append(limit);
        parts.append("&limitPerHost:").append(limitPerHost);
        parts.append("&sslVerify:").append(sslVerify);
        parts.append("&forceClose:").append(forceClose);
        parts.append("&keepaliveTimeout:").append(keepaliveTimeout);
        parts.append("&ttl:").append(ttl);
        parts.append("&maxIdleTime:").append(maxIdleTime);
        
        if (sslCert != null) {
            parts.append("&sslCert:").append(sslCert);
        }
        
        if (!extendParams.isEmpty()) {
            parts.append("&extendParams:").append(extendParams.toString());
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(parts.toString().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return parts.toString().hashCode() + "";
        }
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
    
    public double getKeepaliveTimeout() { return keepaliveTimeout; }
    public void setKeepaliveTimeout(double keepaliveTimeout) { 
        this.keepaliveTimeout = validatePositive(keepaliveTimeout, "keepaliveTimeout"); 
    }
    
    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = validatePositive(ttl, "ttl"); }
    
    public int getMaxIdleTime() { return maxIdleTime; }
    public void setMaxIdleTime(int maxIdleTime) { 
        this.maxIdleTime = validatePositive(maxIdleTime, "maxIdleTime"); 
    }
    
    public Map<String, Object> getExtendParams() { return extendParams; }
    public void setExtendParams(Map<String, Object> extendParams) { 
        this.extendParams = extendParams != null ? extendParams : new HashMap<>(); 
    }
}