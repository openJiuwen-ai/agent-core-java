/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Abstract subscription that processes received messages.
 * Mirrors Python's {@code SubscriptionBase} in {@code message_queue_base.py}.
 */
public abstract class SubscriptionBase {

    /**
     * Sets the async message handler for this subscription.
     *
     * @param handler the async message handler
     */
    public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
    }

    public void activate() {
    }

    public void deactivate() {
    }

    public boolean isActive() {
        return false;
    }
}
