/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for checkpointer instances.
 * <p>
 * Built-in types are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.session.checkpointer.CheckpointerProvider}.
 * Service adapters can register additional types via
 * {@link #register(String, CheckpointerProvider)} without modifying Core source.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerFactory}.
 * 
 * @since 0.1.7
 */
public final class CheckpointerFactory {
    private static final Map<String, CheckpointerProvider> REGISTRY = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private static final Map<String, Checkpointer> TYPE_CHECKPOINTERS = new ConcurrentHashMap<>();
    private static Checkpointer defaultCheckpointer = null;

    /**
     * InMemoryCheckpointer.
     * 
     * @since 0.1.7
     */
    private static final Checkpointer DEFAULT_INMEMORY_CHECKPOINTER = new InMemoryCheckpointer();

    static {
        // Discover and register providers via ServiceLoader
        for (CheckpointerProvider provider : ServiceLoader.load(CheckpointerProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
        // Register redis_checkpointer_cluster as alias for redis
        if (REGISTRY.containsKey("redis") && !REGISTRY.containsKey("redis_checkpointer_cluster")) {
            REGISTRY.put("redis_checkpointer_cluster", REGISTRY.get("redis"));
        }
    }

    /**
     * CheckpointerFactory.
     * 
     * @since 0.1.7
     */
    private CheckpointerFactory() {
    }

    /**
     * Register a checkpointer provider for a given type name.
     * 
     * @param name the type name
     * @param provider the provider
     * @since 0.1.7
     */
    public static void register(String name, CheckpointerProvider provider) {
        REGISTRY.put(name, provider);
    }

    /**
     * Create a checkpointer from a CheckpointerConfig.
     * 
     * @param checkpointerConf the checkpointer configuration
     * @return the checkpointer instance
     * @since 0.1.7
     */
    public static Checkpointer create(CheckpointerConfig checkpointerConf) {
        if (checkpointerConf == null) {
            throw new IllegalArgumentException("checkpointerConf cannot be null");
        }
        return create(checkpointerConf.getType(), checkpointerConf.getConf());
    }

    /**
     * Create a checkpointer from config.
     * 
     * @param type the checkpointer type
     * @param conf the configuration map
     * @return the checkpointer instance
     * @since 0.1.7
     */
    public static Checkpointer create(String type, Map<String, Object> conf) {
        CheckpointerProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No checkpointer provider registered for type: " + type);
        }
        return provider.create(conf);
    }

    /**
     * Set the default checkpointer instance.
     * 
     * @param checkpointer checkpointer
     * @since 0.1.7
     */
    public static void setDefaultCheckpointer(Checkpointer checkpointer) {
        defaultCheckpointer = checkpointer;
    }

    /**
     * Set a checkpointer instance for a specific type.
     * 
     * @param storeType the type
     * @param checkpointer the instance
     * @since 0.1.7
     */
    public static void setCheckpointer(String storeType, Checkpointer checkpointer) {
        TYPE_CHECKPOINTERS.put(storeType, checkpointer);
    }

    /**
     * Get checkpointer instance.
     * 
     * @param storeType optional checkpointer type
     * @return checkpointer instance
     * @since 0.1.7
     */
    public static Checkpointer getCheckpointer(String storeType) {
        if (storeType != null) {
            Checkpointer cp = TYPE_CHECKPOINTERS.get(storeType);
            if (cp != null) {
                return cp;
            }
            if ("in_memory".equals(storeType)) {
                return DEFAULT_INMEMORY_CHECKPOINTER;
            }
        }
        if (defaultCheckpointer != null) {
            return defaultCheckpointer;
        }
        return DEFAULT_INMEMORY_CHECKPOINTER;
    }

    /**
     * Get the default in-memory checkpointer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Checkpointer getCheckpointer() {
        return getCheckpointer(null);
    }
}
