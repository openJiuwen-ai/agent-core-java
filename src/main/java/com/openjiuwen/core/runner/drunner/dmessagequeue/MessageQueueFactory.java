// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;

import java.util.logging.Logger;

/**
 * Factory for creating message queue instances based on configuration.
 * 
 * 对应Python: drunner/dmessage_queue/message_queue_factory.py - MessageQueueFactory
 */
public class MessageQueueFactory {

    private static final Logger LOG = Logger.getLogger(MessageQueueFactory.class.getName());

    /**
     * Create a message queue instance based on the given configuration.
     *
     * @param config the message queue configuration
     * @return a MessageQueueBase instance
     * @throws com.openjiuwen.core.common.exception.BaseError if the MQ type is unknown or init fails
     */
    public static MessageQueueBase create(MessageQueueConfig config) {
        String mqType = config.type().toLowerCase();

        if (mqType.equals(MessageQueueType.FAKE.getValue())) {
            return new FakeMQ();
        } else if (mqType.equals(MessageQueueType.PULSAR.getValue())) {
            try {
                // Try to load Pulsar MQ via reflection (extension module)
                Class<?> clazz = Class.forName("com.openjiuwen.extensions.runner.pulsar.MessageQueuePulsar");
                var constructor = clazz.getConstructor(com.openjiuwen.core.runner.PulsarConfig.class);
                return (MessageQueueBase) constructor.newInstance(config.pulsarConfig());
            } catch (ClassNotFoundException e) {
                LOG.severe("[MessageQueueFactory] Failed to import Pulsar MQ: " + e.getMessage());
                throw ErrorBuilder.build(StatusCode.MESSAGE_QUEUE_INIT_ERROR, mqType + " import error");
            } catch (Exception e) {
                throw ErrorBuilder.build(StatusCode.MESSAGE_QUEUE_INIT_ERROR, mqType + " import error");
            }
        } else {
            throw ErrorBuilder.build(StatusCode.MESSAGE_QUEUE_INIT_ERROR, "Unknown MQ type: " + mqType);
        }
    }
}

