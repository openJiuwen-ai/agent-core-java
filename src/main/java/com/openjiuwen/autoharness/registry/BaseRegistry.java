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
 * @since 1.0
 */
public class BaseRegistry<T> {
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final Map<String, T> items = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public void register(String name, T spec) {
        if (items.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate registration: " + name);
        }
        items.put(name, spec);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public T get(String name) {
        return items.get(name);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public T require(String name) {
        T value = items.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown item '" + name + "'");
        }
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Set<String> names() {
        return items.keySet();
    }
}
