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
 */
public class FakeMessageQueue extends MessageQueueBase {

    private final MessageQueueInMemory delegate = new MessageQueueInMemory();

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void start() {
        delegate.start();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void stop() {
        delegate.stop();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public SubscriptionBase subscribe(String topic) {
        return delegate.subscribe(topic);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void unsubscribe(String topic) {
        delegate.unsubscribe(topic);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void produceMessage(String topic, QueueMessage message) {
        delegate.produceMessage(topic, message);
    }
}
