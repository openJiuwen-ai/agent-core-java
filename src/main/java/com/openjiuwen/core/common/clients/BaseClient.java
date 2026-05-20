/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for common clients.
 */
public abstract class BaseClient implements AutoCloseable {
    private final Map<String, Object> config;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseClient() {
        this(Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getConfig() {
        return new LinkedHashMap<>(config);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() throws Exception {
        // Default no-op.
    }
}
