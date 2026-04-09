/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

/**
 * Cancel event placed into the collector queue to wake up blocked waiters.
 */
public final class CancelEvent {

    private final CancelReason reason;
    private final String info;

    public CancelEvent(CancelReason reason) {
        this(reason, null);
    }

    public CancelEvent(CancelReason reason, String info) {
        this.reason = reason;
        this.info = info;
    }

    public CancelReason getReason() {
        return reason;
    }

    public String getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return "CancelEvent{reason=" + reason + ", info='" + info + "'}";
    }
}
