// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.SubscriptionBase;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for listening to reply_topic and distributing responses to corresponding ResponseCollectors.
 * 
 * <p>Each registered collector is identified by a {@link CollectorKey} composed of (remoteId, messageId, requestId).
 * When a response message arrives, it is routed to the matching collector.
 * 
 * 对应Python: drunner/dmessage_queue/dsubscription/reply_topic_subscription.py - ReplyTopicSubscription
 */
public class ReplyTopicSubscription {

    private static final Logger logger = LoggerFactory.getLogger(ReplyTopicSubscription.class);

    private volatile boolean isActive = false;
    private final MessageQueueBase mq;
    private final String topic;
    private final Map<CollectorKey, ResponseCollector> collectors = new ConcurrentHashMap<>();
    private SubscriptionBase subscription;

    /**
     * Creates a ReplyTopicSubscription.
     *
     * @param mq    the message queue to subscribe to (required)
     * @param topic the topic to listen on (null to use default from RunnerConfig)
     */
    public ReplyTopicSubscription(MessageQueueBase mq, String topic) {
        this.mq = mq;
        if (topic != null) {
            this.topic = topic;
        } else {
            RunnerConfig config = RunnerConfig.getRunnerConfig();
            this.topic = config.replyTopicTemplate()
                .replace("{instance_id}", config.getInstanceId());
        }
    }

    /**
     * Initialize and activate the subscription.
     */
    public void activate() {
        subscription = mq.subscribe(topic);
        subscription.setMessageHandler(msg -> {
            onMessage(msg);
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();
        isActive = true;
        logger.info("[ReplyTopicSubscription] activated topic={}", topic);
    }

    /**
     * Clean up all collectors and deactivate.
     *
     * @return a future that completes when deactivation is done
     */
    public CompletableFuture<Void> deactivate() {
        isActive = false;
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        if (subscription != null) {
            future = mq.unsubscribe(topic);
        }
        unregisterCollector(null, null, null);
        logger.info("[ReplyTopicSubscription] Stopped");
        return future;
    }

    /**
     * Construct unique key for collector.
     */
    CollectorKey makeKey(String senderId, String messageId, String requestId) {
        // Normalize empty/blank requestId to null for consistent key matching
        String normalizedRequestId = (requestId != null && !requestId.isEmpty()) ? requestId : null;
        return new CollectorKey(senderId, messageId, normalizedRequestId);
    }

    /**
     * Distribute message to corresponding ResponseCollector.
     *
     * @param rawMsg the raw message (expected to be DmqResponseMessage)
     */
    public void onMessage(Object rawMsg) {
        DmqResponseMessage msg = (DmqResponseMessage) rawMsg;
        CollectorKey key = makeKey(msg.getSenderId(), msg.getMessageId(), msg.getRequestId());
        logger.info("[ReplyTopicSubscription] receive message key={}", key);

        ResponseCollector collector = collectors.get(key);
        if (collector != null) {
            collector.putMessage(msg);
        } else {
            logger.info("[ReplyTopicSubscription] No collector for {}, discard message", key);
        }
    }

    /**
     * Register a collector for waiting for the corresponding return.
     *
     * @param messageId the message ID to track
     * @param remoteId  the remote agent ID
     * @param requestId optional request ID (can be null)
     * @param ttl       time-to-live in seconds (null for default)
     * @return the registered ResponseCollector
     * @throws CancellationException if the subscription is not active
     * @throws RuntimeException      if too many collectors or duplicate key
     */
    public ResponseCollector registerCollector(String messageId, String remoteId,
                                                String requestId, Double ttl) {
        if (!isActive()) {
            throw new CancellationException("ReplyTopicSubscription was cancelled");
        }
        int maxConcurrency = RunnerConfig.getRunnerConfig().getDistributedConfig().getMaxRequestConcurrency();
        if (collectors.size() >= maxConcurrency) {
            throw new RuntimeException(
                "[ReplyTopicSubscription] Too many collectors (" + maxConcurrency + ")");
        }

        CollectorKey key = makeKey(remoteId, messageId, requestId);
        if (collectors.containsKey(key)) {
            throw new RuntimeException("[ReplyTopicSubscription] Collector already exists for " + key);
        }

        ResponseCollector collector = new ResponseCollector(messageId, remoteId, requestId, ttl);
        collectors.put(key, collector);
        logger.info("[ReplyTopicSubscription] register collector for {}", key);
        return collector;
    }

    /**
     * Clean up by message_id + remote_id + request_id.
     * If all are null, cleans up all collectors.
     *
     * @param messageId message ID filter (null for any)
     * @param remoteId  remote ID filter (null for any)
     * @param requestId request ID filter (null for any)
     */
    public void unregisterCollector(String messageId, String remoteId, String requestId) {
        logger.info("[ReplyTopicSubscription] unregister_collector message_id: {}, remote_id: {}, request_id: {}",
            messageId, remoteId, requestId);

        if (collectors.isEmpty()) {
            return;
        }

        // Filter targets
        List<CollectorKey> keysToRemove = new ArrayList<>();
        for (Map.Entry<CollectorKey, ResponseCollector> entry : collectors.entrySet()) {
            CollectorKey key = entry.getKey();
            if ((messageId == null && remoteId == null && requestId == null)
                || ((messageId == null || messageId.equals(key.messageId()))
                    && (remoteId == null || remoteId.equals(key.remoteId()))
                    && (requestId == null || requestId.equals(key.requestId())))) {
                keysToRemove.add(key);
            }
        }

        if (keysToRemove.isEmpty()) {
            logger.info("[ReplyTopicSub] No matching collectors for message_id={}, remote_id={}, request_id={}, collectors={}",
                messageId, remoteId, requestId, collectors);
            return;
        }

        logger.info("[ReplyTopicSub] unregistering {} collectors (msg_id={}, recv_id={}, req_id={})",
            keysToRemove.size(), messageId, remoteId, requestId);

        for (CollectorKey key : keysToRemove) {
            ResponseCollector collector = collectors.remove(key);
            if (collector != null) {
                collector.close();
            }
        }

        logger.info("[ReplyTopicSub] unregistered {} collectors", keysToRemove.size());
    }

    /**
     * @return true if the subscription is currently active
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Get the collectors map (for testing purposes).
     */
    public Map<CollectorKey, ResponseCollector> getCollectors() {
        return collectors;
    }

    /**
     * Get the topic.
     */
    public String getTopic() {
        return topic;
    }
}

