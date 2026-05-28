/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Stream controller for agent teams.
 * <p>
 * Manages streaming communication between agents using reactive streams.
 * <p>
 * Mirrors Python's {@code StreamController} in
 * {@code openjiuwen.agent_teams.agent.stream_controller}.
 */
public class StreamController implements Flow.Publisher<Object>, Flow.Subscriber<Object> {
    
    private String teamName;
    private SubmissionPublisher<Object> publisher;
    private Map<String, Flow.Subscription> subscriptions;
    private List<Flow.Subscriber<? super Object>> subscribers;
    
    /**
     * Create StreamController.
     *
     * @param teamName Team name
     */
    public StreamController(String teamName) {
        this.teamName = teamName;
        this.publisher = new SubmissionPublisher<>();
        this.subscriptions = new HashMap<>();
        this.subscribers = new ArrayList<>();
    }
    
    /**
     * Subscribe to this controller's stream.
     *
     * @param subscriber Subscriber
     */
    @Override
    public void subscribe(Flow.Subscriber<? super Object> subscriber) {
        subscribers.add(subscriber);
        publisher.subscribe(subscriber);
    }
    
    /**
     * Handle incoming subscription.
     *
     * @param subscription Subscription
     */
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // Request items
        subscription.request(Long.MAX_VALUE);
    }
    
    /**
     * Handle incoming item.
     *
     * @param item Item
     */
    @Override
    public void onNext(Object item) {
        // Forward to subscribers
        publisher.submit(item);
    }
    
    /**
     * Handle error.
     *
     * @param throwable Error
     */
    @Override
    public void onError(Throwable throwable) {
        // Close all subscribers
        publisher.closeExceptionally(throwable);
    }
    
    /**
     * Handle completion.
     */
    @Override
    public void onComplete() {
        publisher.close();
    }
    
    /**
     * Publish message to all subscribers.
     *
     * @param message Message to publish
     */
    public void publish(Object message) {
        publisher.submit(message);
    }
    
    /**
     * Close the stream controller.
     */
    public void close() {
        publisher.close();
        subscriptions.clear();
        subscribers.clear();
    }
    
    /**
     * Get subscriber count.
     *
     * @return Number of subscribers
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }
}