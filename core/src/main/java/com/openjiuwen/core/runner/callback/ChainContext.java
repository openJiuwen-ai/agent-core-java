/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ChainContext} in
 * {@code openjiuwen/core/runner/callback/models.py}.
 */
@Data
public class ChainContext {

    private final String event;

    private final Object[] initialArgs;

    private final Map<String, Object> initialKwargs;

    private final List<Object> results = new ArrayList<>();

    private final Map<String, Object> metadata = new HashMap<>();

    private int currentIndex = 0;

    private boolean completed = false;

    private boolean rolledBack = false;

    private final double startTime;

    public ChainContext(String event, Object[] initialArgs, Map<String, Object> initialKwargs) {
        this.event = event;
        this.initialArgs = initialArgs != null ? initialArgs : new Object[0];
        this.initialKwargs = initialKwargs != null ? new HashMap<>(initialKwargs) : new HashMap<>();
        this.startTime = System.currentTimeMillis() / 1000.0;
    }

    public Object getLastResult() {
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }

    public List<Object> getAllResults() {
        return new ArrayList<>(results);
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key, Object defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public double getElapsedTime() {
        return (System.currentTimeMillis() / 1000.0) - startTime;
    }
}
