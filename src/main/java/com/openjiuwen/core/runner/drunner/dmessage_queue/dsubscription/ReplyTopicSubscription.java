/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for listening to the reply topic and dispatching responses to response collectors.
 *
 * <p>Mirrors Python's openjiuwen/core/runner/drunner/dmessage_queue/dsubscription/reply_topic_subscription.py
 * {@code ReplyTopicSubscription}.</p>
 */
public class ReplyTopicSubscription {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyTopicSubscription.class);
    private static final String DEFAULT_REPLY_TOPIC_TEMPLATE = "openjiuwen.reply.runner.{instance_id}";
    private static final String DEFAULT_INSTANCE_ID = UUID.randomUUID().toString();
    private static final int DEFAULT_MAX_REQUEST_CONCURRENCY = 10_000;

    private final MessageQueueBase mq;
    private final String topic;
    private final Map<CollectorKey, ResponseCollector> collectors = new ConcurrentHashMap<>();
    private volatile Boolean active;
    private SubscriptionBase subscription;

    public ReplyTopicSubscription(MessageQueueBase mq) {
        this(mq, null);
    }

    public ReplyTopicSubscription(MessageQueueBase mq, String topic) {
        this.mq = mq;
        this.topic = topic == null ? resolveDefaultTopic() : topic;
        this.active = null;
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
        active = Boolean.TRUE;

        LOGGER.info("[ReplyTopicSubscription] activated topic={}", topic);
    }

    public void deactivate() {
        active = Boolean.FALSE;
        if (subscription != null) {
            mq.unsubscribe(topic);
        }
        unregisterCollector();
        LOGGER.info("[ReplyTopicSubscription] Stopped");
    }

    static CollectorKey makeKey(String senderId, String messageId, String requestId) {
        return new CollectorKey(senderId, messageId, normalizeRequestId(requestId));
    }

    public void onMessage(DmqResponseMessage message) {
        CollectorKey key = makeKey(message.getSenderId(), message.getMessageId(), message.getRequestId());
        LOGGER.info("[ReplyTopicSubscription] receive message key={}", key);

        ResponseCollector collector = collectors.get(key);
        if (collector != null) {
            invokeCollectorMethod(collector, "putMessage", message);
        } else {
            LOGGER.info("[ReplyTopicSubscription] No collector for {}, discard message", key);
        }
    }

    public ResponseCollector registerCollector(String messageId, String remoteId) {
        return registerCollector(messageId, remoteId, null, null);
    }

    public ResponseCollector registerCollector(String messageId, String remoteId, String requestId, Double ttlSeconds) {
        if (!Boolean.TRUE.equals(isActive())) {
            throw new CancellationException("ReplyTopicSubscription was cancelled");
        }
        int maxConcurrency = resolveMaxRequestConcurrency();
        if (collectors.size() >= maxConcurrency) {
            throw new RuntimeException("[ReplyTopicSubscription] Too many collectors (" + maxConcurrency + ")");
        }

        CollectorKey key = makeKey(remoteId, messageId, requestId);
        if (collectors.containsKey(key)) {
            throw new RuntimeException("[ReplyTopicSubscription] Collector already exists for " + key);
        }

        ResponseCollector collector = createCollector(messageId, remoteId, ttlSeconds);
        invokeCollectorNoArgIfPresent(collector, "start");
        ResponseCollector previous = collectors.putIfAbsent(key, collector);
        if (previous != null) {
            invokeCollectorNoArgIfPresent(collector, "close");
            throw new RuntimeException("[ReplyTopicSubscription] Collector already exists for " + key);
        }
        LOGGER.info("[ReplyTopicSubscription] register collector for {}", key);
        return collector;
    }

    public void unregisterCollector() {
        unregisterCollector(null, null, null);
    }

    public void unregisterCollector(String messageId, String remoteId, String requestId) {
        LOGGER.info("[ReplyTopicSubscription] unregister_collector message_id: {}, remote_id: {}, request_id:{}",
                messageId, remoteId, requestId);

        if (collectors.isEmpty()) {
            return;
        }

        List<CollectorKey> keysToRemove = new ArrayList<>();
        for (CollectorKey key : collectors.keySet()) {
            if (matches(key, messageId, remoteId, requestId)) {
                keysToRemove.add(key);
            }
        }

        if (keysToRemove.isEmpty()) {
            LOGGER.info("[ReplyTopicSub] No matching collectors for message_id={}, remote_id={}, request_id={}, "
                    + "collectors={}", messageId, remoteId, requestId, collectors);
            return;
        }

        LOGGER.info("[ReplyTopicSub] unregistering {} collectors (msg_id={}, recv_id={}, req_id={})",
                keysToRemove.size(), messageId, remoteId, requestId);

        for (CollectorKey key : keysToRemove) {
            ResponseCollector collector = collectors.remove(key);
            if (collector != null) {
                invokeCollectorNoArgIfPresent(collector, "close");
            }
        }

        LOGGER.info("[ReplyTopicSub] unregistered {} collectors", keysToRemove.size());
    }

    public Boolean isActive() {
        return active;
    }

    public String getTopic() {
        return topic;
    }

    int collectorCount() {
        return collectors.size();
    }

    private static boolean matches(CollectorKey key, String messageId, String remoteId, String requestId) {
        if (messageId == null && remoteId == null && requestId == null) {
            return true;
        }
        return (messageId == null || messageId.equals(key.messageId()))
                && (remoteId == null || remoteId.equals(key.remoteId()))
                && (requestId == null || requestId.equals(key.requestId()));
    }

    private static String normalizeRequestId(String requestId) {
        return requestId == null || requestId.isEmpty() ? null : requestId;
    }

    private static ResponseCollector createCollector(String messageId, String remoteId, Double ttlSeconds) {
        try {
            Constructor<ResponseCollector> constructor = ResponseCollector.class.getConstructor(
                    String.class, String.class, String.class, Double.class);
            return constructor.newInstance(messageId, remoteId, null, ttlSeconds);
        } catch (NoSuchMethodException ignored) {
            return createCollectorWithoutRequestId(messageId, remoteId, ttlSeconds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create ResponseCollector", unwrapInvocation(e));
        }
    }

    private static ResponseCollector createCollectorWithoutRequestId(
            String messageId, String remoteId, Double ttlSeconds) {
        try {
            Constructor<ResponseCollector> constructor = ResponseCollector.class.getConstructor(
                    String.class, String.class, Double.class);
            return constructor.newInstance(messageId, remoteId, ttlSeconds);
        } catch (NoSuchMethodException ignored) {
            return createCollectorWithPrimitiveTtl(messageId, remoteId, ttlSeconds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create ResponseCollector", unwrapInvocation(e));
        }
    }

    private static ResponseCollector createCollectorWithPrimitiveTtl(
            String messageId, String remoteId, Double ttlSeconds) {
        try {
            Constructor<ResponseCollector> constructor = ResponseCollector.class.getConstructor(
                    String.class, String.class, double.class);
            double effectiveTtlSeconds = ttlSeconds == null ? 30.0 : ttlSeconds;
            return constructor.newInstance(messageId, remoteId, effectiveTtlSeconds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create ResponseCollector", unwrapInvocation(e));
        }
    }

    private static void invokeCollectorNoArgIfPresent(ResponseCollector collector, String methodName) {
        Method method = findCollectorMethod(methodName, 0);
        if (method == null) {
            return;
        }
        invokeCollectorMethod(method, collector);
    }

    private static void invokeCollectorMethod(ResponseCollector collector, String methodName, DmqResponseMessage message) {
        Method method = findCollectorMethod(methodName, 1);
        if (method == null) {
            throw new IllegalStateException("ResponseCollector method missing: " + methodName);
        }
        invokeCollectorMethod(method, collector, message);
    }

    private static Method findCollectorMethod(String methodName, int parameterCount) {
        for (Method method : ResponseCollector.class.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static void invokeCollectorMethod(Method method, ResponseCollector collector, Object... args) {
        try {
            Object result = method.invoke(collector, args);
            if (result instanceof CompletionStage<?> stage) {
                stage.toCompletableFuture().join();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot call ResponseCollector." + method.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("ResponseCollector." + method.getName() + " failed", e.getCause());
        }
    }

    private static String resolveDefaultTopic() {
        Object config = getRunnerConfigIfAvailable();
        if (config == null) {
            return DEFAULT_REPLY_TOPIC_TEMPLATE.replace("{instance_id}", DEFAULT_INSTANCE_ID);
        }

        String template = invokeString(config, "replyTopicTemplate");
        if (template == null) {
            Object distributedConfig = readProperty(config, "getDistributedConfig", "distributedConfig");
            template = distributedConfig == null
                    ? ""
                    : invokeString(distributedConfig, "getReplyTopicTemplate", "");
        }
        if (template == null) {
            template = DEFAULT_REPLY_TOPIC_TEMPLATE;
        }

        Object instanceId = readProperty(config, "getInstanceId", "instanceId");
        return template.replace("{instance_id}",
                instanceId == null ? DEFAULT_INSTANCE_ID : String.valueOf(instanceId));
    }

    private static int resolveMaxRequestConcurrency() {
        Object config = getRunnerConfigIfAvailable();
        Object distributedConfig = readProperty(config, "getDistributedConfig", "distributedConfig");
        Object maxConcurrency = readProperty(distributedConfig, "getMaxRequestConcurrency", "maxRequestConcurrency");
        if (maxConcurrency instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_MAX_REQUEST_CONCURRENCY;
    }

    private static Object getRunnerConfigIfAvailable() {
        try {
            Class<?> runnerConfigClass = Class.forName("com.openjiuwen.core.runner.RunnerConfig");
            Method method = runnerConfigClass.getMethod("getRunnerConfig");
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                try {
                    Object result = method.invoke(target, args);
                    return result == null ? null : String.valueOf(result);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Object readProperty(Object target, String getterName, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Throwable unwrapInvocation(ReflectiveOperationException e) {
        if (e instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getCause() != null) {
            return invocationTargetException.getCause();
        }
        return e;
    }

    /**
     * Collector lookup key.
     *
     * <p>Mirrors Python's openjiuwen/core/runner/drunner/dmessage_queue/dsubscription/reply_topic_subscription.py
     * {@code CollectorKey}.</p>
     */
    public record CollectorKey(String remoteId, String messageId, String requestId) {
    }
}
