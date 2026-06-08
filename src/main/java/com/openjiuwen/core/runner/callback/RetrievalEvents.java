/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code RetrievalEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class RetrievalEvents {
    public static final String RETRIEVAL_STARTED = Events.getEvent("retrieval_started");

    private RetrievalEvents() {
    }
}
