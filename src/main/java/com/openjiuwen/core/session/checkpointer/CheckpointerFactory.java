/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for checkpointer providers and default instances.
 *
 * <p>Built-in types are discovered via {@link ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.session.checkpointer.CheckpointerProvider}.
 * Each provider is registered under {@link CheckpointerProvider#typeName()} and
 * {@link CheckpointerProvider#aliases()}. Service adapters can also register
 * additional types via {@link #register(String, CheckpointerProvider)}
 * without modifying Core source.</p>
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
        for (CheckpointerProvider provider : ServiceLoader.load(CheckpointerProvider.class)) {
            registerProviderNames(provider);
        }
        // Ensure critical builtins exist even if META-INF is incomplete on the classpath.
        REGISTRY.putIfAbsent("in_memory", new InMemoryCheckpointerProvider());
        REGISTRY.putIfAbsent("persistence", new PersistenceCheckpointerProvider());
    }

    private static void registerProviderNames(CheckpointerProvider provider) {
        putIfAbsentName(provider.typeName(), provider);
        Iterable<String> aliases = provider.aliases();
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            putIfAbsentName(alias, provider);
        }
    }

    private static void putIfAbsentName(String name, CheckpointerProvider provider) {
        if (name != null && !name.isBlank()) {
            REGISTRY.putIfAbsent(name, provider);
        }
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
