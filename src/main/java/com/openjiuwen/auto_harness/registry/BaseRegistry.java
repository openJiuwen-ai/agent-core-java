/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Shared registry implementation.
 * <p>
 * Mirrors Python's {@code BaseRegistry} in
 * {@code openjiuwen/auto_harness/registry/base.py}.
 *
 * @param <SpecT> registry spec type exposing a {@code name} property
 */
public class BaseRegistry<SpecT> {

    private final Map<String, SpecT> items = new LinkedHashMap<>();

    public void register(SpecT spec) {
        String name = extractName(spec);
        if (items.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate registration: " + name);
        }
        items.put(name, spec);
    }

    public SpecT get(String name) {
        return items.get(name);
    }

    public List<String> names() {
        return new ArrayList<>(items.keySet());
    }

    public SpecT require(String name) {
        SpecT spec = get(name);
        if (spec == null) {
            throw new NoSuchElementException("Unknown item '" + name + "'");
        }
        return spec;
    }

    private String extractName(SpecT spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        Object value = readGetter(spec);
        if (value == null) {
            value = readField(spec);
        }
        if (value == null) {
            throw new IllegalArgumentException("Registered spec must expose a name");
        }
        return String.valueOf(value);
    }

    private Object readGetter(SpecT spec) {
        try {
            Method method = spec.getClass().getMethod("getName");
            return method.invoke(spec);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private Object readField(SpecT spec) {
        try {
            Field field = spec.getClass().getDeclaredField("name");
            field.setAccessible(true);
            return field.get(spec);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
