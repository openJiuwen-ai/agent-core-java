/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Abstract subscription that processes received messages.
 *
 * <p>Mirrors Python's {@code SubscriptionBase} in
 * {@code openjiuwen/core/runner/message_queue_base.py}.
 */
public abstract class SubscriptionBase {

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
