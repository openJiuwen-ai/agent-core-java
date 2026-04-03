// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.extensions.checkpointer.redis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis TTL (Time To Live) configuration for stored data.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.checkpointer.RedisTTLConfig}.
 */
public class RedisTTLConfig {

    /**
     * Default TTL in minutes for stored data.
     */
    private Double defaultTtl;

    /**
     * Whether to refresh TTL when data is read.
     */
    private boolean refreshOnRead;

    /**
     * Default constructor.
     */
    public RedisTTLConfig() {
        this.defaultTtl = null;
        this.refreshOnRead = false;
    }

    /**
     * Constructor with parameters.
     *
     * @param defaultTtl    Default TTL in minutes
     * @param refreshOnRead Whether to refresh TTL on read
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
     */
    public static RedisTTLConfig fromMap(Map<String, Object> config) {
        if (config == null) {
            return new RedisTTLConfig();
        }
        Double defaultTtl = config.get("default_ttl") != null 
            ? ((Number) config.get("default_ttl")).doubleValue() 
            : null;
        Boolean refreshOnRead = config.get("refresh_on_read") != null
            ? (Boolean) config.get("refresh_on_read")
            : false;
        return new RedisTTLConfig(defaultTtl, refreshOnRead);
    }

    public Double getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Double defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public boolean isRefreshOnRead() {
        return refreshOnRead;
    }

    public void setRefreshOnRead(boolean refreshOnRead) {
        this.refreshOnRead = refreshOnRead;
    }

    /**
     * Get TTL in seconds.
     *
     * @return TTL in seconds, or null if not set
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
