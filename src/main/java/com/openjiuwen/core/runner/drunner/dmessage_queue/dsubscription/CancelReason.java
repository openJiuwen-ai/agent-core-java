/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

/**
 * Cancellation reason for {@link ResponseCollector}.
 * Used to distinguish exception types after awakening.
 * 
 * @since 0.1.7
 */
public enum CancelReason {
    RUNNER_STOPPED("runner_stopped"),
    /** TTL expired. */
    TTL_EXPIRE("ttl_expire"),
    /** Queue full. */
    QUEUE_FULL("queue_full"),
    /** Normal completion, no need to wake up. */
    FINISH("finish");

    private final String value;

    CancelReason(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }
}
