/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.foundation.store.kv.ApplicationStorageScope;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for checkpointer instances.
 * <p>
 * Built-in types are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.session.checkpointer.CheckpointerProvider}.
 * Service adapters can register additional types via
 * {@link #register(String, CheckpointerProvider)} without modifying Core
 * source.
 * <p>
 * Mirrors Python's
 * {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerFactory}.
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
     * @param name     the type name
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
        if (provider == null && ("redis".equals(type) || "redis_checkpointer_cluster".equals(type))) {
            provider = REGISTRY.computeIfAbsent("redis", ignored -> loadOptionalRedisProvider());
            REGISTRY.putIfAbsent("redis_checkpointer_cluster", provider);
        }
        if (provider == null) {
            throw new IllegalArgumentException("No checkpointer provider registered for type: " + type);
        }
        return provider.create(conf);
    }

    /**
     * Resolve a scoped resource once, then use the existing provider injection
     * contract.
     */
    public static Checkpointer create(String type, Map<String, Object> conf, ApplicationStorageScope scope) {
        if (scope == null) {
            return create(type, conf);
        }
        synchronized (scope.kvStores()) {
            return createScoped(type, conf, scope);
        }
    }

    private static Checkpointer createScoped(String type, Map<String, Object> conf, ApplicationStorageScope scope) {
        boolean redis = "redis".equals(type) || "redis_checkpointer_cluster".equals(type);
        boolean persistence = "persistence".equals(type);
        Map<String, Object> input = new LinkedHashMap<>(conf == null ? Map.of() : conf);
        if (!redis && !persistence) {
            if (input.containsKey("storeRef")) {
                throw new IllegalArgumentException("Checkpointer does not support storeRef: " + type);
            }
            return create(type, input);
        }
        boolean explicit = input.containsKey("connection") || input.containsKey("kv_store")
                || input.containsKey("db_type") || input.containsKey("db_path");
        BaseKVStore store = null;
        if (input.containsKey("storeRef")) {
            if (explicit) {
                throw new IllegalArgumentException("storeRef conflicts with connection or direct Store");
            }
            Object ref = input.remove("storeRef");
            if (!(ref instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("storeRef must be a nonblank string");
            }
            store = scope.kvStores().resolve(name);
        } else if (!explicit && scope.kvStores().contains("default")) {
            store = scope.kvStores().resolve("default");
        } else if (input.get("kv_store") instanceof BaseKVStore supplied) {
            store = supplied;
        } else if (redis && input.containsKey("connection")) {
            store = KVStoreFactory.create("redis", Map.of("connection", input.get("connection")));
            input.remove("connection");
        }
        if (store == null && persistence && !input.containsKey("kv_store")
                && "sqlite".equals(input.getOrDefault("db_type", "sqlite"))) {
            store = KVStoreFactory.create("sqlite", input);
        }
        if (store != null) {
            input.put("kv_store", store);
            Checkpointer result = create(type, input); // Redis type validation stays inside its optional Provider.
            if (!scope.kvStores().contains("default")) {
                scope.kvStores().register("default", store);
            }
            return result;
        }
        return create(type, input);
    }

    private static CheckpointerProvider loadOptionalRedisProvider() {
        try {
            Class<?> type = Class.forName("com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer$Provider");
            return (CheckpointerProvider) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Redis checkpointer is unavailable; add the Redis implementation and Jedis dependency", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize Redis checkpointer provider", e);
        }
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
     * @param storeType    the type
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
