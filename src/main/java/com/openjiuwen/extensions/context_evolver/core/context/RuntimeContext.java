/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's runtime context container in
 * {@code openjiuwen/extensions/context_evolver/core/context/runtime_context.py}.
 */
public class RuntimeContext {

    private final Map<String, Object> data = new LinkedHashMap<>();

    public Object get(String key) {
        return get(key, null);
    }

    public Object get(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Map<String, Object> toDict() {
        return new LinkedHashMap<>(data);
    }

    @Override
    public String toString() {
        return "RuntimeContext(" + data + ")";
    }
}
