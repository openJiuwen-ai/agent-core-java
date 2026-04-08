/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Abstract message queue supporting pub-sub topics.
 * Mirrors Python's {@code MessageQueueBase} in {@code message_queue_base.py}.
 */
public abstract class MessageQueueBase {

    public abstract void start();

    public abstract void stop();

    public abstract SubscriptionBase subscribe(String topic);

    public abstract void unsubscribe(String topic);

    public abstract void produceMessage(String topic, QueueMessage message);
}
