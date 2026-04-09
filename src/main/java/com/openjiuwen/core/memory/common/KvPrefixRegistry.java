  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.common;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry for managing KV store key prefixes used by memory modules.
 * Singleton instance accessible via {@link #getInstance()}.
 */
public final class KvPrefixRegistry {

    private static final KvPrefixRegistry INSTANCE = new KvPrefixRegistry();

    private final Set<String> allPrefixes = new HashSet<>();
    private final Set<String> currentPrefixes = new HashSet<>();

    private KvPrefixRegistry() {
    }

    public static KvPrefixRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a current (active) key prefix used by a memory module.
     */
    public synchronized void registerCurrent(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Prefix cannot be empty or contain only whitespace characters: '" + prefix + "'");
        }
        if (!currentPrefixes.contains(prefix)) {
            currentPrefixes.add(prefix);
            allPrefixes.add(prefix);
        }
    }

    /**
     * Register a legacy (deprecated) key prefix for migration detection.
     */
    public synchronized void registerLegacy(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Prefix cannot be empty or contain only whitespace characters: '" + prefix + "'");
        }
        allPrefixes.add(prefix);
    }

    /**
     * Get all registered prefixes (both current and legacy).
     */
    public synchronized Set<String> getAllPrefixes() {
        return new HashSet<>(allPrefixes);
    }

    /**
     * Unregister a prefix from both current and all prefixes.
     */
    public synchronized void unregister(String prefix) {
        allPrefixes.remove(prefix);
        currentPrefixes.remove(prefix);
    }
}
