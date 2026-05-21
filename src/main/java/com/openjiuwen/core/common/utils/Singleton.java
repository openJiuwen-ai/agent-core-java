/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Singleton utility for thread-safe lazy initialization.
 * <p>
 * Mirrors Python's {@code Singleton} metaclass from
 * <code>common/utils/singleton.py</code>.
 *
 * <p>In Java, this is implemented as a registry-based singleton pattern
 * since Java doesn't support metaclass-based singleton.
 */
public final class Singleton {

    private static final ConcurrentMap<Class<?>, Object> instances = new ConcurrentHashMap<>();

    private Singleton() {}

    /**
     * Get or create a singleton instance of the given class.
     *
     * @param clazz the class to get singleton for
     * @param supplier the constructor supplier (only called once)
     * @param <T> the type
     * @return the singleton instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T getInstance(Class<T> clazz, Supplier<T> supplier) {
        return (T) instances.computeIfAbsent(clazz, k -> supplier.get());
    }

    /**
     * Get an existing singleton instance.
     *
     * @param clazz the class
     * @param <T> the type
     * @return the instance, or null if not yet created
     */
    @SuppressWarnings("unchecked")
    public static <T> T getInstance(Class<T> clazz) {
        return (T) instances.get(clazz);
    }

    /**
     * Clear all singleton instances (for testing).
     */
    public static void clearAll() {
        instances.clear();
    }

    /**
     * Check if a singleton instance exists for the given class.
     */
    public static boolean hasInstance(Class<?> clazz) {
        return instances.containsKey(clazz);
    }
}
