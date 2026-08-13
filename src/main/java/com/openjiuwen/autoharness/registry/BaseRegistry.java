/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.registry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public class BaseRegistry used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class BaseRegistry<T> {
    /**
     * items.
     * 
     * @since 0.1.7
     */
    protected final Map<String, T> items = new ConcurrentHashMap<>();

    /**
     * register.
     * 
     * @param name name
     * @param spec spec
     * @since 0.1.7
     */
    public void register(String name, T spec) {
        if (items.putIfAbsent(name, spec) != null) {
            throw new IllegalArgumentException("Duplicate registration: " + name);
        }
    }

    /**
     * get.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public T get(String name) {
        return items.get(name);
    }

    /**
     * require.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public T require(String name) {
        T value = items.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown item '" + name + "'");
        }
        return value;
    }

    /**
     * names.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(items.keySet());
    }
}
