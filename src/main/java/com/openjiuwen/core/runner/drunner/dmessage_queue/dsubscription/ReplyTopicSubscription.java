/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens on a reply topic and dispatches responses to collectors.
 */
public class ReplyTopicSubscription {

    private final MessageQueueBase mq;
    private final String topic;
    private final Map<CollectorKey, ResponseCollector> collectors = new ConcurrentHashMap<>();

    private volatile boolean active;
    private SubscriptionBase subscription;

    public ReplyTopicSubscription(MessageQueueBase mq, String topic) {
        this.mq = mq;
        this.topic = topic;
    }

    public void activate() {
        subscription = mq.subscribe(topic);
        subscription.setMessageHandler(message -> {
            if (message instanceof DmqResponseMessage responseMessage) {
                onMessage(responseMessage);
            }
            return null;
        });
        subscription.activate();
        active = true;
    }

    public void deactivate() {
        active = false;
        if (subscription != null) {
            subscription.deactivate();
            mq.unsubscribe(topic);
            subscription = null;
        }
        collectors.values().forEach(ResponseCollector::close);
        collectors.clear();
    }

    public ResponseCollector registerCollector(String messageId, String remoteId, String requestId, Double ttlSeconds) {
        if (!active) {
            throw new IllegalStateException("ReplyTopicSubscription is not active");
        }
        CollectorKey key = new CollectorKey(remoteId, messageId, requestId);
        ResponseCollector collector = new ResponseCollector(messageId, remoteId, requestId, ttlSeconds);
        if (collectors.putIfAbsent(key, collector) != null) {
            throw new IllegalStateException("Collector already exists for " + key);
        }
        return collector;
    }

    public void unregisterCollector(String messageId, String remoteId, String requestId) {
        if (messageId == null && remoteId == null && requestId == null) {
            collectors.values().forEach(ResponseCollector::close);
            collectors.clear();
            return;
        }
        collectors.entrySet().removeIf(entry -> {
            CollectorKey key = entry.getKey();
            boolean match = (messageId == null || messageId.equals(key.messageId()))
                    && (remoteId == null || remoteId.equals(key.remoteId()))
                    && (requestId == null || requestId.equals(key.requestId()));
            if (match) {
                entry.getValue().close();
            }
            return match;
        });
    }

    public String getTopic() {
        return topic;
    }

    private void onMessage(DmqResponseMessage message) {
        CollectorKey key = new CollectorKey(message.getSenderId(), message.getMessageId(), message.getRequestId());
        ResponseCollector collector = collectors.get(key);
        if (collector != null) {
            collector.putMessage(message);
        }
    }

    private record CollectorKey(String remoteId, String messageId, String requestId) {
    }
}
