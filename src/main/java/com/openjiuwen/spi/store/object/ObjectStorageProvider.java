/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.object;

import java.util.Map;

/**
 * Provider interface for creating object storage client instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.spi.store.object.ObjectStorageProvider}.
 * Each provider declares which {@code typeName()} it supports.
 * Service adapters can also register providers programmatically via
 * {@link ObjectStorageFactory#register(String, ObjectStorageProvider)}.
 *
 * @see ObjectStorageFactory
 * @see BaseObjectStorageClient
 * @since 0.1.12
 */
public interface ObjectStorageProvider {
    /**
     * The storage type name this provider handles (e.g., "obs", "s3").
     *
     * @return the type name for registration
     */
    String typeName();

    /**
     * Create an object storage client with the given configuration.
     *
     * @param conf the configuration map
     * @return a new BaseObjectStorageClient instance
     */
    BaseObjectStorageClient create(Map<String, Object> conf);
}
