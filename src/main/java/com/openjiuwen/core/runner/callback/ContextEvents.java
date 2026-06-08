/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code ContextEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class ContextEvents {
    public static final String CONTEXT_UPDATED = Events.getEvent("context_updated");
    public static final String CONTEXT_OFFLOADED = Events.getEvent("context_offloaded");
    public static final String CONTEXT_RETRIEVED = Events.getEvent("context_retrieved");
    public static final String CONTEXT_CLEARED = Events.getEvent("context_cleared");
    public static final String CONTEXT_COMPRESSION_STATE = Events.getEvent("context.compression_state");

    private ContextEvents() {
    }
}
