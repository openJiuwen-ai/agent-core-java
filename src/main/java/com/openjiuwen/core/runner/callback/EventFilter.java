/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;

/**
 * Base class for event filters.
 * <p>
 * Filters can intercept and modify callback execution before it occurs.
 * Subclass this to create custom filters.
 * 
 * @since 0.1.7
 */
public class EventFilter {
    private final String name;

    /**
     * EventFilter.
     * 
     * @since 0.1.7
     */
    public EventFilter() {
        this.name = getClass().getSimpleName();
    }

    /**
     * EventFilter.
     * 
     * @param name name
     * @since 0.1.7
     */
    public EventFilter(String name) {
        this.name = (name != null && !name.isEmpty()) ? name : getClass().getSimpleName();
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * Filter logic to execute before callback.
     * Override this method to implement custom filtering logic.
     * 
     * @param event Event name being processed
     * @param callback Callback about to be executed
     * @param args Positional arguments for callback
     * @param kwargs Keyword arguments for callback
     * @return FilterResult indicating action to take
     * @since 0.1.7
     */
    public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs) {
        return FilterResult.continueResult();
    }
}
