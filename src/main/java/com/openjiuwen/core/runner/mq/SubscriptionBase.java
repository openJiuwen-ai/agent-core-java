/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Abstract subscription that processes received messages.
 * Mirrors Python's {@code SubscriptionBase} in {@code message_queue_base.py}.
 * 
 * @since 0.1.7
 */
public abstract class SubscriptionBase {
    /**
     * setMessageHandler.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
    }

    /**
     * activate.
     * 
     * @since 0.1.7
     */
    public void activate() {
    }

    /**
     * deactivate.
     * 
     * @since 0.1.7
     */
    public void deactivate() {
    }

    /**
     * isActive.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isActive() {
        return false;
    }
}
