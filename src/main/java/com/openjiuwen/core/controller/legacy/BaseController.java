  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.runner.mq.InvokeQueueMessage;
import com.openjiuwen.core.runner.mq.MessageQueueInMemory;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import com.openjiuwen.core.session.Session;
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
 * Legacy controller base class backed by the in-memory message queue.
 */
public abstract class BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(BaseController.class);

    protected Object config;
    protected ContextEngine contextEngine;

    private final MessageQueueInMemory msgQueue = new MessageQueueInMemory();
    private final Map<String, SubscriptionBase> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean started;
    private Object group;

    protected BaseController() {
    }

    protected BaseController(Object config, ContextEngine contextEngine) {
        this.config = config;
        this.contextEngine = contextEngine;
    }

    public void setupFromAgent(Object agent) {
        this.config = readProperty(agent, "getAgentConfig", "agentConfig");
        Object engine = readProperty(agent, "getContextEngine", "contextEngine");
        if (engine instanceof ContextEngine ce) {
            this.contextEngine = ce;
        }
    }

    public Map<String, Object> invoke(Map<String, Object> inputs, Session session) {
        ensureStarted();
        String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
        getOrCreateSubscription(conversationId);

        InvokeQueueMessage queueMessage = new InvokeQueueMessage();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", createMessage(inputs));
        payload.put("session", session);
        queueMessage.setPayload(payload);

        msgQueue.produceMessage(topicFor(conversationId), queueMessage);
        try {
            Object result = queueMessage.getResponse().get();
            if (result instanceof Map<?, ?> map) {
                return castMap(map);
            }
            return Map.of("output", result);
        } catch (Exception e) {
            throw new RuntimeException("Legacy controller invoke failed", e);
        }
    }

    protected abstract Map<String, Object> handleEvent(Event event, Session session);

    public Event createMessage(Map<String, Object> inputs) {
        Map<String, Object> extensions = new LinkedHashMap<>(inputs);
        extensions.remove("query");
        extensions.remove("conversation_id");
        extensions.remove("user_id");
        return Event.createUserEvent(
                inputs.get("query"),
                String.valueOf(inputs.getOrDefault("conversation_id", "default_session")),
                inputs.get("user_id") != null ? String.valueOf(inputs.get("user_id")) : null,
                extensions
        );
    }

    public void cleanupConversation(String conversationId) {
        SubscriptionBase subscription = subscriptions.remove(conversationId);
        if (subscription != null) {
            subscription.deactivate();
            msgQueue.unsubscribe(topicFor(conversationId));
            LOG.info("Cleaned up subscription for conversation_id={}", conversationId);
        }
    }

    public void stop() {
        for (Map.Entry<String, SubscriptionBase> entry : subscriptions.entrySet()) {
            entry.getValue().deactivate();
            msgQueue.unsubscribe(topicFor(entry.getKey()));
        }
        subscriptions.clear();
        msgQueue.stop();
        started = false;
    }

    public void setGroup(Object group) {
        this.group = group;
        LOG.debug("{}: Group reference injected", getClass().getSimpleName());
    }

    /**
     * Send event to specified agent (point-to-point).
     * Delegates to the group's controller for actual routing.
     * Mirrors Python's {@code BaseController.send_to_agent()}.
     */
    public Object sendToAgent(String agentId, Event event, Session session) {
        if (group != null) {
            Object groupController = readProperty(group, "getGroupController", "groupController");
            if (groupController != null) {
                try {
                    Method method = groupController.getClass().getMethod("sendToAgent", Event.class, String.class, Session.class);
                    return method.invoke(groupController, event, agentId, session);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to send_to_agent via group controller", e);
                }
            }
        }
        throw new RuntimeException(
                getClass().getSimpleName() + ": Cannot sendToAgent('" + agentId + "'). "
                        + "Agent is not part of a group with a controller.");
    }

    /**
     * Publish event to subscribers (broadcast).
     * Delegates to the group's controller for actual routing.
     * Mirrors Python's {@code BaseController.publish()}.
     */
    @SuppressWarnings("unchecked")
    public List<Object> publish(Event event, Session session) {
        if (group != null) {
            Object groupController = readProperty(group, "getGroupController", "groupController");
            if (groupController != null) {
                try {
                    Method method = groupController.getClass().getMethod("publish", Event.class, Session.class);
                    Object result = method.invoke(groupController, event, session);
                    if (result instanceof List<?> list) {
                        return (List<Object>) list;
                    }
                    return result != null ? List.of(result) : Collections.emptyList();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to publish via group controller", e);
                }
            }
        }
        throw new RuntimeException(
                getClass().getSimpleName() + ": Cannot publish(). "
                        + "Agent is not part of a group with a controller.");
    }

    private void ensureStarted() {
        if (!started) {
            msgQueue.start();
            started = true;
        }
    }

    private SubscriptionBase getOrCreateSubscription(String conversationId) {
        return subscriptions.computeIfAbsent(conversationId, key -> {
            SubscriptionBase subscription = msgQueue.subscribe(topicFor(key));
            subscription.setMessageHandler(request -> {
                if (!(request instanceof Map<?, ?> map)) {
                    return CompletableFuture.completedFuture(null);
                }
                Event event = map.get("message") instanceof Event e ? e : null;
                Session session = map.get("session") instanceof Session s ? s : null;
                return CompletableFuture.completedFuture(handleEvent(event, session));
            });
            subscription.activate();
            LOG.info("Created legacy controller subscription for conversation_id={}", key);
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

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
