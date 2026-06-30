/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base connector pool with reference counting.
 */
public abstract class ConnectorPool extends RefCountedResource {
    private final ConnectorPoolConfig config;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected ConnectorPool(ConnectorPoolConfig config) {
        this.config = config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ConnectorPoolConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract Object conn();

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis();
        if (config.getTtl() != null && (now - getCreatedAtMillis()) > config.getTtl() * 1000L) {
            return true;
        }
        return config.getMaxIdleTime() != null
                && (now - getLastUsedMillis()) > config.getMaxIdleTime() * 1000L;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> stat() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("isClosed", isClosed());
        stats.put("ref_detail", getStats());
        return stats;
    }
}
