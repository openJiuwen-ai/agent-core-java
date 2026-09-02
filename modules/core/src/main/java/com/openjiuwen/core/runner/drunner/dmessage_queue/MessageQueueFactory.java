/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.mq.MessageQueueBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/**
 * Factory for distributed-runner message queues.
 * 
 * @since 0.1.7
 */
public final class MessageQueueFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MessageQueueFactory.class);

    /**
     * MessageQueueFactory.
     * 
     * @since 0.1.7
     */
    private MessageQueueFactory() {
    }

    /**
     * create.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static MessageQueueBase create(MessageQueueConfig config) {
        String mqType = config != null && config.getType() != null
                ? config.getType().toLowerCase(Locale.ROOT)
                : MessageQueueType.FAKE.getValue();
        if (MessageQueueType.PULSAR.getValue().equals(mqType)) {
            return createPulsarMessageQueue(config != null ? config.getPulsarConfig() : null, mqType);
        }
        return new FakeMessageQueue();
    }

    /**
     * createPulsarMessageQueue.
     * 
     * @param pulsarConfig pulsarConfig
     * @param mqType mqType
     * @return the result
     * @since 0.1.7
     */
    private static MessageQueueBase createPulsarMessageQueue(PulsarConfig pulsarConfig, String mqType) {
        try {
            Class<?> mqClass = Class.forName("com.openjiuwen.extensions.message_queue.MessageQueuePulsar");
            Constructor<?> constructor = mqClass.getConstructor(PulsarConfig.class);
            Object instance = constructor.newInstance(pulsarConfig);
            return (MessageQueueBase) instance;
        } catch (ClassNotFoundException e) {
            LOG.error("Pulsar MQ extension class is not available: {}", e.getMessage());
            throw ErrorHelper.buildError(StatusCode.MESSAGE_QUEUE_INITIATION_ERROR, "type", mqType, "reason",
                    "pulsar extension class not found");
        } catch (ReflectiveOperationException e) {
            Throwable root = e instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getTargetException()
                    : e;
            LOG.error("Failed to instantiate Pulsar MQ: {}", root.getMessage(), root);
            throw ErrorHelper.buildError(StatusCode.MESSAGE_QUEUE_INITIATION_ERROR, "type", mqType, "reason",
                    root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName());
        }
    }
}
