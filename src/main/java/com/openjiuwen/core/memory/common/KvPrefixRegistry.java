/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry for KV-store prefixes used by memory modules.
 *
 * <p>Mirrors Python's {@code KvPrefixRegistry} in
 * {@code openjiuwen/core/memory/common/kv_prefix_registry.py}.</p>
 */
public class KvPrefixRegistry {
    private static final KvPrefixRegistry INSTANCE = new KvPrefixRegistry();

    private final Set<String> allPrefixes = new HashSet<>();
    private final Set<String> currentPrefixes = new HashSet<>();

    public KvPrefixRegistry() {
    }

    public static KvPrefixRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void registerCurrent(String prefix) {
        validatePrefix(prefix);
        if (currentPrefixes.add(prefix)) {
            allPrefixes.add(prefix);
        }
    }

    public synchronized void registerLegacy(String prefix) {
        validatePrefix(prefix);
        allPrefixes.add(prefix);
    }

    public synchronized Set<String> getAllPrefixes() {
        return new HashSet<>(allPrefixes);
    }

    public synchronized void unregister(String prefix) {
        allPrefixes.remove(prefix);
        currentPrefixes.remove(prefix);
    }

    private static void validatePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Prefix cannot be empty or contain only whitespace characters: '" + prefix + "'"
            );
        }
    }
}
