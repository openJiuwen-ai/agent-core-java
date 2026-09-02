/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.object;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for object storage client instances.
 * <p>
 * Providers are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.spi.store.object.ObjectStorageProvider}.
 * Service adapters register implementations (e.g. OBS, S3, MinIO) via
 * {@link #register(String, ObjectStorageProvider)}.
 * <p>
 * Calling point: attachment handling, large object persistence, etc.
 * 
 * @see ObjectStorageProvider
 * @see BaseObjectStorageClient
 * @since 0.1.7
 */
public final class ObjectStorageFactory {
    private static final Map<String, ObjectStorageProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // Discover and register providers via ServiceLoader
        for (ObjectStorageProvider provider : ServiceLoader.load(ObjectStorageProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    /**
     * ObjectStorageFactory.
     * 
     * @since 0.1.7
     */
    private ObjectStorageFactory() {
    }

    /**
     * Register an object storage provider for a given type name.
     * 
     * @param type the storage type name (e.g. "obs", "s3", "minio")
     * @param provider the provider that creates BaseObjectStorageClient instances
     * @since 0.1.7
     */
    public static void register(String type, ObjectStorageProvider provider) {
        REGISTRY.put(type, provider);
    }

    /**
     * Create an object storage client from a type and configuration.
     * 
     * @param type the storage type
     * @param conf the configuration map
     * @return a new BaseObjectStorageClient instance
     * @since 0.1.7
     */
    public static BaseObjectStorageClient create(String type, Map<String, Object> conf) {
        ObjectStorageProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No object storage provider registered for type: " + type);
        }
        return provider.create(conf != null ? conf : Map.of());
    }

    /**
     * Check whether a provider is registered for the given type.
     * 
     * @param type the storage type name
     * @return true if a provider exists
     * @since 0.1.7
     */
    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
