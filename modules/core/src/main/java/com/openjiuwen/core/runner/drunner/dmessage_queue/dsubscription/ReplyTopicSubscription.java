/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * 
 * @since 0.1.7
 */
public class ReplyTopicSubscription {
    private static final Logger logger = LoggerFactory.getLogger(ReplyTopicSubscription.class);

    private final MessageQueueBase mq;
    private final String topic;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<CollectorKey, ResponseCollector> collectors = new ConcurrentHashMap<>();

    private volatile boolean active;
    private SubscriptionBase subscription;

    /**
     * ReplyTopicSubscription.
     * 
     * @param mq mq
     * @param topic topic
     * @since 0.1.7
     */
    public ReplyTopicSubscription(MessageQueueBase mq, String topic) {
        this.mq = mq;
        this.topic = topic;
    }

    /**
     * activate.
     * 
     * @since 0.1.7
     */
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

    /**
     * deactivate.
     * 
     * @since 0.1.7
     */
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
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isActive() {
        return active;
    }

    /**
     * registerCollector.
     * 
     * @param messageId messageId
     * @param remoteId remoteId
     * @param requestId requestId
     * @param ttlSeconds ttlSeconds
     * @return the result
     * @since 0.1.7
     */
    public ResponseCollector registerCollector(String messageId, String remoteId, String requestId, Double ttlSeconds) {
        if (!active) {
            throw new CancellationException("ReplyTopicSubscription was cancelled");
        }
        int maxConcurrency = RunnerConfig.getRunnerConfig().getDistributedConfig().getMaxRequestConcurrency();
        if (collectors.size() >= maxConcurrency) {
            throw new RuntimeException("[ReplyTopicSubscription] Too many collectors (" + maxConcurrency + ")");
        }
        CollectorKey key = new CollectorKey(remoteId, messageId, normalizeRequestId(requestId));
        ResponseCollector collector = new ResponseCollector(messageId, remoteId, requestId, ttlSeconds);
        if (collectors.putIfAbsent(key, collector) != null) {
            throw new IllegalStateException("Collector already exists for " + key);
        }
        logger.info("[ReplyTopicSubscription] register collector for {}", key);
        return collector;
    }

    /**
     * unregisterCollector.
     * 
     * @param messageId messageId
     * @param remoteId remoteId
     * @param requestId requestId
     * @since 0.1.7
     */
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

    /**
     * getTopic.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTopic() {
        return topic;
    }

    /**
     * onMessage.
     * 
     * @param message message
     * @since 0.1.7
     */
    private void onMessage(DmqResponseMessage message) {
        CollectorKey key =
            new CollectorKey(message.getSenderId(), message.getMessageId(), normalizeRequestId(message.getRequestId()));
        ResponseCollector collector = collectors.get(key);
        if (collector != null) {
            collector.putMessage(message);
        } else {
            logger.info("[ReplyTopicSubscription] No collector for {}, discard message", key);
        }
    }

    /**
     * normalizeRequestId.
     * 
     * @param requestId requestId
     * @return the result
     * @since 0.1.7
     */
    private String normalizeRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    /**
     * Unique key identifying a collector by remote ID, message ID, and optional request ID.
     * 
     * @since 0.1.7
     */
    public record CollectorKey(String remoteId, String messageId, String requestId) {
    }
}
