/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.message_queue;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageSerializer;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Pulsar message queue wrapper.
 *
 * <p>Mirrors Python's {@code MessageQueuePulsar} in
 * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.</p>
 */
public class MessageQueuePulsar extends MessageQueueBase {
    public static final int MAX_PRODUCERS = 10_000;
    public static final String DEFAULT_SUBSCRIPTION_NAME = "default";

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageQueuePulsar.class);

    private final String url;
    private final int maxWorkers;
    private final int maxProducers;
    private final PulsarClientFactory clientFactory;
    private final Object producerLock = new Object();
    private final LinkedHashMap<String, PulsarProducer> producers = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, PulsarSubscription> subscriptions = new LinkedHashMap<>();

    private PulsarClient client;
    private ExecutorService executor;
    private volatile boolean running;

    public MessageQueuePulsar(PulsarConfig pulsarConfig) {
        this(pulsarConfig, new ReflectivePulsarClientFactory(), MAX_PRODUCERS);
    }

    MessageQueuePulsar(PulsarConfig pulsarConfig, PulsarClientFactory clientFactory, int maxProducers) {
        PulsarConfig config = pulsarConfig == null ? new PulsarConfig() : pulsarConfig;
        this.url = config.getUrl();
        this.maxWorkers = config.getMaxWorkers() > 0 ? config.getMaxWorkers() : 8;
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
        this.maxProducers = maxProducers > 0 ? maxProducers : MAX_PRODUCERS;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            client = clientFactory.create(url);
            executor = OpenJiuwenExecutors.newFixedThreadPool("message-queue-pulsar", maxWorkers, false);
            running = true;
            LOGGER.info("[MessageQueuePulsar] started with url={}", url);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to start pulsar message queue", exception);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        LOGGER.info("[MessageQueuePulsar] closing {} subscriptions", subscriptions.size());
        for (String topic : new ArrayList<>(subscriptions.keySet())) {
            unsubscribe(topic);
        }

        LOGGER.info("[MessageQueuePulsar] closing {} producers", producers.size());
        synchronized (producerLock) {
            producers.values().forEach(this::closeQuietly);
            producers.clear();
        }

        closeQuietly(client);
        client = null;
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        LOGGER.info("[MessageQueuePulsar] stopped");
    }

    @Override
    public PulsarSubscription subscribe(String topic) {
        try {
            validateRunning();
            PulsarSubscription existing = subscriptions.get(topic);
            if (existing != null) {
                return existing;
            }
            PulsarConsumer consumer = client.subscribe(topic, DEFAULT_SUBSCRIPTION_NAME, PulsarConsumerType.KEY_SHARED);
            PulsarSubscription subscription = new PulsarSubscription(topic, consumer, executor);
            subscriptions.put(topic, subscription);
            LOGGER.info("[MessageQueuePulsar] Create new subscription, topic={}", topic);
            return subscription;
        } catch (Exception exception) {
            throw ErrorHelper.buildError(
                    StatusCode.MESSAGE_QUEUE_TOPIC_SUBSCRIPTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("topic", topic, "reason", exception));
        }
    }

    @Override
    public void unsubscribe(String topic) {
        PulsarSubscription subscription = subscriptions.remove(topic);
        if (subscription != null) {
            subscription.deactivate();
            LOGGER.info("[MessageQueuePulsar] unsubscribed {}", topic);
        }
    }

    @Override
    public void produceMessage(String topic, QueueMessage message) {
        try {
            validateRunning();
            if (!(message instanceof DmqMessage dmqMessage)) {
                throw new IllegalArgumentException("pulsar message queue requires DmqMessage");
            }
            PulsarProducer producer = getOrCreateProducer(topic);
            byte[] content = MessageSerializer.serializeMessage(dmqMessage);
            LOGGER.info("[MessageQueuePulsar] Sending message to topic={}, message_id={}", topic, message.getMessageId());
            executor.submit(() -> {
                producer.send(content, message.getMessageId());
                return null;
            }).get();
            LOGGER.info("[MessageQueuePulsar] Message sent successfully: topic={}, message_id={}",
                    topic, message.getMessageId());
        } catch (Exception exception) {
            throw ErrorHelper.buildError(
                    StatusCode.MESSAGE_QUEUE_TOPIC_MESSAGE_PRODUCTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("topic", topic, "message", String.valueOf(message), "reason", exception));
        }
    }

    public boolean isRunning() {
        return running;
    }

    int producerCount() {
        synchronized (producerLock) {
            return producers.size();
        }
    }

    private PulsarProducer getOrCreateProducer(String topic) throws Exception {
        synchronized (producerLock) {
            PulsarProducer existing = producers.get(topic);
            if (existing != null) {
                return existing;
            }
            if (producers.size() >= maxProducers) {
                Iterator<Map.Entry<String, PulsarProducer>> iterator = producers.entrySet().iterator();
                if (iterator.hasNext()) {
                    Map.Entry<String, PulsarProducer> eldest = iterator.next();
                    iterator.remove();
                    closeQuietly(eldest.getValue());
                    LOGGER.debug("[MessageQueuePulsar] LRU producer evicted: {}", eldest.getKey());
                }
            }
            LOGGER.info("[MessageQueuePulsar] Creating new producer for topic={}", topic);
            PulsarProducer created = executor.submit(() -> client.createProducer(topic)).get();
            producers.put(topic, created);
            return created;
        }
    }

    private void validateRunning() {
        if (!running) {
            throw new IllegalStateException("pulsar message queue is not running");
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            LOGGER.warn("[MessageQueuePulsar] close failed: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Mirrors Python's {@code pulsar.ConsumerType.KeyShared} in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    public enum PulsarConsumerType {
        KEY_SHARED
    }

    /**
     * Mirrors Python's {@code pulsar.Client} construction boundary in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    public interface PulsarClientFactory {
        PulsarClient create(String url) throws Exception;
    }

    /**
     * Mirrors Python's {@code pulsar.Client} methods used by
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    public interface PulsarClient extends AutoCloseable {
        PulsarConsumer subscribe(String topic, String subscriptionName, PulsarConsumerType consumerType)
                throws Exception;

        PulsarProducer createProducer(String topic) throws Exception;
    }

    /**
     * Mirrors Python's {@code pulsar.Consumer} methods used by
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    public interface PulsarConsumer extends AutoCloseable {
        PulsarMessage receive(long timeoutMillis) throws Exception;

        void acknowledge(PulsarMessage message) throws Exception;
    }

    /**
     * Mirrors Python's {@code pulsar.Producer} methods used by
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    public interface PulsarProducer extends AutoCloseable {
        void send(byte[] content, String partitionKey) throws Exception;
    }

    /**
     * Mirrors Python's received Pulsar message wrapper in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.
     */
    public interface PulsarMessage {
        byte[] data();
    }

    /**
     * Reflection adapter for a runtime-provided Pulsar Java client.
     *
     * <p>Mirrors Python's direct {@code pulsar.Client(...)} dependency in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py} without requiring a compile-time Pulsar
     * dependency in this project.</p>
     */
    private static final class ReflectivePulsarClientFactory implements PulsarClientFactory {
        @Override
        public PulsarClient create(String url) throws Exception {
            Class<?> clientType = Class.forName("org.apache.pulsar.client.api.PulsarClient");
            Object builder = clientType.getMethod("builder").invoke(null);
            Object configured = builder.getClass().getMethod("serviceUrl", String.class).invoke(builder, url);
            Object client = configured.getClass().getMethod("build").invoke(configured);
            return new ReflectivePulsarClient(client);
        }
    }

    /**
     * Reflection wrapper for runtime Pulsar client objects.
     *
     * <p>Mirrors Python's {@code pulsar.Client} in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.</p>
     */
    private static final class ReflectivePulsarClient implements PulsarClient {
        private final Object delegate;

        private ReflectivePulsarClient(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public PulsarConsumer subscribe(String topic, String subscriptionName, PulsarConsumerType consumerType)
                throws Exception {
            Object builder = delegate.getClass().getMethod("newConsumer").invoke(delegate);
            Object withTopic = builder.getClass().getMethod("topic", String.class).invoke(builder, topic);
            Object withSubscription = withTopic.getClass()
                    .getMethod("subscriptionName", String.class)
                    .invoke(withTopic, subscriptionName);
            Class<?> subscriptionType = Class.forName("org.apache.pulsar.client.api.SubscriptionType");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object keyShared = Enum.valueOf((Class<Enum>) subscriptionType.asSubclass(Enum.class), "Key_Shared");
            Object withType = withSubscription.getClass()
                    .getMethod("subscriptionType", subscriptionType)
                    .invoke(withSubscription, keyShared);
            Object consumer = withType.getClass().getMethod("subscribe").invoke(withType);
            return new ReflectivePulsarConsumer(consumer);
        }

        @Override
        public PulsarProducer createProducer(String topic) throws Exception {
            Object builder = delegate.getClass().getMethod("newProducer").invoke(delegate);
            Object withTopic = builder.getClass().getMethod("topic", String.class).invoke(builder, topic);
            Object producer = withTopic.getClass().getMethod("create").invoke(withTopic);
            return new ReflectivePulsarProducer(producer);
        }

        @Override
        public void close() throws Exception {
            delegate.getClass().getMethod("close").invoke(delegate);
        }
    }

    /**
     * Reflection wrapper for runtime Pulsar consumer objects.
     *
     * <p>Mirrors Python's {@code pulsar.Consumer} in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.</p>
     */
    private static final class ReflectivePulsarConsumer implements PulsarConsumer {
        private final Object delegate;

        private ReflectivePulsarConsumer(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public PulsarMessage receive(long timeoutMillis) throws Exception {
            try {
                Object message = delegate.getClass()
                        .getMethod("receive", int.class, TimeUnit.class)
                        .invoke(delegate, Math.toIntExact(timeoutMillis), TimeUnit.MILLISECONDS);
                return new ReflectivePulsarMessage(message);
            } catch (InvocationTargetException invocation) {
                Throwable cause = invocation.getCause();
                if (cause != null && cause.getClass().getSimpleName().contains("Timeout")) {
                    throw new TimeoutException(cause.getMessage());
                }
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw invocation;
            }
        }

        @Override
        public void acknowledge(PulsarMessage message) throws Exception {
            Object rawMessage = message instanceof ReflectivePulsarMessage reflective ? reflective.delegate : message;
            delegate.getClass().getMethod("acknowledge", rawMessage.getClass()).invoke(delegate, rawMessage);
        }

        @Override
        public void close() throws Exception {
            delegate.getClass().getMethod("close").invoke(delegate);
        }
    }

    /**
     * Reflection wrapper for runtime Pulsar producer objects.
     *
     * <p>Mirrors Python's {@code pulsar.Producer} in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.</p>
     */
    private static final class ReflectivePulsarProducer implements PulsarProducer {
        private final Object delegate;

        private ReflectivePulsarProducer(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public void send(byte[] content, String partitionKey) throws Exception {
            try {
                Object messageBuilder = delegate.getClass().getMethod("newMessage").invoke(delegate);
                Object withKey = messageBuilder.getClass().getMethod("key", String.class).invoke(messageBuilder,
                        partitionKey);
                Object withValue = withKey.getClass().getMethod("value", byte[].class).invoke(withKey, content);
                withValue.getClass().getMethod("send").invoke(withValue);
            } catch (NoSuchMethodException missingTypedMessageBuilder) {
                delegate.getClass().getMethod("send", byte[].class).invoke(delegate, content);
            }
        }

        @Override
        public void close() throws Exception {
            delegate.getClass().getMethod("close").invoke(delegate);
        }
    }

    /**
     * Reflection wrapper for runtime Pulsar message objects.
     *
     * <p>Mirrors Python's {@code msg.data()} in
     * {@code openjiuwen/extensions/message_queue/message_queue_pulsar.py}.</p>
     */
    private static final class ReflectivePulsarMessage implements PulsarMessage {
        private final Object delegate;

        private ReflectivePulsarMessage(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public byte[] data() {
            try {
                return (byte[]) delegate.getClass().getMethod("getData").invoke(delegate);
            } catch (Exception exception) {
                throw new IllegalStateException("failed to read pulsar message data", exception);
            }
        }
    }
}
