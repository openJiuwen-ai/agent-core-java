// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

/**
 * Cancel event placed into the collector queue to wake up blocked waiters.
 * 
 * 对应Python: drunner/dmessage_queue/dsubscription/response_collector.py - CancelEvent
 */
public record CancelEvent(CancelReason reason, String info) {
    public CancelEvent(CancelReason reason) {
        this(reason, null);
    }
}

