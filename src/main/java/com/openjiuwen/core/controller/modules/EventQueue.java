// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.runner.AsyncMessageHandler;
import com.openjiuwen.core.runner.InvokeQueueMessage;
import com.openjiuwen.core.runner.MessageQueueInMemory;
import com.openjiuwen.core.runner.SubscriptionBase;
import com.openjiuwen.core.session.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Event Queue.
 *
 * <p>Responsible for publishing and subscribing to events, and dispatching them to the
 * appropriate methods of event handlers.
 *
 * <p>Built on top of a message queue ({@link MessageQueueInMemory}), it supports:
 * <ul>
 *   <li>Publishing events ({@link #publishEvent})</li>
 *   <li>Subscribing to events ({@link #subscribe})</li>
 *   <li>Unsubscribing from events ({@link #unsubscribe})</li>
 *   <li>Handling multiple event types</li>
 * </ul>
 *
 * <p>Event topic format: {@code {agent_id}_{session_id}_{event_type}}
 *
 * <p>Workflow:
 * <ol>
 *   <li>MessageQueue starts a background consumer task</li>
 *   <li>When publishEvent is called, the message is placed into the queue</li>
 *   <li>MessageQueue automatically invokes the registered callback function</li>
 *   <li>The callback converts the message into an EventHandlerInput and calls the
 *       corresponding EventHandler method</li>
 * </ol>
 *
 * <p>Python reference: {@code modules/event_queue.py::EventQueue}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class EventQueue {

    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    private ControllerConfig config;
    private final MessageQueueInMemory queue;
    private EventHandler eventHandler;

    /**
     * Constructs an EventQueue with the given configuration.
     *
     * @param config the controller configuration
     */
    public EventQueue(ControllerConfig config) {
        this.config = config;
        int queueSize = config.getEventQueueSize() != null ? config.getEventQueueSize() : 10000;
        long timeout = config.getEventTimeout() != null ? config.getEventTimeout().longValue() : 120000L;
        this.queue = new MessageQueueInMemory(queueSize, timeout);
        this.eventHandler = null;
    }

    /**
     * Gets the configuration.
     *
     * @return the controller configuration
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * Sets the configuration.
     *
     * @param config the new controller configuration
     */
    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    /**
     * Sets the event handler.
     *
     * @param eventHandler the event handler instance
     */
    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * Starts event queue message processing.
     *
     * <p>Starts the MessageQueue background consumer task.
     */
    public void start() {
        queue.start();
    }

    /**
     * Stops event queue message processing.
     *
     * <p>Stops the MessageQueue background consumer task.
     */
    public void stop() {
        queue.stop().join();
    }

    /**
     * Subscribes to all event types for the given agent and session.
     *
     * @param agentId   the agent ID
     * @param sessionId the session ID
     * @return a {@link SubscribeResult} containing subscription and topic maps
     * @throws BaseError if subscription fails
     */
    public SubscribeResult subscribe(String agentId, String sessionId) {
        try {
            Map<EventType, String> topics = new HashMap<>();
            Map<EventType, String> subscriptions = new HashMap<>();

            // Subscribe to input event
            String topic = buildTopic(agentId, sessionId, EventType.INPUT.getValue());
            String sub = subscribeEvent(topic, eventHandler::handleInput);
            subscriptions.put(EventType.INPUT, sub);
            topics.put(EventType.INPUT, topic);

            // Subscribe to task interaction event
            topic = buildTopic(agentId, sessionId, EventType.TASK_INTERACTION.getValue());
            sub = subscribeEvent(topic, eventHandler::handleTaskInteraction);
            subscriptions.put(EventType.TASK_INTERACTION, sub);
            topics.put(EventType.TASK_INTERACTION, topic);

            // Subscribe to task completion event
            topic = buildTopic(agentId, sessionId, EventType.TASK_COMPLETION.getValue());
            sub = subscribeEvent(topic, eventHandler::handleTaskCompletion);
            subscriptions.put(EventType.TASK_COMPLETION, sub);
            topics.put(EventType.TASK_COMPLETION, topic);

            // Subscribe to task failed event
            topic = buildTopic(agentId, sessionId, EventType.TASK_FAILED.getValue());
            sub = subscribeEvent(topic, eventHandler::handleTaskFailed);
            subscriptions.put(EventType.TASK_FAILED, sub);
            topics.put(EventType.TASK_FAILED, topic);

            return new SubscribeResult(subscriptions, topics);

        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            logger.error("Event queue execution failed: {}", e.getMessage(), e);
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_EVENT_QUEUE_ERROR,
                e.getMessage(),
                null,
                e,
                Map.of("error_msg", e.getMessage())
            );
        }
    }

    /**
     * Unsubscribes from all event types for the given agent and session.
     *
     * @param agentId   the agent ID
     * @param sessionId the session ID
     */
    public void unsubscribe(String agentId, String sessionId) {
        // Unsubscribe from input event
        String topic = buildTopic(agentId, sessionId, EventType.INPUT.getValue());
        unsubscribeEvent(topic);

        // Unsubscribe from task interaction event
        topic = buildTopic(agentId, sessionId, EventType.TASK_INTERACTION.getValue());
        unsubscribeEvent(topic);

        // Unsubscribe from task completion event
        topic = buildTopic(agentId, sessionId, EventType.TASK_COMPLETION.getValue());
        unsubscribeEvent(topic);

        // Unsubscribe from task failed event
        topic = buildTopic(agentId, sessionId, EventType.TASK_FAILED.getValue());
        unsubscribeEvent(topic);
    }

    /**
     * Publishes an event to the event queue and waits until it is handled.
     *
     * @param agentId the agent ID
     * @param session the session object
     * @param event   the event to publish
     * @throws BaseError if event handling fails
     */
    @SuppressWarnings("unchecked")
    public void publishEvent(String agentId, Session session, Event event) {
        String sessionId = session.getSessionId();
        String topic = buildTopic(agentId, sessionId, event.getEventType().getValue());

        InvokeQueueMessage<Object> queueMessage = new InvokeQueueMessage<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("session", session);
        queueMessage.setPayload(payload);

        // Publish message
        queue.produceMessage(topic, queueMessage).join();

        // Wait until EventHandler finishes processing
        try {
            queueMessage.getResponse().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BaseError) {
                throw (BaseError) cause;
            }
            logger.error("Event handler failed for {}: {}", event.getEventType(), cause.getMessage(), cause);
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_EVENT_HANDLER_ERROR,
                cause.getMessage(),
                null,
                cause,
                Map.of("error_msg", cause.getMessage())
            );
        }
    }

    /**
     * Unsubscribes from all topics (stops the entire queue).
     */
    public void unsubscribeAll() {
        queue.stop().join();
    }

    /**
     * Builds an event topic string.
     *
     * @param agentId   the agent ID
     * @param sessionId the session ID
     * @param eventType the event type string
     * @return the topic string
     */
    public static String buildTopic(String agentId, String sessionId, String eventType) {
        return agentId + "_" + sessionId + "_" + eventType;
    }

    // ==================== Private Helpers ====================

    /**
     * Subscribes to a single event topic.
     */
    @SuppressWarnings("unchecked")
    private String subscribeEvent(
            String topic,
            Function<EventHandlerInput, CompletableFuture<Map<String, Object>>> eventHandleFunc) {

        SubscriptionBase subscription = queue.subscribe(topic);

        AsyncMessageHandler handler = (Object payload) -> {
            Map<String, Object> payloadMap = (Map<String, Object>) payload;
            Event event = (Event) payloadMap.get("event");
            Session session = (Session) payloadMap.get("session");
            EventHandlerInput handlerInput = new EventHandlerInput(event, session);
            return eventHandleFunc.apply(handlerInput).thenApply(result -> (Object) result);
        };

        subscription.setMessageHandler(handler);
        subscription.activate();

        return topic;
    }

    /**
     * Unsubscribes from a single event topic.
     */
    private void unsubscribeEvent(String topic) {
        queue.unsubscribe(topic).join();
    }

    /**
     * Gets the internal message queue (for testing).
     *
     * @return the message queue
     */
    MessageQueueInMemory getQueue() {
        return queue;
    }

    /**
     * Gets the event handler (for testing).
     *
     * @return the event handler, or null
     */
    EventHandler getEventHandler() {
        return eventHandler;
    }

    /**
     * Result of a subscribe operation.
     *
     * @param subscriptions map of EventType to subscription identifier (topic string)
     * @param topics        map of EventType to topic string
     */
    public record SubscribeResult(Map<EventType, String> subscriptions,
                                   Map<EventType, String> topics) {}
}
