/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's fake message queue behavior in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_queue_fake.py}.
 */
class FakeMessageQueueTest {

    @Test
    void subscribeRequiresRunningQueue() {
        FakeMessageQueue queue = new FakeMessageQueue();

        assertThatThrownBy(() -> queue.subscribe("topic-a"))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MESSAGE_QUEUE_TOPIC_SUBSCRIPTION_ERROR);
    }

    @Test
    void activeSubscriptionReceivesSerializedMessage() throws Exception {
        FakeMessageQueue queue = new FakeMessageQueue();
        ArrayBlockingQueue<Object> received = new ArrayBlockingQueue<>(1);
        queue.start();
        SubscriptionBase subscription = queue.subscribe("topic-a");
        subscription.setMessageHandler(message -> {
            received.offer(message);
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();

        DmqResponseMessage response = new DmqResponseMessage();
        response.setMessageId("message-1");
        response.setPayload("payload-1");
        response.setSenderId("sender-1");
        response.setReceiverId("receiver-1");

        queue.produceMessage("topic-a", response);

        Object delivered = received.poll(2, TimeUnit.SECONDS);
        assertThat(delivered).isInstanceOf(DmqResponseMessage.class);
        DmqResponseMessage restored = (DmqResponseMessage) delivered;
        assertThat(restored.getMessageId()).isEqualTo("message-1");
        assertThat(restored.getBody()).isEqualTo("payload-1");
    }

    @Test
    void unsubscribeDeactivatesTopicSubscriptions() throws Exception {
        FakeMessageQueue queue = new FakeMessageQueue();
        ArrayBlockingQueue<Object> received = new ArrayBlockingQueue<>(1);
        queue.start();
        SubscriptionBase subscription = queue.subscribe("topic-a");
        subscription.setMessageHandler(message -> {
            received.offer(message);
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();

        queue.unsubscribe("topic-a");
        queue.produceMessage("topic-a", new DmqResponseMessage());

        assertThat(subscription.isActive()).isFalse();
        assertThat(queue.subscriptionCount("topic-a")).isZero();
        assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }
}
