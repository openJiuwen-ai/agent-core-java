/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis TTL (Time To Live) configuration for stored data.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.checkpointer.RedisTTLConfig}.
 * 
 * @since 0.1.7
 */
public class RedisTTLConfig {
    private Double defaultTtl;

    /**
     * Whether to refresh TTL when data is read.
     */
    private boolean refreshOnRead;

    /**
     * Default constructor.
     * 
     * @since 0.1.7
     */
    public RedisTTLConfig() {
        this.defaultTtl = null;
        this.refreshOnRead = false;
    }

    /**
     * Constructor with parameters.
     * 
     * @param defaultTtl Default TTL in minutes
     * @param refreshOnRead Whether to refresh TTL on read
     * @since 0.1.7
     */
    public RedisTTLConfig(Double defaultTtl, boolean refreshOnRead) {
        this.defaultTtl = defaultTtl;
        this.refreshOnRead = refreshOnRead;
    }

    /**
     * Create from a configuration map.
     * 
     * @param config Configuration map
     * @return RedisTTLConfig instance
     * @since 0.1.7
     */
    public static RedisTTLConfig fromMap(Map<String, Object> config) {
        if (config == null) {
            return new RedisTTLConfig();
        }
        Double defaultTtl =
            config.get("default_ttl") != null ? ((Number) config.get("default_ttl")).doubleValue() : null;
        Boolean refreshOnRead = config.get("refresh_on_read") != null ? (Boolean) config.get("refresh_on_read") : false;
        return new RedisTTLConfig(defaultTtl, refreshOnRead);
    }

    /**
     * getDefaultTtl.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * setDefaultTtl.
     * 
     * @param defaultTtl defaultTtl
     * @since 0.1.7
     */
    public void setDefaultTtl(Double defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * isRefreshOnRead.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRefreshOnRead() {
        return refreshOnRead;
    }

    /**
     * setRefreshOnRead.
     * 
     * @param refreshOnRead refreshOnRead
     * @since 0.1.7
     */
    public void setRefreshOnRead(boolean refreshOnRead) {
        this.refreshOnRead = refreshOnRead;
    }

    /**
     * Get TTL in seconds.
     * 
     * @return TTL in seconds, or null if not set
     * @since 0.1.7
     */
    public Integer getTtlSeconds() {
        if (defaultTtl == null) {
            return null;
        }
        return (int) (defaultTtl * 60);
    }

    /**
     * Convert this TTL configuration into the map shape used by storage helpers.
     * 
     * @return storage-friendly TTL map
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> ttl = new LinkedHashMap<>();
        if (defaultTtl != null) {
            ttl.put("default_ttl", defaultTtl);
        }
        ttl.put("refresh_on_read", refreshOnRead);
        return ttl;
    }
}
