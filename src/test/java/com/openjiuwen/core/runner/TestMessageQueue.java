/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.mq.AsyncMessageHandler;
import com.openjiuwen.core.runner.mq.InvokeQueueMessage;
import com.openjiuwen.core.runner.mq.MessageQueueInMemory;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.StreamQueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageQueue.
 * Mirrors Python's tests/unit_tests/core/runner/test_message_queue.py
 */
@DisplayName("MessageQueue Tests")
class TestMessageQueue {

    private MessageQueueInMemory mq;

    @BeforeEach
    void setUp() {
        mq = null;
    }

    @AfterEach
    void tearDown() {
        if (mq != null) {
            mq.stop();
        }
    }

    private static class MockMessageHandlerStream implements AsyncMessageHandler<Object, Object> {
        @Override
        public CompletableFuture<Object> handle(Object request) {
            return CompletableFuture.supplyAsync(() -> {
                List<String> responses = new ArrayList<>();
                for (int i = 1; i <= 9; i++) {
                    responses.add("MockMessageHandlerStream response for msg : " + request + ", i is " + i);
                }
                return responses.iterator();
            });
        }
    }

    private static class MockMessageHandlerInvoke implements AsyncMessageHandler<Object, Object> {
        @Override
        public CompletableFuture<Object> handle(Object request) {
            return CompletableFuture.supplyAsync(() -> "MockMessageHandlerInvoke response for msg : " + request);
        }
    }

    private static Object getResponse(CompletableFuture<?> response, long timeoutSeconds) {
        try {
            return response.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            return null;
        }
    }

    private void messageQueueCommon(MessageQueueInMemory mq) throws Exception {
        mq.start();

        SubscriptionBase subscription1 = mq.subscribe("topic_stream");
        subscription1.setMessageHandler(new MockMessageHandlerStream());
        subscription1.activate();

        SubscriptionBase subscription2 = mq.subscribe("topic_invoke");
        subscription2.setMessageHandler(new MockMessageHandlerInvoke());
        subscription2.activate();

        Thread.sleep(100);

        StreamQueueMessage message = new StreamQueueMessage();
        message.setPayload("上海温度多少");
        mq.produceMessage("topic_stream", message);
        Iterator<?> response = (Iterator<?>) getResponse(message.getResponse(), 1);
        assertNotNull(response);
        assertEquals(StatusCode.SUCCESS.getCode(), message.getErrorCode());
        assertEquals("", message.getErrorMsg());
        int i = 1;
        while (response.hasNext()) {
            Object ret = response.next();
            assertEquals("MockMessageHandlerStream response for msg : 上海温度多少, i is " + i, ret);
            i++;
        }

        InvokeQueueMessage message1 = new InvokeQueueMessage();
        message1.setPayload("上海温度多少");
        mq.produceMessage("topic_stream", message1);
        Object response1 = getResponse(message1.getResponse(), 1);
        assertNull(response1);
        assertEquals(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode(), message1.getErrorCode());

        QueueMessage message2 = new QueueMessage();
        message2.setPayload("上海温度多少");
        mq.produceMessage("topic_stream", message2);
        assertEquals(StatusCode.SUCCESS.getCode(), message2.getErrorCode());
        assertEquals("", message2.getErrorMsg());

        InvokeQueueMessage message3 = new InvokeQueueMessage();
        message3.setPayload("北京温度多少");
        mq.produceMessage("topic_invoke", message3);
        Object response3 = getResponse(message3.getResponse(), 1);
        assertNotNull(response3);
        assertEquals("MockMessageHandlerInvoke response for msg : 北京温度多少", response3);
        assertEquals(StatusCode.SUCCESS.getCode(), message3.getErrorCode());
        assertEquals("", message3.getErrorMsg());

        StreamQueueMessage message4 = new StreamQueueMessage();
        message4.setPayload("北京温度多少");
        mq.produceMessage("topic_invoke", message4);
        Object response4 = getResponse(message4.getResponse(), 1);
        assertNull(response4);
        assertEquals(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode(), message4.getErrorCode());

        QueueMessage message5 = new QueueMessage();
        message5.setPayload("北京温度多少");
        mq.produceMessage("topic_invoke", message5);
        assertEquals(StatusCode.SUCCESS.getCode(), message5.getErrorCode());
        assertEquals("", message5.getErrorMsg());

        mq.unsubscribe("topic_invoke");
        mq.unsubscribe("topic_stream");
        mq.stop();
    }

    @Nested
    @DisplayName("MessageQueue tests")
    class QueueTests {

        @Test
        @DisplayName("test message queue inmemory")
        void testMessageQueueInmemory() throws Exception {
            mq = new MessageQueueInMemory();
            messageQueueCommon(mq);
        }
    }

    @Nested
    @DisplayName("Nested send deadlock tests")
    class NestedSendTests {

        @Test
        @DisplayName("test nested send no deadlock")
        void testNestedSendNoDeadlock() throws Exception {
            String TOPIC = "test_nested_send";
            double TIMEOUT = 5.0;
            mq = new MessageQueueInMemory(100, (long) (TIMEOUT * 1000));
            AtomicInteger callCount = new AtomicInteger(0);

            AsyncMessageHandler<Object, Object> dispatch = payload -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    InvokeQueueMessage innerMsg = new InvokeQueueMessage();
                    innerMsg.setMessageId(UUID.randomUUID().toString());
                    innerMsg.setPayload("inner");
                    mq.produceMessage(TOPIC, innerMsg);
                    return innerMsg.getResponse().thenApply(innerResult -> "outer(" + innerResult + ")");
                }
                return CompletableFuture.completedFuture("inner_done");
            };

            SubscriptionBase subscription = mq.subscribe(TOPIC);
            subscription.setMessageHandler(dispatch);
            subscription.activate();
            mq.start();

            Thread.sleep(100);

            InvokeQueueMessage outerMsg = new InvokeQueueMessage();
            outerMsg.setMessageId(UUID.randomUUID().toString());
            outerMsg.setPayload("outer");
            mq.produceMessage(TOPIC, outerMsg);

            try {
                Object result = outerMsg.getResponse().get((long) (TIMEOUT * 1000), TimeUnit.MILLISECONDS);
                assertEquals("outer(inner_done)", result);
                assertEquals(2, callCount.get());
            } finally {
                mq.stop();
            }
        }

        @Test
        @DisplayName("test nested send three levels")
        void testNestedSendThreeLevels() throws Exception {
            String TOPIC = "test_nested_three";
            double TIMEOUT = 5.0;
            mq = new MessageQueueInMemory(100, (long) (TIMEOUT * 1000));
            AtomicInteger callCount = new AtomicInteger(0);

            AsyncMessageHandler<Object, Object> levelC = payload -> CompletableFuture.completedFuture("C");

            AsyncMessageHandler<Object, Object> levelB = payload -> {
                InvokeQueueMessage msg = new InvokeQueueMessage();
                msg.setMessageId(UUID.randomUUID().toString());
                msg.setPayload("c");
                mq.produceMessage(TOPIC, msg);
                return msg.getResponse().thenApply(cResult -> "B(" + cResult + ")");
            };

            AsyncMessageHandler<Object, Object> levelA = payload -> {
                InvokeQueueMessage msg = new InvokeQueueMessage();
                msg.setMessageId(UUID.randomUUID().toString());
                msg.setPayload("b");
                mq.produceMessage(TOPIC, msg);
                return msg.getResponse().thenApply(bResult -> "A(" + bResult + ")");
            };

            AsyncMessageHandler<Object, Object> dispatch = payload -> {
                int count = callCount.incrementAndGet();
                if (count == 1) return levelA.handle(payload);
                if (count == 2) return levelB.handle(payload);
                return levelC.handle(payload);
            };

            SubscriptionBase subscription = mq.subscribe(TOPIC);
            subscription.setMessageHandler(dispatch);
            subscription.activate();
            mq.start();

            Thread.sleep(100);

            InvokeQueueMessage rootMsg = new InvokeQueueMessage();
            rootMsg.setMessageId(UUID.randomUUID().toString());
            rootMsg.setPayload("a");
            mq.produceMessage(TOPIC, rootMsg);

            try {
                Object result = rootMsg.getResponse().get((long) (TIMEOUT * 1000), TimeUnit.MILLISECONDS);
                assertEquals("A(B(C))", result);
                assertEquals(3, callCount.get());
            } finally {
                mq.stop();
            }
        }

        @Test
        @DisplayName("test nested send task done called once")
        void testNestedSendTaskDoneCalledOnce() throws Exception {
            String TOPIC = "test_task_done";
            double TIMEOUT = 5.0;
            mq = new MessageQueueInMemory(100, (long) (TIMEOUT * 1000));
            AtomicInteger callCount = new AtomicInteger(0);

            AsyncMessageHandler<Object, Object> outer = payload -> {
                InvokeQueueMessage innerMsg = new InvokeQueueMessage();
                innerMsg.setMessageId(UUID.randomUUID().toString());
                innerMsg.setPayload("inner");
                mq.produceMessage(TOPIC, innerMsg);
                return innerMsg.getResponse().thenApply(res -> "ok(" + res + ")");
            };

            AsyncMessageHandler<Object, Object> dispatch = payload -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    return outer.handle(payload);
                }
                return CompletableFuture.completedFuture("inner_result");
            };

            SubscriptionBase subscription = mq.subscribe(TOPIC);
            subscription.setMessageHandler(dispatch);
            subscription.activate();
            mq.start();

            Thread.sleep(100);

            InvokeQueueMessage msg = new InvokeQueueMessage();
            msg.setMessageId(UUID.randomUUID().toString());
            msg.setPayload("start");
            mq.produceMessage(TOPIC, msg);

            try {
                Object result = msg.getResponse().get((long) (TIMEOUT * 1000), TimeUnit.MILLISECONDS);
                Thread.sleep(100);
                assertEquals("ok(inner_result)", result);
                assertEquals(2, callCount.get());
            } finally {
                mq.stop();
            }
        }
    }
}