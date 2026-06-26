/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

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
        CheckpointerProvider provider = REGISTRY.get(actual.getType());
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported checkpointer type: " + actual.getType());
        }
        return provider.create(actual.getConf());
    }

    public static void setDefaultCheckpointer(Checkpointer checkpointer) {
        defaultCheckpointer = checkpointer;
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
