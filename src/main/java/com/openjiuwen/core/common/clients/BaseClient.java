/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for common clients.
 * 
 * @since 0.1.7
 */
public abstract class BaseClient implements AutoCloseable {
    private final Map<String, Object> config;

    /**
     * BaseClient.
     * 
     * @since 0.1.7
     */
    protected BaseClient() {
        this(Map.of());
    }

    /**
     * BaseClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    protected BaseClient(Map<String, ?> config) {
        this.config = new LinkedHashMap<>();
        if (config != null) {
            config.forEach((key, value) -> {
                if (key != null) {
                    this.config.put(key, value);
                }
            });
        }
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getConfig() {
        return new LinkedHashMap<>(config);
    }

    /**
     * close.
     * 
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void close() throws Exception {
        // Default no-op.
    }
}
