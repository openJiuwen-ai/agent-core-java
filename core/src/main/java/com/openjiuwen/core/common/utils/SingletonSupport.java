/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic thread-safe singleton support using double-checked locking.
 * <p>
 * Java equivalent of the Python Singleton metaclass. Subclasses are guaranteed
 * to have exactly one instance per concrete class.
 * 
 * <pre>{@code
 * public class MyService extends SingletonSupport<MyService> {
 *     public static MyService getInstance() {
 *         return SingletonSupport.getInstance(MyService.class, MyService::new);
 *     }
 * }
 * }</pre>
 * 
 * @since 0.1.7
 */
public abstract class SingletonSupport<T> {
    private static final ConcurrentHashMap<Class<?>, Object> INSTANCES = new ConcurrentHashMap<>();

    /**
     * getInstance.
     * 
     * @param clazz clazz
     * @param factory factory
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static <T> T getInstance(Class<T> clazz, java.util.function.Supplier<T> factory) {
        Object instance = INSTANCES.get(clazz);
        if (instance == null) {
            synchronized (SingletonSupport.class) {
                instance = INSTANCES.get(clazz);
                if (instance == null) {
                    instance = factory.get();
                    INSTANCES.put(clazz, instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Reset a specific singleton — primarily for testing.
     * 
     * @param clazz clazz
     * @since 0.1.7
     */
    public static void reset(Class<?> clazz) {
        INSTANCES.remove(clazz);
    }
}
