/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

/**
 * Cancellation signal placed into a collector queue.
 *
 * <p>Mirrors Python's {@code CancelEvent} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/dsubscription/response_collector.py}.</p>
 */
public record CancelEvent(CancelReason reason, String info) {

    public CancelEvent(CancelReason reason) {
        this(reason, null);
    }
}
