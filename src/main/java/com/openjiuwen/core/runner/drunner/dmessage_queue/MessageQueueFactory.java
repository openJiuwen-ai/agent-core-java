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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for distributed-runner message queues.
 *
 * <p>Mirrors Python's {@code MessageQueueFactory} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_queue_factory.py}.</p>
 */
public final class MessageQueueFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageQueueFactory.class);
    private static final String PULSAR_CLASS = "com.openjiuwen.extensions.message_queue.MessageQueuePulsar";

    private MessageQueueFactory() {
    }

    public static MessageQueueBase create(MessageQueueConfig config) {
        String mqType = config.getType().toLowerCase();
        if (MessageQueueType.FAKE.getValue().equals(mqType)) {
            return new FakeMessageQueue();
        }
        if (MessageQueueType.PULSAR.getValue().equals(mqType)) {
            return createPulsarMessageQueue(config.getPulsarConfig(), mqType);
        }
        throw initiationError(mqType, "Unknown MQ type: " + mqType, null);
    }

    private static MessageQueueBase createPulsarMessageQueue(PulsarConfig pulsarConfig, String mqType) {
        try {
            Class<?> mqClass = Class.forName(PULSAR_CLASS);
            Constructor<?> constructor = mqClass.getConstructor(PulsarConfig.class);
            return (MessageQueueBase) constructor.newInstance(pulsarConfig);
        } catch (ClassNotFoundException e) {
            LOGGER.error("[MessageQueueFactory] Failed to import Pulsar MQ: {}", e.getMessage(), e);
            throw initiationError(mqType, mqType + " import error", e);
        } catch (ReflectiveOperationException | ClassCastException e) {
            Throwable cause = unwrapInvocation(e);
            throw initiationError(mqType, mqType + " import error", cause);
        }
    }

    private static RuntimeException initiationError(String mqType, String reason, Throwable cause) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", mqType);
        params.put("reason", reason);
        return ErrorHelper.buildError(StatusCode.MESSAGE_QUEUE_INITIATION_ERROR, null, null, cause, params);
    }

    private static Throwable unwrapInvocation(Throwable error) {
        if (error instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getTargetException() != null) {
            return invocationTargetException.getTargetException();
        }
        return error;
    }
}
