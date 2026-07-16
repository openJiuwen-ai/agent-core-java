/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.security.SslUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.net.ssl.SSLContext;

/**
 * Connector pool configuration.
 * 
 * @since 0.1.7
 */
public class ConnectorPoolConfig {
    private final int limit;
    private final int limitPerHost;
    private final boolean isSslVerifyEnabled;
    private final String sslCert;
    private final boolean isForceCloseEnabled;
    private final Double keepaliveTimeout;
    private final Integer ttl;
    private final Integer maxIdleTime;
    private final Map<String, Object> extendParams;

    /**
     * ConnectorPoolConfig.
     * 
     * @since 0.1.7
     */
    public ConnectorPoolConfig() {
        this(100, 30, true, null, false, 60.0, 3600, 300, Map.of());
    }

    /**
     * ConnectorPoolConfig.
     * 
     * @param limit limit
     * @param limitPerHost limitPerHost
     * @param isSslVerifyEnabled isSslVerifyEnabled
     * @param sslCert sslCert
     * @param isForceCloseEnabled isForceCloseEnabled
     * @param keepaliveTimeout keepaliveTimeout
     * @param ttl ttl
     * @param maxIdleTime maxIdleTime
     * @param extendParams extendParams
     * @since 0.1.7
     */
    public ConnectorPoolConfig(int limit, int limitPerHost, boolean isSslVerifyEnabled, String sslCert,
            boolean isForceCloseEnabled, Double keepaliveTimeout, Integer ttl, Integer maxIdleTime,
            Map<String, Object> extendParams) {
        validatePositive("limit", limit);
        validatePositive("limit_per_host", limitPerHost);
        validatePositive("keepalive_timeout", keepaliveTimeout);
        validatePositive("ttl", ttl);
        validatePositive("max_idle_time", maxIdleTime);
        this.limit = limit;
        this.limitPerHost = limitPerHost;
        this.isSslVerifyEnabled = isSslVerifyEnabled;
        this.sslCert = sslCert;
        this.isForceCloseEnabled = isForceCloseEnabled;
        this.keepaliveTimeout = keepaliveTimeout;
        this.ttl = ttl;
        this.maxIdleTime = maxIdleTime;
        this.extendParams = new LinkedHashMap<>();
        if (extendParams != null) {
            extendParams.forEach((key, value) -> {
                if (key != null) {
                    this.extendParams.put(key, value);
                }
            });
        }
    }

    /**
     * getLimit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getLimit() {
        return limit;
    }

    /**
     * getLimitPerHost.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getLimitPerHost() {
        return limitPerHost;
    }

    /**
     * isSslVerify.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSslVerify() {
        return isSslVerifyEnabled;
    }

    /**
     * getSslCert.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSslCert() {
        return sslCert;
    }

    /**
     * isForceClose.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isForceClose() {
        return isForceCloseEnabled;
    }

    /**
     * getKeepaliveTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getKeepaliveTimeout() {
        return keepaliveTimeout;
    }

    /**
     * getTtl.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getTtl() {
        return ttl;
    }

    /**
     * getMaxIdleTime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     * getExtendParams.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtendParams() {
        return new LinkedHashMap<>(extendParams);
    }

    /**
     * createSslContext.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SSLContext createSslContext() {
        if (!isSslVerifyEnabled) {
            return SslUtils.createInsecureSslContext();
        }
        return SslUtils.createStrictSslContext(sslCert);
    }

    /**
     * generateKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String generateKey() {
        Map<String, Object> normalized = new TreeMap<>();
        normalized.put("limit", limit);
        normalized.put("limit_per_host", limitPerHost);
        normalized.put("ssl_verify", isSslVerifyEnabled);
        normalized.put("ssl_cert", sslCert);
        normalized.put("force_close", isForceCloseEnabled);
        normalized.put("keepalive_timeout", keepaliveTimeout);
        normalized.put("ttl", ttl);
        normalized.put("max_idle_time", maxIdleTime);
        normalized.put("extend_params", new TreeMap<>(extendParams));
        return md5Hex(normalized.toString());
    }

    /**
     * from.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ConnectorPoolConfig from(Object value) {
        if (value instanceof ConnectorPoolConfig config) {
            return config;
        }
        Map<String, Object> map = ClientConfigSupport.asObjectMap(value);
        return new ConnectorPoolConfig(ClientConfigSupport.asInt(map.get("limit"), 100),
                ClientConfigSupport.asInt(map.get("limit_per_host"), 30),
                ClientConfigSupport.asBoolean(map.get("ssl_verify"), true),
                ClientConfigSupport.asString(map.get("ssl_cert")),
                ClientConfigSupport.asBoolean(map.get("force_close"), false),
                ClientConfigSupport.asNullableDouble(map.get("keepalive_timeout")) != null
                        ? ClientConfigSupport.asNullableDouble(map.get("keepalive_timeout"))
                        : 60.0,
                ClientConfigSupport.asNullableInt(map.get("ttl")) != null
                        ? ClientConfigSupport.asNullableInt(map.get("ttl"))
                        : 3600,
                ClientConfigSupport.asNullableInt(map.get("max_idle_time")) != null
                        ? ClientConfigSupport.asNullableInt(map.get("max_idle_time"))
                        : 300,
                ClientConfigSupport.asObjectMap(map.get("extend_params")));
    }

    /**
     * validatePositive.
     * 
     * @param name name
     * @param value value
     * @since 0.1.7
     */
    protected static void validatePositive(String name, Number value) {
        if (value != null && value.doubleValue() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /**
     * md5Hex.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    protected static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
