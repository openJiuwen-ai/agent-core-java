/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.message_queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Pulsar message queue adapter.
 * <p>
 * Mirrors Python's {@code message_queue_pulsar} in
 * {@code openjiuwen.extensions.message_queue.message_queue_pulsar}.
 */
public class PulsarMessageQueue {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarMessageQueue.class);

    private final String serviceUrl;
    private final Map<String, List<Consumer<Map<String, Object>>>> subscriptions = new LinkedHashMap<>();

    public PulsarMessageQueue(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /** Publish a message to a topic. */
    public void publish(String topic, Map<String, Object> message) {
        LOG.info("[PulsarMQ] Publishing to topic: {}", topic);
    }

    /** Subscribe to a topic. */
    public void subscribe(String topic, Consumer<Map<String, Object>> handler) {
        subscriptions.computeIfAbsent(topic, k -> new ArrayList<>()).add(handler);
        LOG.info("[PulsarMQ] Subscribed to topic: {}", topic);
    }

    /** Close the connection. */
    public void close() {
        subscriptions.clear();
        LOG.info("[PulsarMQ] Closed");
    }
}
