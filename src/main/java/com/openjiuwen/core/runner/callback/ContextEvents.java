/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for context management.
 * 
 * <p>Mirrors Python's {@code ContextEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class ContextEvents {

    /** Context was updated */
    public static final String CONTEXT_UPDATED = Events.getEvent("context_updated");
    
    /** Context was offloaded to storage */
    public static final String CONTEXT_OFFLOADED = Events.getEvent("context_offloaded");
    
    /** Context was retrieved from storage */
    public static final String CONTEXT_RETRIEVED = Events.getEvent("context_retrieved");
    
    /** Context was cleared */
    public static final String CONTEXT_CLEARED = Events.getEvent("context_cleared");
    
    /** Context compression state */
    public static final String CONTEXT_COMPRESSION_STATE = Events.getEvent("context_compression_state");

    private ContextEvents() {
        // Utility class
    }
}
