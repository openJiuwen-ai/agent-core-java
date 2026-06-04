/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for memory operations.
 * 
 * <p>Mirrors Python's {@code MemoryEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class MemoryEvents {

    /** Memory add operation (before) */
    public static final String MEMORY_ADDED = Events.getEvent("memory_added");
    
    /** Memory search operation started */
    public static final String MEMORY_SEARCH_STARTED = Events.getEvent("memory_search_started");
    
    /** Memory search operation completed successfully */
    public static final String MEMORY_SEARCH_FINISHED = Events.getEvent("memory_search_finished");
    
    /** Memory update operation (before) */
    public static final String MEMORY_UPDATED = Events.getEvent("memory_updated");
    
    /** Memory delete operation (before) */
    public static final String MEMORY_DELETED = Events.getEvent("memory_deleted");

    private MemoryEvents() {
        // Utility class
    }
}
