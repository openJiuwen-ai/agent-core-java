/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.mq.AsyncMessageHandler;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for reply-topic response routing.
 *
 * <p>Mirrors Python's tests/unit_tests/core/runner/dunner/test_reply_topic_subscription.py.</p>
 */
class ReplyTopicSubscriptionTest {

    @Test
    @DisplayName("collector keys normalize empty request IDs to null")
    void testMakeKeyNormalizesEmptyRequestId() {
        ReplyTopicSubscription.CollectorKey key = ReplyTopicSubscription.makeKey("agent-1", "message-1", "");

        assertEquals("agent-1", key.remoteId());
        assertEquals("message-1", key.messageId());
        assertNull(key.requestId());
    }

    @Test
    @DisplayName("activate subscribes to the configured reply topic")
    void testActivateSubscribesToConfiguredTopic() {
        FakeMessageQueue queue = new FakeMessageQueue();
        ReplyTopicSubscription subscription = new ReplyTopicSubscription(queue, "reply-topic");

        assertNull(subscription.isActive());
        subscription.activate();

        assertEquals("reply-topic", queue.subscribedTopics.getFirst());
        assertTrue(queue.lastSubscription.isActive());
        assertEquals(Boolean.TRUE, subscription.isActive());
    }

    @Test
    @DisplayName("normal message reception routes payload to the matching collector")
    void testNormalMessageReception() throws Exception {
        FakeMessageQueue queue = new FakeMessageQueue();
        ReplyTopicSubscription subscription = new ReplyTopicSubscription(queue, "reply-topic");
        subscription.activate();

        String messageId = "test_msg_123";
        String remoteId = "agent_456";
        ResponseCollector collector = subscription.registerCollector(messageId, remoteId);

        DmqResponseMessage message = new DmqResponseMessage();
        message.setType(DMessageType.OUTPUT);
        message.setSenderId(remoteId);
        message.setMessageId(messageId);
        message.setBody("test_payload");
        message.setLastChunk(true);

        subscription.onMessage(message);

        assertEquals("test_payload", collector.result(1.0).get(1, TimeUnit.SECONDS));
        subscription.unregisterCollector(messageId, remoteId, null);
        assertEquals(0, subscription.collectorCount());
    }

    @Test
    @DisplayName("unregistered messages are discarded without throwing")
    void testUnregisteredMessageHandling() {
        FakeMessageQueue queue = new FakeMessageQueue();
        ReplyTopicSubscription subscription = new ReplyTopicSubscription(queue, "reply-topic");
        subscription.activate();

        DmqResponseMessage message = new DmqResponseMessage();
        message.setType(DMessageType.OUTPUT);
        message.setSenderId("unknown_agent");
        message.setMessageId("unknown_msg");
        message.setBody("unregistered");

        assertDoesNotThrow(() -> subscription.onMessage(message));
        assertEquals(0, subscription.collectorCount());
    }

    @Test
    @DisplayName("deactivate unsubscribes and clears collectors")
    void testDeactivateUnsubscribesAndClearsCollectors() {
        FakeMessageQueue queue = new FakeMessageQueue();
        ReplyTopicSubscription subscription = new ReplyTopicSubscription(queue, "reply-topic");
        subscription.activate();
        subscription.registerCollector("message-1", "agent-1");

        subscription.deactivate();

        assertEquals(List.of("reply-topic"), queue.unsubscribedTopics);
        assertFalse(Boolean.TRUE.equals(subscription.isActive()));
        assertEquals(0, subscription.collectorCount());
    }

    private static final class FakeMessageQueue extends MessageQueueBase {
        private final List<String> subscribedTopics = new ArrayList<>();
        private final List<String> unsubscribedTopics = new ArrayList<>();
        private FakeSubscription lastSubscription;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public SubscriptionBase subscribe(String topic) {
            subscribedTopics.add(topic);
            lastSubscription = new FakeSubscription();
            return lastSubscription;
        }

        @Override
        public void unsubscribe(String topic) {
            unsubscribedTopics.add(topic);
        }

        @Override
        public void produceMessage(String topic, QueueMessage message) {
            if (lastSubscription != null) {
                lastSubscription.handle(message.getPayload());
            }
        }
    }

    private static final class FakeSubscription extends SubscriptionBase {
        private AsyncMessageHandler<Object, Object> handler;
        private boolean active;

        @Override
        public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
            this.handler = handler;
        }

        @Override
        public void activate() {
            active = true;
        }

        @Override
        public void deactivate() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        private CompletableFuture<Object> handle(Object message) {
            if (handler == null) {
                return CompletableFuture.completedFuture(null);
            }
            return handler.handle(message);
        }
    }
}
