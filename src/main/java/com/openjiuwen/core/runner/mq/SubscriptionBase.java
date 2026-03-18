/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.mq;

import java.util.function.Function;

/**
 * Abstract subscription that processes received messages.
 * Mirrors Python's {@code SubscriptionBase} in {@code message_queue_base.py}.
 */
public abstract class SubscriptionBase {

    public void setMessageHandler(Function<Object, Object> handler) {
    }

    public void activate() {
    }

    public void deactivate() {
    }

    public boolean isActive() {
        return false;
    }
}
