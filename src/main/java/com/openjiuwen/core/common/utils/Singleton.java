/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code Singleton} in
 * {@code openjiuwen/core/common/utils/singleton.py}.
 */
public final class Singleton {

    private static final Object SINGLETON_LOCK = new Object();
    private static final Map<Class<?>, Object> INSTANCES = new LinkedHashMap<>();

    private Singleton() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInstance(Class<T> clazz, Supplier<? extends T> supplier) {
        synchronized (SINGLETON_LOCK) {
            if (!INSTANCES.containsKey(clazz)) {
                INSTANCES.put(clazz, supplier.get());
            }
            return (T) INSTANCES.get(clazz);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getExistingInstance(Class<T> clazz) {
        synchronized (SINGLETON_LOCK) {
            return (T) INSTANCES.get(clazz);
        }
    }

    public static boolean hasInstance(Class<?> clazz) {
        synchronized (SINGLETON_LOCK) {
            return INSTANCES.containsKey(clazz);
        }
    }

    public static void clearAll() {
        synchronized (SINGLETON_LOCK) {
            INSTANCES.clear();
        }
    }
}
