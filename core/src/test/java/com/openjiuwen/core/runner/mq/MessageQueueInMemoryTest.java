/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the in-memory message queue implementation.
 *
 * <p>Mirrors Python's {@code MessageQueueInMemory} and {@code SubscriptionInMemory} in
 * {@code openjiuwen/core/runner/message_queue_inmemory.py}.</p>
 *
 * <p>Mirrors Python's tests in
 * {@code tests/unit_tests/core/runner/test_message_queue.py}.</p>
 */
class MessageQueueInMemoryTest {

    @Test
    void routesInvokeAndStreamMessages() throws Exception {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        queue.start();
        try {
            SubscriptionInMemory streamSubscription = queue.subscribe("topic_stream");
            streamSubscription.setMessageHandler(payload -> CompletableFutureFactory.completedIterator(
                    "stream response for " + payload,
                    "stream done"));
            streamSubscription.activate();

            SubscriptionInMemory invokeSubscription = queue.subscribe("topic_invoke");
            invokeSubscription.setMessageHandler(payload ->
                    CompletableFutureFactory.completedValue("invoke response for " + payload));
            invokeSubscription.activate();

            StreamQueueMessage streamMessage = new StreamQueueMessage("stream-1", "payload-a");
            queue.produceMessage("topic_stream", streamMessage);
            assertThat(streamMessage.getResponse().get(1, TimeUnit.SECONDS))
                    .toIterable()
                    .containsExactly("stream response for payload-a", "stream done");
            assertThat(streamMessage.getErrorCode()).isEqualTo(StatusCode.SUCCESS.getCode());
            assertThat(streamMessage.getErrorMsg()).isEmpty();

            InvokeQueueMessage invokeMessage = new InvokeQueueMessage("invoke-1", "payload-b");
            queue.produceMessage("topic_invoke", invokeMessage);
            assertThat(invokeMessage.getResponse().get(1, TimeUnit.SECONDS))
                    .isEqualTo("invoke response for payload-b");
            assertThat(invokeMessage.getErrorCode()).isEqualTo(StatusCode.SUCCESS.getCode());
            assertThat(invokeMessage.getErrorMsg()).isEmpty();
        } finally {
            queue.stop();
        }
    }

    @Test
    void recordsWrongResponseTypeAsConsumeError() {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        queue.start();
        try {
            SubscriptionInMemory streamSubscription = queue.subscribe("topic_stream");
            streamSubscription.setMessageHandler(payload -> CompletableFutureFactory.completedIterator("item"));
            streamSubscription.activate();

            InvokeQueueMessage message = new InvokeQueueMessage("invoke-1", "payload");
            queue.produceMessage("topic_stream", message);

            assertThatThrownBy(() -> message.getResponse().get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(message.getErrorCode())
                    .isEqualTo(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode());
        } finally {
            queue.stop();
        }
    }

    @Test
    void invokeRejectsPythonFalsyResponse() {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        queue.start();
        try {
            SubscriptionInMemory subscription = queue.subscribe("topic");
            subscription.setMessageHandler(payload -> CompletableFutureFactory.completedValue(""));
            subscription.activate();

            InvokeQueueMessage message = new InvokeQueueMessage("invoke-empty", "payload");
            queue.produceMessage("topic", message);

            assertThatThrownBy(() -> message.getResponse().get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("response is empty");
            assertThat(message.getErrorCode())
                    .isEqualTo(StatusCode.MESSAGE_QUEUE_MESSAGE_CONSUME_ERROR.getCode());
        } finally {
            queue.stop();
        }
    }

    @Test
    void nestedSendDoesNotDeadlockSubscriptionConsumer() throws Exception {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        AtomicInteger callCount = new AtomicInteger();
        queue.start();
        try {
            SubscriptionInMemory subscription = queue.subscribe("nested");
            subscription.setMessageHandler(payload -> {
                int currentCall = callCount.incrementAndGet();
                if (currentCall == 1) {
                    InvokeQueueMessage innerMessage = new InvokeQueueMessage("inner", "inner-payload");
                    queue.produceMessage("nested", innerMessage);
                    return innerMessage.getResponse().thenApply(result -> "outer(" + result + ")");
                }
                return CompletableFutureFactory.completedValue("inner_done");
            });
            subscription.activate();

            InvokeQueueMessage outerMessage = new InvokeQueueMessage("outer", "outer-payload");
            queue.produceMessage("nested", outerMessage);

            assertThat(outerMessage.getResponse().get(1, TimeUnit.SECONDS))
                    .isEqualTo("outer(inner_done)");
            assertThat(callCount).hasValue(2);
        } finally {
            queue.stop();
        }
    }

    @Test
    void nestedSendSupportsThreeLevelChain() throws Exception {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        AtomicInteger callCount = new AtomicInteger();
        queue.start();
        try {
            SubscriptionInMemory subscription = queue.subscribe("nested-three");
            subscription.setMessageHandler(payload -> {
                int currentCall = callCount.incrementAndGet();
                if (currentCall == 1) {
                    InvokeQueueMessage levelBMessage = new InvokeQueueMessage("level-b", "b");
                    queue.produceMessage("nested-three", levelBMessage);
                    return levelBMessage.getResponse().thenApply(result -> "A(" + result + ")");
                }
                if (currentCall == 2) {
                    InvokeQueueMessage levelCMessage = new InvokeQueueMessage("level-c", "c");
                    queue.produceMessage("nested-three", levelCMessage);
                    return levelCMessage.getResponse().thenApply(result -> "B(" + result + ")");
                }
                return CompletableFutureFactory.completedValue("C");
            });
            subscription.activate();

            InvokeQueueMessage rootMessage = new InvokeQueueMessage("level-a", "a");
            queue.produceMessage("nested-three", rootMessage);

            assertThat(rootMessage.getResponse().get(1, TimeUnit.SECONDS)).isEqualTo("A(B(C))");
            assertThat(callCount).hasValue(3);
        } finally {
            queue.stop();
        }
    }

    @Test
    void nestedSendLeavesSubscriptionUsableAfterNestedCompletion() throws Exception {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        AtomicInteger callCount = new AtomicInteger();
        queue.start();
        try {
            SubscriptionInMemory subscription = queue.subscribe("nested-task-done");
            subscription.setMessageHandler(payload -> {
                int currentCall = callCount.incrementAndGet();
                if (currentCall == 1) {
                    InvokeQueueMessage innerMessage = new InvokeQueueMessage("inner", "inner");
                    queue.produceMessage("nested-task-done", innerMessage);
                    return innerMessage.getResponse().thenApply(result -> "ok(" + result + ")");
                }
                if ("after".equals(payload)) {
                    return CompletableFutureFactory.completedValue("after_done");
                }
                return CompletableFutureFactory.completedValue("inner_result");
            });
            subscription.activate();

            InvokeQueueMessage rootMessage = new InvokeQueueMessage("start", "start");
            queue.produceMessage("nested-task-done", rootMessage);
            assertThat(rootMessage.getResponse().get(1, TimeUnit.SECONDS)).isEqualTo("ok(inner_result)");

            InvokeQueueMessage afterMessage = new InvokeQueueMessage("after", "after");
            queue.produceMessage("nested-task-done", afterMessage);
            assertThat(afterMessage.getResponse().get(1, TimeUnit.SECONDS)).isEqualTo("after_done");
            assertThat(callCount).hasValue(3);
        } finally {
            queue.stop();
        }
    }

    @Test
    void subscriptionAssignsMissingMessageIdAndDuplicateTopicFails() throws Exception {
        MessageQueueInMemory queue = new MessageQueueInMemory(100, Duration.ofSeconds(5));
        queue.start();
        try {
            SubscriptionInMemory subscription = queue.subscribe("topic");
            subscription.setMessageHandler(CompletableFutureFactory::completedValue);
            subscription.activate();

            assertThatThrownBy(() -> queue.subscribe("topic"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Topic 'topic' is already subscribed.");

            InvokeQueueMessage message = new InvokeQueueMessage("", "payload");
            queue.produceMessage("topic", message);
            assertThat(message.getResponse().get(1, TimeUnit.SECONDS)).isEqualTo("payload");
            assertThat(message.getMessageId()).isNotEmpty();
        } finally {
            queue.stop();
        }
    }

    private static final class CompletableFutureFactory {

        private CompletableFutureFactory() {
        }

        static java.util.concurrent.CompletableFuture<Object> completedValue(Object value) {
            return java.util.concurrent.CompletableFuture.completedFuture(value);
        }

        static java.util.concurrent.CompletableFuture<Object> completedIterator(Object... values) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of(values).iterator());
        }
    }
}
