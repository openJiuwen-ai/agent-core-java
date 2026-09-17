/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry shared by consumers within one application scope.
 * Registration never transfers ownership: consumers coordinate explicit store closure.
 * Different names may alias one store, but an existing name cannot be replaced.
 */
public final class KVStoreContext {
    private final Map<String, BaseKVStore> stores = new ConcurrentHashMap<>();

    /** Register an existing store; reject duplicate names without closing either store. */
    public synchronized void register(String name, BaseKVStore store) {
        requireName(name);
        Objects.requireNonNull(store, "store must not be null");
        BaseKVStore previous = stores.putIfAbsent(name, store);
        if (previous != null) {
            throw new IllegalArgumentException("KV store name already registered: " + name);
        }
    }

    /** Create once under the registry lock; failed creation leaves the name available. */
    public synchronized BaseKVStore create(String name, String type, Map<String, Object> conf) {
        requireName(name);
        if (stores.containsKey(name)) {
            throw new IllegalArgumentException("KV store name already registered: " + name);
        }
        BaseKVStore store = KVStoreFactory.create(type, conf);
        register(name, store);
        return store;
    }

    /** Resolve an already registered store; an unknown name never creates a fallback. */
    public BaseKVStore resolve(String name) {
        requireName(name);
        BaseKVStore store = stores.get(name);
        if (store == null) {
            throw new IllegalArgumentException("Unknown KV store reference: " + name);
        }
        return store;
    }

    public boolean contains(String name) {
        return name != null && stores.containsKey(name);
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("KV store name must not be blank");
        }
    }
}
