/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.message_queue;

import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageSerializer;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.mq.AsyncMessageHandler;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulsar-backed message queue extension for distributed runner traffic.
 * 
 * @since 0.1.7
 */
public class MessageQueuePulsar extends MessageQueueBase {
    static final String DEFAULT_SUBSCRIPTION_NAME = "default";

    private final PulsarConfig config;
    private final PulsarRuntime runtime;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, PulsarSubscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, PulsarProducer> producers = new ConcurrentHashMap<>();

    /**
     * AtomicBoolean.
     * 
     * @since 0.1.7
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * MessageQueuePulsar.
     * 
     * @param config config
     * @since 0.1.7
     */
    public MessageQueuePulsar(PulsarConfig config) {
        this(config, new LivePulsarRuntime(config));
    }

    MessageQueuePulsar(PulsarConfig config, PulsarRuntime runtime) {
        this.config = config != null ? config : PulsarConfig.builder().url("").build();
        this.runtime = Objects.requireNonNull(runtime);
    }

    /**
     * start.
     * 
     * @since 0.1.7
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            runtime.start();
        }
    }

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            subscriptions.values().forEach(PulsarSubscription::deactivate);
            subscriptions.clear();
            producers.values().forEach(PulsarProducer::close);
            producers.clear();
            runtime.close();
        }
    }

    /**
     * subscribe.
     * 
     * @param topic topic
     * @return the result
     * @since 0.1.7
     */
    @Override
    public SubscriptionBase subscribe(String topic) {
        ensureStarted();
        return subscriptions.computeIfAbsent(topic, key -> new PulsarSubscription(topic, runtime.newConsumer(topic)));
    }

    /**
     * unsubscribe.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    @Override
    public void unsubscribe(String topic) {
        PulsarSubscription subscription = subscriptions.remove(topic);
        if (subscription != null) {
            subscription.deactivate();
        }
    }

    /**
     * produceMessage.
     * 
     * @param topic topic
     * @param message message
     * @since 0.1.7
     */
    @Override
    public void produceMessage(String topic, QueueMessage message) {
        ensureStarted();
        if (!(message instanceof DmqMessage dmqMessage)) {
            throw new IllegalArgumentException("MessageQueuePulsar only supports DmqMessage payloads");
        }
        try {
            byte[] payload = MessageSerializer.serializeMessage(dmqMessage);
            PulsarProducer producer = producers.computeIfAbsent(topic, key -> runtime.newProducer(topic));
            producer.send(message.getMessageId(), payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to produce Pulsar message", e);
        }
    }

    /**
     * ensureStarted.
     * 
     * @since 0.1.7
     */
    private void ensureStarted() {
        if (!running.get()) {
            start();
        }
    }

    interface PulsarRuntime {
        /**
         * start.
         * 
         * @since 0.1.7
         */
        void start();

        /**
         * newProducer.
         * 
         * @param topic topic
         * @return the result
         * @since 0.1.7
         */
        PulsarProducer newProducer(String topic);

        /**
         * newConsumer.
         * 
         * @param topic topic
         * @return the result
         * @since 0.1.7
         */
        PulsarConsumer newConsumer(String topic);

        /**
         * close.
         * 
         * @since 0.1.7
         */
        void close();
    }

    interface PulsarProducer {
        /**
         * send.
         * 
         * @param key key
         * @param payload payload
         * @throws Exception Exception
         * @since 0.1.7
         */
        void send(String key, byte[] payload) throws Exception;

        /**
         * close.
         * 
         * @since 0.1.7
         */
        void close();
    }

    interface PulsarConsumer {
        /**
         * receive.
         * 
         * @param timeoutMillis timeoutMillis
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        ReceivedMessage receive(long timeoutMillis) throws Exception;

        /**
         * acknowledge.
         * 
         * @param message message
         * @throws Exception Exception
         * @since 0.1.7
         */
        void acknowledge(ReceivedMessage message) throws Exception;

        /**
         * close.
         * 
         * @since 0.1.7
         */
        void close();
    }

    /**
     * ReceivedMessage.
     * 
     * @param payload payload
     * @param handle handle
     * @since 0.1.7
     */
    record ReceivedMessage(byte[] payload, Object handle) {
    }

    static final class PulsarSubscription extends SubscriptionBase {
        private final String topic;
        private final PulsarConsumer consumer;

        /**
         * ThreadPoolExecutor.
         * 
         * @param LinkedBlockingQueue<>( LinkedBlockingQueue<>(
         * @since 0.1.7
         */
        private final ExecutorService executor =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        /**
         * AtomicBoolean.
         * 
         * @since 0.1.7
         */
        private final AtomicBoolean active = new AtomicBoolean(false);
        private AsyncMessageHandler<Object, Object> handler;

        PulsarSubscription(String topic, PulsarConsumer consumer) {
            this.topic = topic;
            this.consumer = consumer;
        }

        /**
         * setMessageHandler.
         * 
         * @param handler handler
         * @since 0.1.7
         */
        @Override
        public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
            this.handler = handler;
        }

        /**
         * activate.
         * 
         * @since 0.1.7
         */
        @Override
        public void activate() {
            if (active.compareAndSet(false, true)) {
                executor.submit(this::consumeLoop);
            }
        }

        /**
         * deactivate.
         * 
         * @since 0.1.7
         */
        @Override
        public void deactivate() {
            if (active.compareAndSet(true, false)) {
                executor.shutdownNow();
                consumer.close();
            }
        }

        /**
         * isActive.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean isActive() {
            return active.get();
        }

        /**
         * consumeLoop.
         * 
         * @since 0.1.7
         */
        private void consumeLoop() {
            while (active.get()) {
                try {
                    ReceivedMessage received = consumer.receive(1000);
                    if (received == null) {
                        continue;
                    }
                    Object decoded = MessageSerializer.deserializeMessage(received.payload());
                    if (handler != null) {
                        CompletableFuture<Object> future = handler.handle(decoded);
                        future.join();
                    }
                    consumer.acknowledge(received);
                } catch (Exception e) {
                    if (active.get()) {
                        throw new IllegalStateException("Failed to consume Pulsar message for topic " + topic, e);
                    }
                }
            }
        }
    }

    static final class LivePulsarRuntime implements PulsarRuntime {
        private final PulsarConfig config;
        private PulsarClient client;

        LivePulsarRuntime(PulsarConfig config) {
            this.config = config != null ? config : PulsarConfig.builder().url("").build();
        }

        /**
         * start.
         * 
         * @since 0.1.7
         */
        @Override
        public void start() {
            if (client != null) {
                return;
            }
            try {
                client = PulsarClient.builder().serviceUrl(config.getUrl()).build();
            } catch (PulsarClientException e) {
                throw new IllegalStateException("Failed to start Pulsar client", e);
            }
        }

        /**
         * newProducer.
         * 
         * @param topic topic
         * @return the result
         * @since 0.1.7
         */
        @Override
        public PulsarProducer newProducer(String topic) {
            try {
                Producer<byte[]> producer = client.newProducer().topic(topic).create();
                return new PulsarProducer() {
                    @Override
                    public void send(String key, byte[] payload) throws Exception {
                        producer.newMessage().key(key != null ? key : "").value(payload).send();
                    }

                    @Override
                    public void close() {
                        try {
                            producer.close();
                        } catch (PulsarClientException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                };
            } catch (PulsarClientException e) {
                throw new IllegalStateException("Failed to create Pulsar producer", e);
            }
        }

        /**
         * newConsumer.
         * 
         * @param topic topic
         * @return the result
         * @since 0.1.7
         */
        @Override
        public PulsarConsumer newConsumer(String topic) {
            try {
                Consumer<byte[]> consumer =
                    client.newConsumer().topic(topic).subscriptionName(DEFAULT_SUBSCRIPTION_NAME)
                            .subscriptionType(SubscriptionType.Key_Shared).subscribe();
                return new PulsarConsumer() {
                    @Override
                    public ReceivedMessage receive(long timeoutMillis) throws Exception {
                        Message<byte[]> message =
                            consumer.receive((int) timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                        return message != null ? new ReceivedMessage(message.getValue(), message) : null;
                    }

                    @Override
                    public void acknowledge(ReceivedMessage message) throws Exception {
                        @SuppressWarnings("unchecked")
                        Message<byte[]> typed = (Message<byte[]>) message.handle();
                        consumer.acknowledge(typed);
                    }

                    @Override
                    public void close() {
                        try {
                            consumer.close();
                        } catch (PulsarClientException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                };
            } catch (PulsarClientException e) {
                throw new IllegalStateException("Failed to create Pulsar consumer", e);
            }
        }

        /**
         * close.
         * 
         * @since 0.1.7
         */
        @Override
        public void close() {
            if (client != null) {
                try {
                    client.close();
                } catch (PulsarClientException e) {
                    throw new IllegalStateException("Failed to close Pulsar client", e);
                } finally {
                    client = null;
                }
            }
        }
    }
}
