/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.message_queue;

import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageSerializer;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.QueueMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code MessageQueuePulsar} and {@code PulsarSubscription} in
 * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
 */
class MessageQueuePulsarTest {
    @Test
    void startSubscribeProduceAndStopUsePulsarClientPorts() {
        FakeClientFactory factory = new FakeClientFactory();
        MessageQueuePulsar queue = new MessageQueuePulsar(config(), factory, 10);

        queue.start();
        queue.subscribe("topic-a");
        DmqRequestMessage message = request("message-1", "hello");
        queue.produceMessage("topic-a", message);
        queue.stop();

        assertFalse(queue.isRunning());
        assertEquals(1, factory.createdClients.size());
        FakeClient client = factory.createdClients.get(0);
        assertEquals("pulsar://localhost:6650", client.url);
        assertEquals("topic-a", client.subscriptions.get(0).topic);
        assertEquals(MessageQueuePulsar.DEFAULT_SUBSCRIPTION_NAME, client.subscriptions.get(0).subscriptionName);
        assertEquals(MessageQueuePulsar.PulsarConsumerType.KEY_SHARED, client.subscriptions.get(0).consumerType);
        FakeProducer producer = client.producers.get(0);
        assertEquals("message-1", producer.sent.get(0).partitionKey());
        DmqMessage restored = MessageSerializer.deserializeMessage(producer.sent.get(0).content());
        assertEquals("message-1", restored.getMessageId());
        assertEquals("hello", ((DmqRequestMessage) restored).getBody());
        assertTrue(client.closed);
        assertTrue(producer.closed);
        assertFalse(client.subscriptions.get(0).consumer.closed);
    }

    @Test
    void subscribeReturnsExistingSubscriptionForSameTopic() {
        FakeClientFactory factory = new FakeClientFactory();
        MessageQueuePulsar queue = new MessageQueuePulsar(config(), factory, 10);
        queue.start();
        try {
            PulsarSubscription first = queue.subscribe("topic-a");
            PulsarSubscription second = queue.subscribe("topic-a");

            assertSame(first, second);
            assertEquals(1, factory.createdClients.get(0).subscriptions.size());
        } finally {
            queue.stop();
        }
    }

    @Test
    void producerCacheEvictsLeastRecentlyUsedProducer() {
        FakeClientFactory factory = new FakeClientFactory();
        MessageQueuePulsar queue = new MessageQueuePulsar(config(), factory, 1);
        queue.start();
        try {
            queue.produceMessage("topic-a", request("message-a", "a"));
            FakeProducer evicted = factory.createdClients.get(0).producers.get(0);
            queue.produceMessage("topic-b", request("message-b", "b"));

            assertTrue(evicted.closed);
            assertEquals(1, queue.producerCount());
            assertEquals(2, factory.createdClients.get(0).producers.size());
        } finally {
            queue.stop();
        }
    }

    @Test
    void subscriptionConsumesDeserializesAndAcknowledgesMessages() throws Exception {
        FakeConsumer consumer = new FakeConsumer();
        DmqRequestMessage source = request("message-2", "payload");
        consumer.messages.add(new FakeMessage(MessageSerializer.serializeMessage(source)));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        PulsarSubscription subscription = new PulsarSubscription("topic-a", consumer, executor);
        CountDownLatch handled = new CountDownLatch(1);
        List<Object> payloads = new ArrayList<>();
        subscription.setMessageHandler(payload -> {
            payloads.add(payload);
            handled.countDown();
            return CompletableFuture.completedFuture(null);
        });

        subscription.activate();
        assertTrue(handled.await(2, TimeUnit.SECONDS));
        subscription.deactivate();
        executor.shutdownNow();

        assertFalse(subscription.isActive());
        assertEquals(1, consumer.acknowledged.size());
        DmqRequestMessage restored = assertInstanceOf(DmqRequestMessage.class, payloads.get(0));
        assertEquals("message-2", restored.getMessageId());
        assertEquals("payload", restored.getBody());
    }

    @Test
    void produceMessageRequiresRunningQueue() {
        MessageQueuePulsar queue = new MessageQueuePulsar(config(), new FakeClientFactory(), 10);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> queue.produceMessage("topic", request("message", "payload")));

        assertTrue(thrown.getMessage().contains("produce message error"));
    }

    @Test
    void produceMessageRequiresDistributedQueueMessage() {
        MessageQueuePulsar queue = new MessageQueuePulsar(config(), new FakeClientFactory(), 10);
        queue.start();
        try {
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> queue.produceMessage("topic", new QueueMessage("plain", "payload")));

            assertTrue(thrown.getMessage().contains("produce message error"));
        } finally {
            queue.stop();
        }
    }

    private static PulsarConfig config() {
        return PulsarConfig.builder()
                .url("pulsar://localhost:6650")
                .maxWorkers(2)
                .build();
    }

    private static DmqRequestMessage request(String messageId, Object body) {
        DmqRequestMessage message = new DmqRequestMessage();
        message.setMessageId(messageId);
        message.setType(DMessageType.INPUT);
        message.setPayload(body);
        return message;
    }

    /**
     * Mirrors Python's injected {@code pulsar.Client} constructor boundary in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private static final class FakeClientFactory implements MessageQueuePulsar.PulsarClientFactory {
        private final List<FakeClient> createdClients = new ArrayList<>();

        @Override
        public MessageQueuePulsar.PulsarClient create(String url) {
            FakeClient client = new FakeClient(url);
            createdClients.add(client);
            return client;
        }
    }

    /**
     * Mirrors Python's {@code pulsar.Client} collaborator in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private static final class FakeClient implements MessageQueuePulsar.PulsarClient {
        private final String url;
        private final List<SubscriptionRecord> subscriptions = new ArrayList<>();
        private final List<FakeProducer> producers = new ArrayList<>();
        private boolean closed;

        private FakeClient(String url) {
            this.url = url;
        }

        @Override
        public MessageQueuePulsar.PulsarConsumer subscribe(String topic, String subscriptionName,
                                                           MessageQueuePulsar.PulsarConsumerType consumerType) {
            FakeConsumer consumer = new FakeConsumer();
            subscriptions.add(new SubscriptionRecord(topic, subscriptionName, consumerType, consumer));
            return consumer;
        }

        @Override
        public MessageQueuePulsar.PulsarProducer createProducer(String topic) {
            FakeProducer producer = new FakeProducer(topic);
            producers.add(producer);
            return producer;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Mirrors Python's stored subscription metadata in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private record SubscriptionRecord(String topic,
                                      String subscriptionName,
                                      MessageQueuePulsar.PulsarConsumerType consumerType,
                                      FakeConsumer consumer) {
    }

    /**
     * Mirrors Python's {@code pulsar.Consumer} collaborator in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private static final class FakeConsumer implements MessageQueuePulsar.PulsarConsumer {
        private final BlockingQueue<FakeMessage> messages = new LinkedBlockingQueue<>();
        private final List<MessageQueuePulsar.PulsarMessage> acknowledged = new ArrayList<>();
        private boolean closed;

        @Override
        public MessageQueuePulsar.PulsarMessage receive(long timeoutMillis) throws Exception {
            FakeMessage message = messages.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new TimeoutException("timeout");
            }
            return message;
        }

        @Override
        public void acknowledge(MessageQueuePulsar.PulsarMessage message) {
            acknowledged.add(message);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Mirrors Python's {@code pulsar.Producer} collaborator in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private static final class FakeProducer implements MessageQueuePulsar.PulsarProducer {
        private final String topic;
        private final List<SentMessage> sent = new ArrayList<>();
        private boolean closed;

        private FakeProducer(String topic) {
            this.topic = topic;
        }

        @Override
        public void send(byte[] content, String partitionKey) {
            sent.add(new SentMessage(topic, content, partitionKey));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Mirrors Python's sent Pulsar payload in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private record SentMessage(String topic, byte[] content, String partitionKey) {
    }

    /**
     * Mirrors Python's received Pulsar payload in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    private record FakeMessage(byte[] data) implements MessageQueuePulsar.PulsarMessage {
    }
}
