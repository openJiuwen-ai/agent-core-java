/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base connector pool with reference counting.
 * 
 * @since 0.1.7
 */
public abstract class ConnectorPool extends RefCountedResource {
    private final ConnectorPoolConfig config;

    /**
     * ConnectorPool.
     * 
     * @param config config
     * @since 0.1.7
     */
    protected ConnectorPool(ConnectorPoolConfig config) {
        this.config = config;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ConnectorPoolConfig getConfig() {
        return config;
    }

    /**
     * conn.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract Object conn();

    /**
     * isExpired.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis();
        if (config.getTtl() != null && (now - getCreatedAtMillis()) > config.getTtl() * 1000L) {
            return true;
        }
        return config.getMaxIdleTime() != null && (now - getLastUsedMillis()) > config.getMaxIdleTime() * 1000L;
    }

    /**
     * stat.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> stat() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("isClosed", isClosed());
        stats.put("ref_detail", getStats());
        return stats;
    }
}
