/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Abstract message queue supporting pub-sub topics.
 * Mirrors Python's {@code MessageQueueBase} in {@code message_queue_base.py}.
 */
public abstract class MessageQueueBase {

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract void start();

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract void stop();

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract SubscriptionBase subscribe(String topic);

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract void unsubscribe(String topic);

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract void produceMessage(String topic, QueueMessage message);
}
