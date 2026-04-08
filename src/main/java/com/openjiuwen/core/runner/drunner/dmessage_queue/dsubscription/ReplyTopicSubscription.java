/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens on a reply topic and dispatches responses to collectors.
 */
public class ReplyTopicSubscription {

    private static final Logger logger = LoggerFactory.getLogger(ReplyTopicSubscription.class);

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
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();
        active = true;
        logger.info("[ReplyTopicSubscription] activated topic={}", topic);
    }

    public void deactivate() {
        active = false;
        if (subscription != null) {
            subscription.deactivate();
            mq.unsubscribe(topic);
            subscription = null;
        }
        // Close and clear all collectors
        for (ResponseCollector collector : collectors.values()) {
            collector.close(CancelReason.RUNNER_STOPPED);
        }
        collectors.clear();
        logger.info("[ReplyTopicSubscription] Stopped");
    }

    /**
     * Whether this subscription is currently active.
     */
    public boolean isActive() {
        return active;
    }

    public ResponseCollector registerCollector(String messageId, String remoteId, String requestId, Double ttlSeconds) {
        if (!active) {
            throw new CancellationException("ReplyTopicSubscription was cancelled");
        }
        int maxConcurrency = RunnerConfig.getRunnerConfig().getDistributedConfig().getMaxRequestConcurrency();
        if (collectors.size() >= maxConcurrency) {
            throw new RuntimeException(
                    "[ReplyTopicSubscription] Too many collectors (" + maxConcurrency + ")");
        }
        CollectorKey key = new CollectorKey(remoteId, messageId, normalizeRequestId(requestId));
        ResponseCollector collector = new ResponseCollector(messageId, remoteId, requestId, ttlSeconds);
        if (collectors.putIfAbsent(key, collector) != null) {
            throw new IllegalStateException("Collector already exists for " + key);
        }
        logger.info("[ReplyTopicSubscription] register collector for {}", key);
        return collector;
    }

    public void unregisterCollector(String messageId, String remoteId, String requestId) {
        if (messageId == null && remoteId == null && requestId == null) {
            collectors.values().forEach(c -> c.close(CancelReason.RUNNER_STOPPED));
            collectors.clear();
            return;
        }
        collectors.entrySet().removeIf(entry -> {
            CollectorKey key = entry.getKey();
            boolean match = (messageId == null || messageId.equals(key.messageId()))
                    && (remoteId == null || remoteId.equals(key.remoteId()))
                    && (requestId == null || requestId.equals(key.requestId()));
            if (match) {
                entry.getValue().close(CancelReason.RUNNER_STOPPED);
            }
            return match;
        });
    }

    public String getTopic() {
        return topic;
    }

    private void onMessage(DmqResponseMessage message) {
        CollectorKey key = new CollectorKey(
                message.getSenderId(),
                message.getMessageId(),
                normalizeRequestId(message.getRequestId())
        );
        ResponseCollector collector = collectors.get(key);
        if (collector != null) {
            collector.putMessage(message);
        } else {
            logger.info("[ReplyTopicSubscription] No collector for {}, discard message", key);
        }
    }

    private String normalizeRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    /**
     * Unique key identifying a collector by remote ID, message ID, and optional request ID.
     */
    public record CollectorKey(String remoteId, String messageId, String requestId) {
    }
}
