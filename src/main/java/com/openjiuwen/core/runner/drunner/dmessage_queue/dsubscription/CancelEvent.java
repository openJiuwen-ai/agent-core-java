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

    /**
     * Auto-generated for codecheck compliance.
     */
    public CancelEvent(CancelReason reason) {
        this(reason, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CancelEvent(CancelReason reason, String info) {
        this.reason = reason;
        this.info = info;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CancelReason getReason() {
        return reason;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getInfo() {
        return info;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "CancelEvent{reason=" + reason + ", info='" + info + "'}";
    }
}
