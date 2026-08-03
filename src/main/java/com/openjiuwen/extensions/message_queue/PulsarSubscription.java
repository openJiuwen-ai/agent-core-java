/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.message_queue;

import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageSerializer;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.mq.AsyncMessageHandler;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Pulsar-backed subscription consumer loop.
 *
 * <p>Mirrors Python's {@code PulsarSubscription} in
 * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.</p>
 */
public class PulsarSubscription extends SubscriptionBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarSubscription.class);
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(1);

    private final String topic;
    private final MessageQueuePulsar.PulsarConsumer consumer;
    private final ExecutorService executor;

    private AsyncMessageHandler<Object, Object> handler;
    private Future<?> task;
    private volatile boolean active;

    public PulsarSubscription(String topic, MessageQueuePulsar.PulsarConsumer consumer, ExecutorService executor) {
        this.topic = topic;
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
        this.handler = handler;
    }

    @Override
    public void activate() {
        if (!active) {
            active = true;
            task = executor.submit(this::consumeLoop);
            LOGGER.info("[PulsarSubscription] activated topic={}", topic);
        }
    }

    @Override
    public void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        if (task != null) {
            task.cancel(true);
        }
        try {
            executor.submit(() -> {
                consumer.close();
                return null;
            }).get();
        } catch (Exception exception) {
            LOGGER.warn("[PulsarSubscription] close consumer failed topic={}", topic, exception);
        }
        awaitCancelledTask();
        task = null;
        LOGGER.info("[PulsarSubscription] deactivated topic={}", topic);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    private void consumeLoop() {
        while (active) {
            try {
                MessageQueuePulsar.PulsarMessage message = consumer.receive(RECEIVE_TIMEOUT.toMillis());
                DmqMessage payload = MessageSerializer.deserializeMessage(message.data());
                LOGGER.info("[PulsarSubscription] Received message, topic={}, message_id={}, type:{}",
                        topic, payload.getMessageId(), payload.getClass().getSimpleName());
                AsyncMessageHandler<Object, Object> currentHandler = handler;
                if (currentHandler != null) {
                    currentHandler.handle(payload).get();
                }
                consumer.acknowledge(message);
            } catch (TimeoutException timeout) {
                // Python catches pulsar.Timeout and continues polling.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                LOGGER.warn("[PulsarSubscription] receive error: {}", exception.getMessage(), exception);
            }
        }
    }

    private void awaitCancelledTask() {
        if (task == null || task.isDone()) {
            return;
        }
        try {
            task.get(100, TimeUnit.MILLISECONDS);
        } catch (CancellationException ignored) {
            // Expected after deactivate cancels the consume loop.
        } catch (Exception ignored) {
            // Python swallows CancelledError during deactivation.
        }
    }
}
