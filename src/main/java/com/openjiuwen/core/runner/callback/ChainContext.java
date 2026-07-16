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
 * 
 * @since 0.1.7
 */
@Data
public class ChainContext {
    private final String event;

    /** Original positional arguments. */
    private final Object[] initialArgs;

    /** Original keyword arguments. */
    private final Map<String, Object> initialKwargs;

    /**
     * List of results from executed callbacks.
     * 
     * @since 0.1.7
     */
    private final List<Object> results = new ArrayList<>();

    /**
     * Arbitrary metadata for sharing data.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> metadata = new HashMap<>();

    /** Index of currently executing callback. */
    private int currentIndex = 0;

    /** Whether chain completed successfully. */
    private boolean completed = false;

    /** Whether chain was rolled back. */
    private boolean rolledBack = false;

    /** Timestamp when chain execution started (epoch millis). */
    private final long startTime;

    /**
     * ChainContext.
     * 
     * @param event event
     * @param initialArgs initialArgs
     * @param initialKwargs initialKwargs
     * @since 0.1.7
     */
    public ChainContext(String event, Object[] initialArgs, Map<String, Object> initialKwargs) {
        this.event = event;
        this.initialArgs = initialArgs != null ? initialArgs : new Object[0];
        this.initialKwargs = initialKwargs != null ? new HashMap<>(initialKwargs) : new HashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Get the result from the previous callback.
     * 
     * @return Last result in the chain, or null if no results
     * @since 0.1.7
     */
    public Object getLastResult() {
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }

    /**
     * Get all results from executed callbacks.
     * 
     * @return Copy of all results list
     * @since 0.1.7
     */
    public List<Object> getAllResults() {
        return new ArrayList<>(results);
    }

    /**
     * Store metadata in the context.
     * 
     * @param key Metadata key
     * @param value Metadata value
     * @since 0.1.7
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Retrieve metadata from the context.
     * 
     * @param key Metadata key
     * @param defaultValue Default value if key not found
     * @return Metadata value or default
     * @since 0.1.7
     */
    public Object getMetadata(String key, Object defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Calculate elapsed time since chain start.
     * 
     * @return Elapsed time in seconds
     * @since 0.1.7
     */
    public double getElapsedTime() {
        return (System.currentTimeMillis() - startTime) / 1000.0;
    }
}
