// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for message queues.
 * 
 * <p>Provides the interface for publish/subscribe messaging pattern.
 */
public abstract class MessageQueueBase {
    
    /**
     * Starts the message queue.
     */
    public abstract void start();
    
    /**
     * Stops the message queue.
     *
     * @return a future that completes when the queue is stopped
     */
    public abstract CompletableFuture<Void> stop();
    
    /**
     * Subscribes to a topic.
     *
     * @param topic the topic to subscribe to
     * @return a subscription handle
     */
    public abstract SubscriptionBase subscribe(String topic);
    
    /**
     * Unsubscribes from a topic.
     *
     * @param topic the topic to unsubscribe from
     * @return a future that completes when unsubscription is done
     */
    public abstract CompletableFuture<Void> unsubscribe(String topic);
    
    /**
     * Produces a message to a topic.
     *
     * @param topic the topic to send to
     * @param message the message to send
     * @return a future that completes when the message is sent
     */
    public abstract CompletableFuture<Void> produceMessage(String topic, Object message);
}

