/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Abstract message queue supporting pub-sub topics.
 * Mirrors Python's {@code MessageQueueBase} in {@code message_queue_base.py}.
 * 
 * @since 0.1.7
 */
public abstract class MessageQueueBase {
    /**
     * start.
     * 
     * @since 0.1.7
     */
    public abstract void start();

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    public abstract void stop();

    /**
     * subscribe.
     * 
     * @param topic topic
     * @return the result
     * @since 0.1.7
     */
    public abstract SubscriptionBase subscribe(String topic);

    /**
     * unsubscribe.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    public abstract void unsubscribe(String topic);

    /**
     * produceMessage.
     * 
     * @param topic topic
     * @param message message
     * @since 0.1.7
     */
    public abstract void produceMessage(String topic, QueueMessage message);
}
