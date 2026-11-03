/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

/**
 * Callback invoked by loop lifecycle events.
 *
 * <p>Mirrors Python's {@code LoopCallback} in
 * {@code openjiuwen/core/workflow/components/flow/loop/callback/loop_callback.py}.</p>
 */
@FunctionalInterface
public interface LoopCallback {

    /**
     * Handles a loop event name.
     *
     * @param event loop event name
     */
    void onLoopEvent(String event);
}
