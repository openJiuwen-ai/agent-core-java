/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.Map;

/**
 * Base class for all client implementations.
 * <p>
 * Mirrors Python's {@code BaseClient} class from
 * <code>common/clients/client_registry.py</code>.
 *
 * <p>Provides a common interface for all client types with metadata
 * support for registration.
 */
public abstract class BaseClient {

    /**
     * Get the client name for registration.
     * Subclasses must override this method.
     *
     * @return the client name
     */
    public static String getClientName() {
        return null;
    }

    /**
     * Get the client type category for registration.
     * Subclasses must override this method.
     *
     * @return the client type (e.g., 'database', 'cache')
     */
    public static String getClientType() {
        return null;
    }

    /**
     * Initialize the client with configuration.
     *
     * @param config the configuration map
     */
    public abstract void initialize(Map<String, Object> config);

    /**
     * Close the client and release resources.
     */
    public abstract void close();

    /**
     * Check if the client is healthy.
     *
     * @return true if the client is healthy
     */
    public abstract boolean isHealthy();

    /**
     * Get client metadata.
     *
     * @return a map of metadata
     */
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("client_name", getClientName());
        metadata.put("client_type", getClientType());
        return metadata;
    }
}