/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

/**
 * Cancel event placed into the collector queue to wake up blocked waiters.
 * 
 * @since 0.1.7
 */
public final class CancelEvent {
    private final CancelReason reason;
    private final String info;

    /**
     * CancelEvent.
     * 
     * @param reason reason
     * @since 0.1.7
     */
    public CancelEvent(CancelReason reason) {
        this(reason, null);
    }

    /**
     * CancelEvent.
     * 
     * @param reason reason
     * @param info info
     * @since 0.1.7
     */
    public CancelEvent(CancelReason reason, String info) {
        this.reason = reason;
        this.info = info;
    }

    /**
     * getReason.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CancelReason getReason() {
        return reason;
    }

    /**
     * getInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getInfo() {
        return info;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "CancelEvent{reason=" + reason + ", info='" + info + "'}";
    }
}
