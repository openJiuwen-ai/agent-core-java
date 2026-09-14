/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Cancellation reason used to wake blocked response waiters.
 *
 * <p>Mirrors Python's {@code CancelReason} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/dsubscription/response_collector.py}.</p>
 */
public enum CancelReason {
    RUNNER_STOPPED("runner_stopped"),
    TTL_EXPIRE("ttl_expire"),
    QUEUE_FULL("queue_full"),
    FINISH("finish");

    private final String value;

    CancelReason(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CancelReason fromValue(String value) {
        for (CancelReason reason : values()) {
            if (reason.value.equals(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown cancel reason: " + value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
