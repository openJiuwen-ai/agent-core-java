/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for connector pools with reference counting.
 * <p>
 * Mirrors Python's {@code ConnectorPool} class from
 * <code>common/clients/connector_pool.py</code>.
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
     * Check if the connector pool has expired.
     *
     * @return true if the pool has exceeded its TTL or max idle time
     */
    public boolean isExpired() {
        Instant now = Instant.now();
        
        if (config.getTtl() > 0) {
            long ageSeconds = now.getEpochSecond() - getCreatedAt().getEpochSecond();
            if (ageSeconds > config.getTtl()) {
                return true;
            }
        }

        if (config.getMaxIdleTime() > 0) {
            long idleSeconds = now.getEpochSecond() - getLastUsed().getEpochSecond();
            if (idleSeconds > config.getMaxIdleTime()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get statistics for this connector pool.
     *
     * @return a map containing pool statistics
     */
    public Map<String, Object> getStat() {
        Map<String, Object> stat = new HashMap<>();
        stat.put("closed", isClosed());
        stat.put("ref_detail", getStats());
        stat.put("config_key", config.generateKey());
        return stat;
    }
}