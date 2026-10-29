/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

/**
 * Supplies owner-bound interruption signals for cooperative cancellation.
 * A signal only requests interruption; callers must still cancel pending work and await termination.
 *
 * @since 2026-10-28
 */
public final class InterruptSignals {
    private InterruptSignals() {
    }

    /**
     * Captures the current worker so a cancellation owner can signal it from another thread.
     * The owner must discard the signal when the worker's registered operation ends.
     *
     * @return a cooperative interruption signal bound to the calling thread
     */
    public static Runnable captureCurrentThread() {
        return Thread.currentThread()::interrupt;
    }

    /**
     * Restores the interruption status consumed by an interruptible blocking operation.
     * This does not replace propagating the failure or terminating the interrupted operation.
     */
    public static void restoreCurrentThread() {
        captureCurrentThread().run();
    }
}
