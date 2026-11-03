/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code EventFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class EventFilter {

    private final String name;

    public EventFilter() {
        this("");
    }

    public EventFilter(String name) {
        this.name = name != null && !name.isEmpty() ? name : getClass().getSimpleName();
    }

    public String getName() {
        return name;
    }

    public FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        return FilterResult.continueResult();
    }

    protected static Object[] safeArgs(Object[] args) {
        return args == null ? new Object[0] : args.clone();
    }

    protected static Map<String, Object> safeKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
    }

    protected static String callbackName(Function<Map<String, Object>, Object> callback) {
        return callback == null ? "<null-callback>" : callback.toString();
    }
}
