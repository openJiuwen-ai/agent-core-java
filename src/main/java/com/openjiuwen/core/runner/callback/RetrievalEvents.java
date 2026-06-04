/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for knowledge retrieval operations.
 * 
 * <p>Mirrors Python's {@code RetrievalEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class RetrievalEvents {

    /** Knowledge retrieval started */
    public static final String RETRIEVAL_STARTED = Events.getEvent("retrieval_started");

    private RetrievalEvents() {
        // Utility class
    }
}