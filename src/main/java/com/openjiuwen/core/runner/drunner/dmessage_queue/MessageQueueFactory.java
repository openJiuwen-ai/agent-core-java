/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for distributed-runner message queues.
 */
public final class MessageQueueFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MessageQueueFactory.class);

    private MessageQueueFactory() {
    }

    public static MessageQueueBase create(MessageQueueConfig config) {
        String mqType = config != null && config.getType() != null ? config.getType().toLowerCase() : MessageQueueType.FAKE.getValue();
        if (MessageQueueType.PULSAR.getValue().equals(mqType)) {
            LOG.warn("Pulsar MQ is not bundled in agent-core-java yet, falling back to fake MQ");
        }
        return new FakeMessageQueue();
    }
}
