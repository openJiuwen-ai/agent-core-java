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
 * Execution context for callback chains.
 * <p>
 * Provides state management and data sharing across chain execution.
 */
@Data
public class ChainContext {

    /** Name of the event being processed. */
    private final String event;

    /** Original positional arguments. */
    private final Object[] initialArgs;

    /** Original keyword arguments. */
    private final Map<String, Object> initialKwargs;

    /** List of results from executed callbacks. */
    private final List<Object> results = new ArrayList<>();

    /** Arbitrary metadata for sharing data. */
    private final Map<String, Object> metadata = new HashMap<>();

    /** Index of currently executing callback. */
    private int currentIndex = 0;

    /** Whether chain completed successfully. */
    private boolean completed = false;

    /** Whether chain was rolled back. */
    private boolean rolledBack = false;

    /** Timestamp when chain execution started (epoch millis). */
    private final long startTime;

    public ChainContext(String event, Object[] initialArgs, Map<String, Object> initialKwargs) {
        this.event = event;
        this.initialArgs = initialArgs != null ? initialArgs : new Object[0];
        this.initialKwargs = initialKwargs != null ? initialKwargs : new HashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Get the result from the previous callback.
     *
     * @return Last result in the chain, or null if no results
     */
    public Object getLastResult() {
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }

    /**
     * Get all results from executed callbacks.
     *
     * @return Copy of all results list
     */
    public List<Object> getAllResults() {
        return new ArrayList<>(results);
    }

    public List<Object> getResults() {
        return results;
    }

    public Object[] getInitialArgs() {
        return initialArgs;
    }

    public Map<String, Object> getInitialKwargs() {
        return initialKwargs;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setRolledBack(boolean rolledBack) {
        this.rolledBack = rolledBack;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    /**
     * Store metadata in the context.
     *
     * @param key   Metadata key
     * @param value Metadata value
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Retrieve metadata from the context.
     *
     * @param key          Metadata key
     * @param defaultValue Default value if key not found
     * @return Metadata value or default
     */
    public Object getMetadata(String key, Object defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Calculate elapsed time since chain start.
     *
     * @return Elapsed time in seconds
     */
    public double getElapsedTime() {
        return (System.currentTimeMillis() - startTime) / 1000.0;
    }
}
