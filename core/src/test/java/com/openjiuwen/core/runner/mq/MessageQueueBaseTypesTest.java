/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for base message queue contracts.
 *
 * <p>Mirrors the contract surface exercised by
 * {@code tests/unit_tests/core/runner/test_message_queue.py}.
 */
class MessageQueueBaseTypesTest {

    @Test
    @DisplayName("queue message defaults mirror the Python model")
    void testQueueMessageDefaults() {
        QueueMessage message = new QueueMessage();

        assertEquals("", message.getMessageId());
        assertEquals(StatusCode.SUCCESS.getCode(), message.getErrorCode());
        assertEquals("", message.getErrorMsg());
        assertEquals(null, message.getPayload());
    }

    @Test
    @DisplayName("queue message setters keep explicit values")
    void testQueueMessageSetters() {
        QueueMessage message = new QueueMessage("msg-1", "payload");
        message.setErrorCode(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode());
        message.setErrorMsg("boom");

        assertEquals("msg-1", message.getMessageId());
        assertEquals("payload", message.getPayload());
        assertEquals(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode(), message.getErrorCode());
        assertEquals("boom", message.getErrorMsg());
    }

    @Test
    @DisplayName("local message queue start and stop return true")
    void testLocalMessageQueueLifecycle() {
        LocalMessageQueue queue = new LocalMessageQueue();

        assertTrue(queue.start());
        assertTrue(queue.stop());
    }

    @Test
    @DisplayName("invoke queue message initializes a response future")
    void testInvokeQueueMessageInitializesResponseFuture() {
        InvokeQueueMessage message = new InvokeQueueMessage("invoke-1", "payload");

        assertEquals("invoke-1", message.getMessageId());
        assertEquals("payload", message.getPayload());
        assertNotNull(message.getResponse());
        assertFalse(message.getResponse().isDone());
    }

    @Test
    @DisplayName("stream queue message initializes an iterator response future")
    void testStreamQueueMessageInitializesResponseFuture() throws Exception {
        StreamQueueMessage message = new StreamQueueMessage("stream-1", "payload");
        Iterator<Object> iterator = List.<Object>of("a", "b").iterator();
        message.getResponse().complete(iterator);

        assertEquals("stream-1", message.getMessageId());
        assertEquals("payload", message.getPayload());
        assertSame(iterator, message.getResponse().get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("subscription base default lifecycle is inert")
    void testSubscriptionBaseDefaultLifecycle() {
        SubscriptionBase subscription = new SubscriptionBase() {
        };

        subscription.setMessageHandler(message -> CompletableFuture.completedFuture(message));
        subscription.activate();
        subscription.deactivate();

        assertFalse(subscription.isActive());
    }

    @Test
    @DisplayName("async message handler returns completable future")
    void testAsyncMessageHandlerContract() throws Exception {
        AsyncMessageHandler<Object, Object> handler =
                message -> CompletableFuture.completedFuture("handled:" + message);

        assertEquals("handled:payload", handler.handle("payload").get(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("message queue base subclass can wire topic operations")
    void testMessageQueueBaseSubclassContract() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicBoolean unsubscribed = new AtomicBoolean(false);
        QueueMessage[] delivered = new QueueMessage[1];

        MessageQueueBase queue = new MessageQueueBase() {
            @Override
            public void start() {
                started.set(true);
            }

            @Override
            public void stop() {
                stopped.set(true);
            }

            @Override
            public SubscriptionBase subscribe(String topic) {
                return new SubscriptionBase() {
                };
            }

            @Override
            public void unsubscribe(String topic) {
                unsubscribed.set(true);
            }

            @Override
            public void produceMessage(String topic, QueueMessage message) {
                delivered[0] = message;
            }
        };

        QueueMessage message = new QueueMessage("msg", "payload");
        queue.start();
        SubscriptionBase subscription = queue.subscribe("topic");
        queue.produceMessage("topic", message);
        queue.unsubscribe("topic");
        queue.stop();

        assertTrue(started.get());
        assertTrue(stopped.get());
        assertTrue(unsubscribed.get());
        assertSame(message, delivered[0]);
        assertNotNull(subscription);
    }
}
