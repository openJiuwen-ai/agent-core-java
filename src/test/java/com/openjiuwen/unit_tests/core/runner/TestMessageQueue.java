/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.runner;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for MessageQueue.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/runner/test_message_queue.py}.</p>
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

    private static final class MockMessageHandlerStream implements AsyncMessageHandler<Object, Object> {
        @Override
        public CompletableFuture<Object> handle(Object request) {
            return CompletableFuture.supplyAsync(() -> {
                List<String> responses = new ArrayList<>();
                for (int i = 1; i <= 9; i++) {
                    responses.add("stream response for msg: " + request + ", i=" + i);
                }
                return responses.iterator();
            });
        }
    }

    private static final class MockMessageHandlerInvoke implements AsyncMessageHandler<Object, Object> {
        @Override
        public CompletableFuture<Object> handle(Object request) {
            return CompletableFuture.supplyAsync(() -> "invoke response for msg: " + request);
        }
    }

    private static Object getResponse(CompletableFuture<?> response, long timeoutSeconds) {
        try {
            return response.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            return null;
        }
    }

    private void verifyCommonQueueBehavior(MessageQueueInMemory queue) throws Exception {
        queue.start();

        SubscriptionBase streamSubscription = queue.subscribe("topic_stream");
        streamSubscription.setMessageHandler(new MockMessageHandlerStream());
        streamSubscription.activate();

        SubscriptionBase invokeSubscription = queue.subscribe("topic_invoke");
        invokeSubscription.setMessageHandler(new MockMessageHandlerInvoke());
        invokeSubscription.activate();

        Thread.sleep(100);

        StreamQueueMessage streamMessage = new StreamQueueMessage();
        streamMessage.setPayload("stream payload");
        queue.produceMessage("topic_stream", streamMessage);
        Iterator<?> streamResponse = (Iterator<?>) getResponse(streamMessage.getResponse(), 1);
        assertNotNull(streamResponse);
        assertEquals(StatusCode.SUCCESS.getCode(), streamMessage.getErrorCode());
        assertEquals("", streamMessage.getErrorMsg());
        int index = 1;
        while (streamResponse.hasNext()) {
            assertEquals("stream response for msg: stream payload, i=" + index, streamResponse.next());
            index++;
        }

        InvokeQueueMessage wrongInvokeMessage = new InvokeQueueMessage();
        wrongInvokeMessage.setPayload("wrong invoke");
        queue.produceMessage("topic_stream", wrongInvokeMessage);
        assertNull(getResponse(wrongInvokeMessage.getResponse(), 1));
        assertEquals(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode(), wrongInvokeMessage.getErrorCode());

        QueueMessage plainStreamMessage = new QueueMessage();
        plainStreamMessage.setPayload("plain stream");
        queue.produceMessage("topic_stream", plainStreamMessage);
        assertEquals(StatusCode.SUCCESS.getCode(), plainStreamMessage.getErrorCode());
        assertEquals("", plainStreamMessage.getErrorMsg());

        InvokeQueueMessage invokeMessage = new InvokeQueueMessage();
        invokeMessage.setPayload("invoke payload");
        queue.produceMessage("topic_invoke", invokeMessage);
        assertEquals("invoke response for msg: invoke payload", getResponse(invokeMessage.getResponse(), 1));
        assertEquals(StatusCode.SUCCESS.getCode(), invokeMessage.getErrorCode());
        assertEquals("", invokeMessage.getErrorMsg());

        StreamQueueMessage wrongStreamMessage = new StreamQueueMessage();
        wrongStreamMessage.setPayload("wrong stream");
        queue.produceMessage("topic_invoke", wrongStreamMessage);
        assertNull(getResponse(wrongStreamMessage.getResponse(), 1));
        assertEquals(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode(), wrongStreamMessage.getErrorCode());

        QueueMessage plainInvokeMessage = new QueueMessage();
        plainInvokeMessage.setPayload("plain invoke");
        queue.produceMessage("topic_invoke", plainInvokeMessage);
        assertEquals(StatusCode.SUCCESS.getCode(), plainInvokeMessage.getErrorCode());
        assertEquals("", plainInvokeMessage.getErrorMsg());

        queue.unsubscribe("topic_invoke");
        queue.unsubscribe("topic_stream");
        queue.stop();
    }

    @Nested
    @DisplayName("MessageQueue tests")
    class QueueTests {

        @Test
        @DisplayName("test message queue inmemory")
        void testMessageQueueInmemory() throws Exception {
            mq = new MessageQueueInMemory();
            verifyCommonQueueBehavior(mq);
        }
    }

    @Nested
    @DisplayName("Nested send deadlock tests")
    class NestedSendTests {

        @Test
        @DisplayName("test nested send no deadlock")
        void testNestedSendNoDeadlock() throws Exception {
            String topic = "test_nested_send";
            double timeoutSeconds = 5.0;
            mq = new MessageQueueInMemory(100, (long) (timeoutSeconds * 1000));
            AtomicInteger callCount = new AtomicInteger(0);

            AsyncMessageHandler<Object, Object> dispatch = payload -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    InvokeQueueMessage innerMessage = new InvokeQueueMessage();
                    innerMessage.setMessageId(UUID.randomUUID().toString());
                    innerMessage.setPayload("inner");
                    mq.produceMessage(topic, innerMessage);
                    return innerMessage.getResponse().thenApply(innerResult -> "outer(" + innerResult + ")");
                }
                return CompletableFuture.completedFuture("inner_done");
            };

            SubscriptionBase subscription = mq.subscribe(topic);
            subscription.setMessageHandler(dispatch);
            subscription.activate();
            mq.start();

            Thread.sleep(100);

            InvokeQueueMessage outerMessage = new InvokeQueueMessage();
            outerMessage.setMessageId(UUID.randomUUID().toString());
            outerMessage.setPayload("outer");
            mq.produceMessage(topic, outerMessage);

            try {
                Object result = outerMessage.getResponse().get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
                assertEquals("outer(inner_done)", result);
                assertEquals(2, callCount.get());
            } finally {
                mq.stop();
            }
        }

        @Test
        @DisplayName("test nested send three levels")
        void testNestedSendThreeLevels() throws Exception {
            String topic = "test_nested_three";
            double timeoutSeconds = 5.0;
            mq = new MessageQueueInMemory(100, (long) (timeoutSeconds * 1000));
            AtomicInteger callCount = new AtomicInteger(0);

            AsyncMessageHandler<Object, Object> levelC = payload -> CompletableFuture.completedFuture("C");
            AsyncMessageHandler<Object, Object> levelB = payload -> {
                InvokeQueueMessage message = new InvokeQueueMessage();
                message.setMessageId(UUID.randomUUID().toString());
                message.setPayload("c");
                mq.produceMessage(topic, message);
                return message.getResponse().thenApply(cResult -> "B(" + cResult + ")");
            };
            AsyncMessageHandler<Object, Object> levelA = payload -> {
                InvokeQueueMessage message = new InvokeQueueMessage();
                message.setMessageId(UUID.randomUUID().toString());
                message.setPayload("b");
                mq.produceMessage(topic, message);
                return message.getResponse().thenApply(bResult -> "A(" + bResult + ")");
            };

            AsyncMessageHandler<Object, Object> dispatch = payload -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    return levelA.handle(payload);
                }
                if (count == 2) {
                    return levelB.handle(payload);
                }
                return levelC.handle(payload);
            };

            SubscriptionBase subscription = mq.subscribe(topic);
            subscription.setMessageHandler(dispatch);
            subscription.activate();
            mq.start();

            Thread.sleep(100);

            InvokeQueueMessage rootMessage = new InvokeQueueMessage();
            rootMessage.setMessageId(UUID.randomUUID().toString());
            rootMessage.setPayload("a");
            mq.produceMessage(topic, rootMessage);

            try {
                Object result = rootMessage.getResponse().get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
                assertEquals("A(B(C))", result);
                assertEquals(3, callCount.get());
            } finally {
                mq.stop();
            }
        }

        @Test
        @DisplayName("test nested send task done called once")
        void testNestedSendTaskDoneCalledOnce() throws Exception {
            String topic = "test_task_done";
            double timeoutSeconds = 5.0;
            mq = new MessageQueueInMemory(100, (long) (timeoutSeconds * 1000));
            AtomicInteger callCount = new AtomicInteger(0);

            AsyncMessageHandler<Object, Object> outer = payload -> {
                InvokeQueueMessage innerMessage = new InvokeQueueMessage();
                innerMessage.setMessageId(UUID.randomUUID().toString());
                innerMessage.setPayload("inner");
                mq.produceMessage(topic, innerMessage);
                return innerMessage.getResponse().thenApply(result -> "ok(" + result + ")");
            };

            AsyncMessageHandler<Object, Object> dispatch = payload -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    return outer.handle(payload);
                }
                return CompletableFuture.completedFuture("inner_result");
            };

            SubscriptionBase subscription = mq.subscribe(topic);
            subscription.setMessageHandler(dispatch);
            subscription.activate();
            mq.start();

            Thread.sleep(100);

            InvokeQueueMessage message = new InvokeQueueMessage();
            message.setMessageId(UUID.randomUUID().toString());
            message.setPayload("start");
            mq.produceMessage(topic, message);

            try {
                Object result = message.getResponse().get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
                Thread.sleep(100);
                assertEquals("ok(inner_result)", result);
                assertEquals(2, callCount.get());
            } finally {
                mq.stop();
            }
        }
    }
}
