/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    protected final Map<String, T> items = new LinkedHashMap<>();

    /**
     * register.
     * 
     * @param name name
     * @param spec spec
     * @since 0.1.7
     */
    public void register(String name, T spec) {
        if (items.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate registration: " + name);
        }
        items.put(name, spec);
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
        return items.keySet();
    }
}
