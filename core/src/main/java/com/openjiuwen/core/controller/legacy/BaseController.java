/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.runner.mq.AsyncMessageHandler;
import com.openjiuwen.core.runner.mq.InvokeQueueMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message queue based legacy controller.
 *
 * <p>Mirrors Python's {@code BaseController} in
 * {@code openjiuwen/core/controller/legacy/controller.py}.</p>
 */
public abstract class BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(BaseController.class);

    protected Object config;
    protected ContextEngine contextEngine;

    private MessageQueueBase msgQueue = new ControllerLocalMessageQueue();
    private final Map<String, SubscriptionBase> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean started = false;
    private Object group;

    protected BaseController() {
    }

    protected BaseController(Object config, ContextEngine contextEngine) {
        this.config = config;
        this.contextEngine = contextEngine;
    }

    /**
     * Setup controller from the owning agent.
     *
     * @param agent owning controller agent
     */
    public void setupFromAgent(Object agent) {
        Object agentConfig = readProperty(agent, "getAgentConfig", "agentConfig");
        if (agentConfig == null) {
            Object configObject = readProperty(agent, "getConfig", "config");
            agentConfig = readProperty(configObject, "getAgentConfig", "agentConfig");
        }
        if (agentConfig == null) {
            throw new IllegalArgumentException(
                    "Agent must have agentConfig or config.getAgentConfig()"
            );
        }
        this.config = agentConfig;

        Object engine = readProperty(agent, "getContextEngine", "contextEngine");
        if (!(engine instanceof ContextEngine currentContextEngine)) {
            throw new IllegalArgumentException("Agent must have contextEngine");
        }
        this.contextEngine = currentContextEngine;
    }

    /**
     * Synchronous invocation entry.
     *
     * @param inputs input map
     * @param session session object
     * @return handler result, or Python's default processed response
     */
    public Map<String, Object> invoke(Map<String, Object> inputs, Object session) {
        ensureStarted();
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        String conversationId = String.valueOf(
                safeInputs.getOrDefault("conversation_id", "default_session")
        );
        getOrCreateSubscription(conversationId);

        InvokeQueueMessage queueMessage = new InvokeQueueMessage();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", createMessage(safeInputs));
        payload.put("session", session);
        queueMessage.setPayload(payload);

        msgQueue.produceMessage(topicFor(conversationId), queueMessage);
        try {
            Object result = queueMessage.getResponse().get();
            if (result == null) {
                return Map.of("output", "processed");
            }
            if (result instanceof Map<?, ?> map) {
                return castMap(map);
            }
            return Map.of("output", result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg",
                    e.getMessage()
            );
        } catch (Exception e) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg",
                    e.getMessage()
            );
        }
    }

    /**
     * Core method for message processing.
     *
     * @param event event object
     * @param session session context
     * @return processing result
     */
    protected abstract Map<String, Object> handleEvent(Event event, Object session);

    /**
     * Create a user-input event from invoke inputs.
     *
     * @param inputs input map
     * @return created event
     */
    public Event createMessage(Map<String, Object> inputs) {
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        Map<String, Object> extensions = new LinkedHashMap<>(safeInputs);
        extensions.remove("query");
        extensions.remove("conversation_id");
        extensions.remove("user_id");
        return Event.createUserEvent(
                safeInputs.getOrDefault("query", ""),
                String.valueOf(safeInputs.getOrDefault("conversation_id", "default_session")),
                safeInputs.get("user_id") == null ? null : String.valueOf(safeInputs.get("user_id")),
                extensions
        );
    }

    /**
     * Clean up subscription for completed conversation.
     *
     * @param conversationId conversation id
     */
    public void cleanupConversation(String conversationId) {
        SubscriptionBase subscription = subscriptions.remove(conversationId);
        if (subscription != null) {
            String topic = topicFor(conversationId);
            subscription.deactivate();
            msgQueue.unsubscribe(topic);
            LOG.info("BaseController: cleaned up subscription for conversation_id={}", conversationId);
        }
    }

    /**
     * Stop controller and all active subscriptions.
     */
    public void stop() {
        for (Map.Entry<String, SubscriptionBase> entry : subscriptions.entrySet()) {
            entry.getValue().deactivate();
            msgQueue.unsubscribe(topicFor(entry.getKey()));
        }
        subscriptions.clear();
        msgQueue.stop();
        started = false;
    }

    /**
     * Set group reference, injected by group owner.
     *
     * @param group group object
     */
    public void setGroup(Object group) {
        this.group = group;
        LOG.debug("{}: Group reference injected", getClass().getSimpleName());
    }

    /**
     * Send event to specified agent through group controller.
     *
     * @param agentId target agent id
     * @param event event object
     * @param session session context
     * @return group controller result
     */
    public Object sendToAgent(String agentId, Event event, Object session) {
        Object groupController = group == null
                ? null
                : readProperty(group, "getGroupController", "groupController");
        if (groupController != null) {
            try {
                Method method = findMethod(groupController.getClass(), "sendToAgent", 3);
                return method.invoke(groupController, event, agentId, session);
            } catch (Exception e) {
                throw new RuntimeException("Failed to sendToAgent via group controller", e);
            }
        }
        throw new RuntimeException(
                getClass().getSimpleName() + ": Cannot sendToAgent('" + agentId + "'). "
                        + "Agent is not part of a group with a controller."
        );
    }

    /**
     * Publish event to subscribers through group controller.
     *
     * @param event event object
     * @param session session context
     * @return subscriber results
     */
    @SuppressWarnings("unchecked")
    public List<Object> publish(Event event, Object session) {
        Object groupController = group == null
                ? null
                : readProperty(group, "getGroupController", "groupController");
        if (groupController != null) {
            try {
                Method method = findMethod(groupController.getClass(), "publish", 2);
                Object result = method.invoke(groupController, event, session);
                if (result instanceof List<?> list) {
                    return (List<Object>) list;
                }
                return result == null ? Collections.emptyList() : List.of(result);
            } catch (Exception e) {
                throw new RuntimeException("Failed to publish via group controller", e);
            }
        }
        throw new RuntimeException(
                getClass().getSimpleName() + ": Cannot publish(). "
                        + "Agent is not part of a group with a controller."
        );
    }

    protected Object getConfig() {
        return config;
    }

    protected ContextEngine getContextEngine() {
        return contextEngine;
    }

    int subscriptionCount() {
        return subscriptions.size();
    }

    private void ensureStarted() {
        if (!started) {
            msgQueue.start();
            started = true;
        }
    }

    private SubscriptionBase getOrCreateSubscription(String conversationId) {
        return subscriptions.computeIfAbsent(conversationId, key -> {
            ControllerSubscription subscription = (ControllerSubscription) msgQueue.subscribe(topicFor(key));
            subscription.setMessageHandler(request -> {
                if (!(request instanceof Map<?, ?> rawRequest)) {
                    return CompletableFuture.completedFuture(null);
                }
                Event event = rawRequest.get("message") instanceof Event currentEvent
                        ? currentEvent
                        : null;
                Object session = rawRequest.get("session");
                try {
                    return CompletableFuture.completedFuture(handleEvent(event, session));
                } catch (Exception e) {
                    CompletableFuture<Object> failed = new CompletableFuture<>();
                    failed.completeExceptionally(e);
                    return failed;
                }
            });
            subscription.activate();
            LOG.info("BaseController: Created subscription for conversation_id={}", key);
            return subscription;
        });
    }

    private static String topicFor(String conversationId) {
        return "controller_messages_" + conversationId;
    }

    private static Object readProperty(Object target, String getterName, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (Exception ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method named " + methodName);
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static final class ControllerLocalMessageQueue extends MessageQueueBase {
        private final Map<String, ControllerSubscription> subscriptions = new ConcurrentHashMap<>();
        private volatile boolean running = false;

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            running = false;
            subscriptions.clear();
        }

        @Override
        public SubscriptionBase subscribe(String topic) {
            ControllerSubscription subscription = new ControllerSubscription();
            subscriptions.put(topic, subscription);
            return subscription;
        }

        @Override
        public void unsubscribe(String topic) {
            subscriptions.remove(topic);
        }

        @Override
        public void produceMessage(String topic, QueueMessage message) {
            if (!running) {
                return;
            }
            ControllerSubscription subscription = subscriptions.get(topic);
            if (subscription == null || !subscription.isActive()) {
                return;
            }
            Object request = message.getPayload();
            if (message instanceof InvokeQueueMessage invokeQueueMessage) {
                subscription.handle(request).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        invokeQueueMessage.getResponse().completeExceptionally(throwable);
                    } else {
                        invokeQueueMessage.getResponse().complete(result);
                    }
                });
            } else {
                subscription.handle(request);
            }
        }
    }

    private static final class ControllerSubscription extends SubscriptionBase {
        private AsyncMessageHandler<Object, Object> handler;
        private boolean active = false;

        @Override
        public void setMessageHandler(AsyncMessageHandler<Object, Object> handler) {
            this.handler = handler;
        }

        @Override
        public void activate() {
            active = true;
        }

        @Override
        public void deactivate() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        private CompletableFuture<Object> handle(Object message) {
            if (handler == null) {
                return CompletableFuture.completedFuture(null);
            }
            return handler.handle(message);
        }
    }
}
