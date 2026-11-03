/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Event queue responsible for event publishing and subscription.
 * <p>
 * Built on top of topic-based internal queues with handler dispatch.
 * Each subscription registers a handler that is invoked when an event
 * is published to the matching topic.
 * <p>
 * Event topic format: {@code {agentId}_{sessionId}_{eventType}}
 * <p>
 * Mirrors Python's {@code EventQueue} in
 * {@code openjiuwen/core/controller/modules/event_queue.py}.
 */
public class EventQueue {

    private ControllerConfig config;
    private EventHandler eventHandler;

    private final Map<String, TopicSubscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EventQueue(ControllerConfig config) {
        this.config = config;
    }

    public ControllerConfig getConfig() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * Start event queue processing.
     */
    public void start() {
        running.set(true);
    }

    /**
     * Stop event queue processing and clear all subscriptions.
     */
    public void stop() {
        running.set(false);
        for (TopicSubscription sub : subscriptions.values()) {
            sub.stop();
        }
        subscriptions.clear();
    }

    /**
     * Subscribe to all event types for a given agent/session pair.
     *
     * @param agentId   agent ID
     * @param sessionId session ID
     * @return subscription and topic maps, matching Python's tuple return
     */
    public SubscriptionResult subscribe(String agentId, String sessionId) {
        try {
            Map<EventType, String> topics = new LinkedHashMap<>();
            Map<EventType, String> subscriptionMap = new LinkedHashMap<>();

            String topic = buildTopic(agentId, sessionId, EventType.INPUT);
            String subscription = subscribeEvent(topic, input -> eventHandler.handleInput(input));
            subscriptionMap.put(EventType.INPUT, subscription);
            topics.put(EventType.INPUT, topic);

            topic = buildTopic(agentId, sessionId, EventType.TASK_INTERACTION);
            subscription = subscribeEvent(topic, input -> eventHandler.handleTaskInteraction(input));
            subscriptionMap.put(EventType.TASK_INTERACTION, subscription);
            topics.put(EventType.TASK_INTERACTION, topic);

            topic = buildTopic(agentId, sessionId, EventType.TASK_COMPLETION);
            subscription = subscribeEvent(topic, input -> eventHandler.handleTaskCompletion(input));
            subscriptionMap.put(EventType.TASK_COMPLETION, subscription);
            topics.put(EventType.TASK_COMPLETION, topic);

            topic = buildTopic(agentId, sessionId, EventType.TASK_FAILED);
            subscription = subscribeEvent(topic, input -> eventHandler.handleTaskFailed(input));
            subscriptionMap.put(EventType.TASK_FAILED, subscription);
            topics.put(EventType.TASK_FAILED, topic);

            topic = buildTopic(agentId, sessionId, EventType.FOLLOW_UP);
            subscription = subscribeEvent(topic, input -> eventHandler.handleFollowUp(input));
            subscriptionMap.put(EventType.FOLLOW_UP, subscription);
            topics.put(EventType.FOLLOW_UP, topic);

            return new SubscriptionResult(subscriptionMap, topics);
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Event queue subscription failed: {}", e.getMessage());
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_EVENT_QUEUE_ERROR,
                    "error_msg", e.getMessage());
        }
    }

    /**
     * Unsubscribe from all event types for a given agent/session pair.
     *
     * @param agentId   agent ID
     * @param sessionId session ID
     * @return topic dictionary matching Python's return shape
     */
    public Map<EventType, String> unsubscribe(String agentId, String sessionId) {
        Map<EventType, String> topics = new LinkedHashMap<>();

        String topic = buildTopic(agentId, sessionId, EventType.INPUT);
        unsubscribeEvent(topic);
        topics.put(EventType.INPUT, topic);

        topic = buildTopic(agentId, sessionId, EventType.TASK_INTERACTION);
        unsubscribeEvent(topic);
        topics.put(EventType.TASK_INTERACTION, topic);

        topic = buildTopic(agentId, sessionId, EventType.TASK_COMPLETION);
        unsubscribeEvent(topic);
        topics.put(EventType.TASK_COMPLETION, topic);

        topic = buildTopic(agentId, sessionId, EventType.TASK_FAILED);
        unsubscribeEvent(topic);
        topics.put(EventType.TASK_FAILED, topic);

        topic = buildTopic(agentId, sessionId, EventType.FOLLOW_UP);
        unsubscribeEvent(topic);
        topics.put(EventType.FOLLOW_UP, topic);

        return topics;
    }

    /**
     * Publish an event to the queue and wait until it is handled.
     * <p>
     * This ensures event processing order by blocking until the handler finishes.
     *
     * @param agentId agent ID
     * @param session session object
     * @param event   event to publish
     */
    public void publishEvent(String agentId, AgentSessionApi session, Event event) {
        String sessionId = session.getSessionId();
        String topic = buildTopic(agentId, sessionId, event.getEventType());

        TopicSubscription sub = subscriptions.get(topic);
        if (sub == null) {
            Loggers.CONTROLLER.warning("No subscription for topic {}, event dropped", topic);
            return;
        }

        // Synchronous dispatch: build handler input and invoke handler directly
        EventHandlerInput handlerInput = new EventHandlerInput(event, session);
        try {
            sub.dispatch(handlerInput);
        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Event handler failed for {}: {}", event.getEventType(), e.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_EVENT_HANDLER_ERROR,
                    null,
                    null,
                    e,
                    Map.of("error_msg", e.getMessage())
            );
        }
    }

    /**
     * Publish an event and return immediately.
     *
     * <p>Mirrors Python's {@code publish_event_async} in
     * {@code openjiuwen/core/controller/modules/event_queue.py}.</p>
     *
     * @param agentId agent ID
     * @param session session object
     * @param event   event to publish
     * @return completion stage for the fire-and-forget dispatch
     */
    public CompletionStage<Void> publishEventAsync(String agentId, AgentSessionApi session, Event event) {
        return CompletableFuture.runAsync(() -> publishEvent(agentId, session, event));
    }

    /**
     * Unsubscribe from all topics.
     */
    public void unsubscribeAll() {
        stop();
    }

    // ==================== Internal ====================

    private String subscribeEvent(String topic, Function<EventHandlerInput, Map<String, Object>> handler) {
        TopicSubscription sub = new TopicSubscription(topic, handler);
        subscriptions.put(topic, sub);
        sub.activate();
        return topic;
    }

    private void unsubscribeEvent(String topic) {
        TopicSubscription sub = subscriptions.remove(topic);
        if (sub != null) {
            sub.stop();
        }
    }

    static String buildTopic(String agentId, String sessionId, EventType eventType) {
        return agentId + "_" + sessionId + "_" + eventType.getValue();
    }

    // ==================== Inner class ====================

    /**
     * Represents a topic subscription with its handler.
     */
    /**
     * Subscription and topic maps returned by {@link #subscribe(String, String)}.
     *
     * <p>Mirrors Python's {@code (subscriptions, topics)} return in
     * {@code openjiuwen/core/controller/modules/event_queue.py}.</p>
     */
    public record SubscriptionResult(Map<EventType, String> subscriptions, Map<EventType, String> topics) {
        public SubscriptionResult {
            subscriptions = subscriptions == null ? Map.of() : Map.copyOf(subscriptions);
            topics = topics == null ? Map.of() : Map.copyOf(topics);
        }
    }

    /**
     * Represents a topic subscription with its handler.
     *
     * <p>Mirrors Python queue subscriptions used by
     * {@code openjiuwen/core/controller/modules/event_queue.py}.</p>
     */
    private static class TopicSubscription {
        private final String topic;
        private final Function<EventHandlerInput, Map<String, Object>> handler;
        private volatile boolean active = false;

        TopicSubscription(String topic, Function<EventHandlerInput, Map<String, Object>> handler) {
            this.topic = topic;
            this.handler = handler;
        }

        Map<String, Object> dispatch(EventHandlerInput input) {
            if (!active) {
                return Map.of();
            }
            return handler.apply(input);
        }

        void activate() {
            active = true;
        }

        void stop() {
            active = false;
        }
    }
}
