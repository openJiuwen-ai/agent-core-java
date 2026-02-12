// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

/**
 * Cancellation reason: used to distinguish exception types after awakening.
 * 
 * 对应Python: drunner/dmessage_queue/dsubscription/response_collector.py - CancelReason
 */
public enum CancelReason {
    /** Runner/Adapter actively stopped (should throw RUNNER_STOPPED) */
    RUNNER_STOPPED("runner_stopped"),
    /** TTL expired (should throw TimeoutException) */
    TTL_EXPIRE("ttl_expire"),
    /** Queue full (should throw CancellationException) */
    QUEUE_FULL("queue_full"),
    /** Normal completion, no need to wake up */
    FINISH("finish");

    private final String value;

    CancelReason(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

