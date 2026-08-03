/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for checkpointer providers and default instances.
 *
 * <p>Mirrors Python's {@code CheckpointerFactory} in
 * {@code openjiuwen/core/session/checkpointer/checkpointer.py}.</p>
 */
public final class CheckpointerFactory {

    private static final InMemoryCheckpointer DEFAULT_IN_MEMORY_CHECKPOINTER = new InMemoryCheckpointer();
    private static final Map<String, CheckpointerProvider> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Checkpointer> TYPE_CHECKPOINTERS = new ConcurrentHashMap<>();
    private static volatile Checkpointer defaultCheckpointer;

    static {
        register("in_memory", conf -> DEFAULT_IN_MEMORY_CHECKPOINTER);
        register("persistence", PersistenceCheckpointer::createFromConfig);
        register("redis", new RedisCheckpointer.Provider());
        register("redis_checkpointer_cluster", new RedisCheckpointer.Provider());
    }

    private CheckpointerFactory() {
    }

    public static void register(String name, CheckpointerProvider provider) {
        if (name != null && provider != null) {
            REGISTRY.put(name, provider);
        }
    }

    public static Checkpointer create(CheckpointerConfig config) {
        CheckpointerConfig actual = config == null ? new CheckpointerConfig() : config;
        return create(actual.getType(), actual.getConf());
    }

    public static Checkpointer create(String type, Map<String, Object> conf) {
        CheckpointerProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported checkpointer type: " + type);
        }
        return provider.create(conf);
    }

    public static synchronized void installDefaultCheckpointer(CheckpointerConfig config) {
        if (config == null) {
            return;
        }
        Checkpointer checkpointer = create(config);
        try {
            releaseDefaultCheckpointer();
        } catch (RuntimeException exception) {
            closeDefaultCheckpointer(checkpointer);
            throw exception;
        }
        defaultCheckpointer = checkpointer;
    }

    public static synchronized void releaseDefaultCheckpointer() {
        Checkpointer checkpointer = defaultCheckpointer;
        defaultCheckpointer = null;
        closeDefaultCheckpointer(checkpointer);
    }

    public static void setDefaultCheckpointer(Checkpointer checkpointer) {
        defaultCheckpointer = checkpointer;
    }

    private static void closeDefaultCheckpointer(Checkpointer checkpointer) {
        if (checkpointer instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to close default checkpointer", exception);
            }
        }
    }

    public static void setCheckpointer(String storeType, Checkpointer checkpointer) {
        if (storeType != null && checkpointer != null) {
            TYPE_CHECKPOINTERS.put(storeType, checkpointer);
        }
    }

    public static Checkpointer getCheckpointer() {
        return getCheckpointer(null);
    }

    public static Checkpointer getCheckpointer(String storeType) {
        if (storeType != null) {
            Checkpointer typed = TYPE_CHECKPOINTERS.get(storeType);
            if (typed != null) {
                return typed;
            }
            if ("in_memory".equals(storeType)) {
                return DEFAULT_IN_MEMORY_CHECKPOINTER;
            }
        }
        return defaultCheckpointer == null ? DEFAULT_IN_MEMORY_CHECKPOINTER : defaultCheckpointer;
    }

    public static InMemoryCheckpointer defaultInMemoryCheckpointer() {
        return DEFAULT_IN_MEMORY_CHECKPOINTER;
    }
}
