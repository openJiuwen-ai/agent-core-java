/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code MemoryEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class MemoryEvents {
    public static final String MEMORY_ADDED = Events.getEvent("memory_added");
    public static final String MEMORY_SEARCH_STARTED = Events.getEvent("memory_search_started");
    public static final String MEMORY_SEARCH_FINISHED = Events.getEvent("memory_search_finished");
    public static final String MEMORY_UPDATED = Events.getEvent("memory_updated");
    public static final String MEMORY_DELETED = Events.getEvent("memory_deleted");

    private MemoryEvents() {
    }
}
