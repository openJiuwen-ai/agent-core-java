/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Event queue responsible for event publishing and subscription.
 * <p>
 * Built on top of topic-based internal queues with handler dispatch.
 * Each subscription registers a handler that is invoked when an event
 * is published to the matching topic.
 * <p>
 * Event topic format: {@code {agentId}_{sessionId}_{eventType}}
 * <p>
 * Mirrors Python's {@code EventQueue}.
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
     */
    public void subscribe(String agentId, String sessionId) {
        try {
            subscribeEvent(buildTopic(agentId, sessionId, EventType.INPUT),
                    input -> eventHandler.handleInput(input));
            subscribeEvent(buildTopic(agentId, sessionId, EventType.TASK_INTERACTION),
                    input -> eventHandler.handleTaskInteraction(input));
            subscribeEvent(buildTopic(agentId, sessionId, EventType.TASK_COMPLETION),
                    input -> eventHandler.handleTaskCompletion(input));
            subscribeEvent(buildTopic(agentId, sessionId, EventType.TASK_FAILED),
                    input -> eventHandler.handleTaskFailed(input));
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
     */
    public void unsubscribe(String agentId, String sessionId) {
        unsubscribeEvent(buildTopic(agentId, sessionId, EventType.INPUT));
        unsubscribeEvent(buildTopic(agentId, sessionId, EventType.TASK_INTERACTION));
        unsubscribeEvent(buildTopic(agentId, sessionId, EventType.TASK_COMPLETION));
        unsubscribeEvent(buildTopic(agentId, sessionId, EventType.TASK_FAILED));
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
        } catch (RuntimeException e) {
            // Re-throw framework errors directly
            throw e;
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Event handler failed for {}: {}", event.getEventType(), e.getMessage());
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_EVENT_HANDLER_ERROR,
                    "error_msg", e.getMessage());
        }
    }

    /**
     * Unsubscribe from all topics.
     */
    public void unsubscribeAll() {
        stop();
    }

    // ==================== Internal ====================

    private void subscribeEvent(String topic, Consumer<EventHandlerInput> handler) {
        TopicSubscription sub = new TopicSubscription(topic, handler);
        subscriptions.put(topic, sub);
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
    private static class TopicSubscription {
        private final String topic;
        private final Consumer<EventHandlerInput> handler;
        private volatile boolean active = true;

        TopicSubscription(String topic, Consumer<EventHandlerInput> handler) {
            this.topic = topic;
            this.handler = handler;
        }

        void dispatch(EventHandlerInput input) {
            if (!active) {
                return;
            }
            handler.accept(input);
        }

        void stop() {
            active = false;
        }
    }
}
