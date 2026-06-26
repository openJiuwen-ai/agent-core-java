/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for connector pools with reference counting.
 * <p>
 * Mirrors Python's {@code ConnectorPool} in
 * {@code openjiuwen/core/common/clients/connector_pool.py}.
 *
 * <p>This class provides reference counting and lifecycle management for
 * various types of connector pools.
 */
public abstract class ConnectorPool extends RefCountedResource {

    protected final ConnectorPoolConfig config;
    protected Object conn;

    protected ConnectorPool(ConnectorPoolConfig config) {
        super();
        this.config = config;
        this.conn = null;
    }

    public ConnectorPoolConfig getConfig() {
        return config;
    }

    /**
     * Get the underlying connector instance.
     *
     * @return the connector instance
     */
    public abstract Object getConn();

    /**
     * Python-compatible alias for {@link #getConn()}.
     *
     * <p>Mirrors Python's {@code ConnectorPool.conn()}.</p>
     *
     * @return the connector instance
     */
    public Object conn() {
        return getConn();
    }

    /**
     * Check if the connector pool has expired.
     *
     * @return true if the pool has exceeded its TTL or max idle time
     */
    public boolean isExpired() {
        double currentTime = System.currentTimeMillis() / 1000.0d;

        if (config.getTtl() != null && (currentTime - getCreatedAt()) > config.getTtl()) {
            return true;
        }

        if (config.getMaxIdleTime() != null && (currentTime - getLastUsed()) > config.getMaxIdleTime()) {
            return true;
        }

        return false;
    }

    @Override
    public int incrementRef() {
        return super.incrementRef();
    }

    /**
     * Get statistics for this connector pool.
     *
     * @return a map containing pool statistics
     */
    public Map<String, Object> getStat() {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("closed", isClosed());
        stat.put("ref_detail", getStats());
        stat.put("config_key", config.generateKey());
        return stat;
    }

    /**
     * Python-compatible alias for {@link #getStat()}.
     *
     * <p>Mirrors Python's {@code ConnectorPool.stat()}.</p>
     *
     * @return a map containing pool statistics
     */
    public Map<String, Object> stat() {
        return getStat();
    }
}
