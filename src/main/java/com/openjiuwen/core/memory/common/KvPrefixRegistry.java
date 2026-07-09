/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry for managing KV store key prefixes used by memory modules.
 * Singleton instance accessible via {@link #getInstance()}.
 * 
 * @since 0.1.7
 */
public final class KvPrefixRegistry {
    private static final KvPrefixRegistry instance = new KvPrefixRegistry();

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> allPrefixes = new HashSet<>();

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> currentPrefixes = new HashSet<>();

    /**
     * KvPrefixRegistry.
     * 
     * @since 0.1.7
     */
    private KvPrefixRegistry() {
    }

    /**
     * getInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static KvPrefixRegistry getInstance() {
        return instance;
    }

    /**
     * Register a current (active) key prefix used by a memory module.
     * 
     * @param prefix prefix
     * @since 0.1.7
     */
    public synchronized void registerCurrent(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Prefix cannot be empty or contain only whitespace characters: '" + prefix + "'");
        }
        if (!currentPrefixes.contains(prefix)) {
            currentPrefixes.add(prefix);
            allPrefixes.add(prefix);
        }
    }

    /**
     * Register a legacy (deprecated) key prefix for migration detection.
     * 
     * @param prefix prefix
     * @since 0.1.7
     */
    public synchronized void registerLegacy(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Prefix cannot be empty or contain only whitespace characters: '" + prefix + "'");
        }
        allPrefixes.add(prefix);
    }

    /**
     * Get all registered prefixes (both current and legacy).
     * 
     * @return the result
     * @since 0.1.7
     */
    public synchronized Set<String> getAllPrefixes() {
        return new HashSet<>(allPrefixes);
    }

    /**
     * Unregister a prefix from both current and all prefixes.
     * 
     * @param prefix prefix
     * @since 0.1.7
     */
    public synchronized void unregister(String prefix) {
        allPrefixes.remove(prefix);
        currentPrefixes.remove(prefix);
    }
}
