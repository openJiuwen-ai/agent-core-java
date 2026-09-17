/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import java.time.Duration;
import java.util.Map;

/**
 * KvTodoStorageProvider.
 *
 * @since 0.1.7
 */
public class KvTodoStorageProvider implements TodoStorageProvider {
    @Override
    public String typeName() {
        return "kv";
    }

    @Override
    @SuppressWarnings("unchecked")
    public TodoStorage create(Map<String, Object> conf) {
        Map<String, Object> input = conf == null ? Map.of() : conf;
        if (input.containsKey("storeRef")) {
            throw new IllegalArgumentException("Resolve Todo storeRef in the application scope first");
        }
        Duration ttl = null;
        boolean refresh = false;
        if (input.containsKey("ttl")) {
            if (!(input.get("ttl") instanceof Map<?, ?> policy)) {
                throw new IllegalArgumentException("Todo ttl must be a map");
            }
            if (policy.containsKey("default_ttl")) {
                Object raw = policy.get("default_ttl");
                if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0
                        || n.doubleValue() * 60 > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Todo default_ttl must be positive finite minutes");
                }
                ttl = Duration.ofSeconds((long) Math.ceil(n.doubleValue() * 60));
            }
            if (policy.containsKey("refresh_on_read") && !(policy.get("refresh_on_read") instanceof Boolean)) {
                throw new IllegalArgumentException("Todo refresh_on_read must be boolean");
            }
            refresh = Boolean.TRUE.equals(policy.get("refresh_on_read"));
        }
        if (refresh && ttl == null) {
            throw new IllegalArgumentException("Todo refresh_on_read requires default_ttl");
        }
        BaseKVStore store;
        if (input.containsKey("sharedKvStore")) {
            if (!(input.get("sharedKvStore") instanceof BaseKVStore supplied)) {
                throw new IllegalArgumentException("sharedKvStore must be a BaseKVStore");
            }
            store = supplied;
        } else {
            Object type = input.getOrDefault("kvStoreType", "in_memory");
            if (!(type instanceof String)) {
                throw new IllegalArgumentException("kvStoreType must be a string");
            }
            Object args = input.getOrDefault("kvStoreConf", Map.of());
            if (!(args instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("kvStoreConf must be a map");
            }
            store = KVStoreFactory.create((String) type, (Map<String, Object>) args);
        }
        return new KvTodoStorage(store, ttl, refresh);
    }
}
