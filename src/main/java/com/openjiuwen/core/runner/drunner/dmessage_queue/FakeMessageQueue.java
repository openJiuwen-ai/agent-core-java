/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.MessageQueueInMemory;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

/**
 * In-memory fake MQ used by the distributed-runner compatibility layer.
 */
public class FakeMessageQueue extends MessageQueueBase {

    private final MessageQueueInMemory delegate = new MessageQueueInMemory();

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public SubscriptionBase subscribe(String topic) {
        return delegate.subscribe(topic);
    }

    @Override
    public void unsubscribe(String topic) {
        delegate.unsubscribe(topic);
    }

    @Override
    public void produceMessage(String topic, QueueMessage message) {
        delegate.produceMessage(topic, message);
    }
}
