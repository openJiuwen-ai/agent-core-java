/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.MessageQueueInMemory;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

/**
 * In-memory fake MQ used by the distributed-runner compatibility layer.
 * 
 * @since 0.1.7
 */
public class FakeMessageQueue extends MessageQueueBase {
    private final MessageQueueInMemory delegate = new MessageQueueInMemory();

    /**
     * start.
     * 
     * @since 0.1.7
     */
    @Override
    public void start() {
        delegate.start();
    }

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    @Override
    public void stop() {
        delegate.stop();
    }

    /**
     * subscribe.
     * 
     * @param topic topic
     * @return the result
     * @since 0.1.7
     */
    @Override
    public SubscriptionBase subscribe(String topic) {
        return delegate.subscribe(topic);
    }

    /**
     * unsubscribe.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    @Override
    public void unsubscribe(String topic) {
        delegate.unsubscribe(topic);
    }

    /**
     * produceMessage.
     * 
     * @param topic topic
     * @param message message
     * @since 0.1.7
     */
    @Override
    public void produceMessage(String topic, QueueMessage message) {
        delegate.produceMessage(topic, message);
    }
}
