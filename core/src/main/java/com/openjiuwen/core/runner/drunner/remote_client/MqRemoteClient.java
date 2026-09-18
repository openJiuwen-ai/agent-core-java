/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ResponseCollector;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

/**
 * Message-queue backed remote client.
 *
 * <p>Mirrors Python's {@code MqRemoteClient} in
 * {@code openjiuwen/core/runner/drunner/remote_client/mq_remote_clent.py}.</p>
 */
public class MqRemoteClient implements RemoteClient {

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;

    private final RemoteClientConfig config;
    private final Object lock = new Object();
    private MessageQueueBase mq;
    private ReplyTopicSubscription systemReplySub;
    private String topic;
    private String replyTopic;
    private String remoteId;
    private volatile boolean started;

    public MqRemoteClient(RemoteClientConfig config) {
        this.config = config == null ? RemoteClientConfig.builder().build() : config;
        this.topic = this.config.getTopic();
        this.remoteId = this.config.getId();
    }

    @Override
    public CompletionStage<Void> start() {
        if (started) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lock) {
            if (started) {
                return CompletableFuture.completedFuture(null);
            }
            MessageQueueBase resolvedMq = RunnerRuntimeAccess.messageQueue();
            ReplyTopicSubscription resolvedReplySubscription = RunnerRuntimeAccess.replySubscription();
            if (resolvedReplySubscription == null) {
                throw ErrorHelper.buildError(StatusCode.DIST_MESSAGE_QUEUE_CLIENT_START_ERROR,
                        "reason", "reply topic not initialized");
            }
            this.mq = resolvedMq;
            this.systemReplySub = resolvedReplySubscription;
            this.replyTopic = resolvedReplySubscription.getTopic();
            this.started = true;
            LOGGER.debug("[MqRemoteClient] init success topic: {}, reply_topic: {}", topic, replyTopic);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> stop() {
        this.started = false;
        LOGGER.info("[MqRemoteClient] Stopped client for {}", remoteId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
        start().toCompletableFuture().join();
        Map<String, Object> safeInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
        String messageId = buildMessageId(safeInputs);
        Double effectiveTimeout = effectiveTimeout(timeoutSeconds);
        LOGGER.info("[MqRemoteClient] Invoke {} with message_id: {}", remoteId, messageId);

        ResponseCollector collector = systemReplySub.registerCollector(messageId, remoteId, null, effectiveTimeout);
        LOGGER.info("[MqRemoteClient] Register collector with message_id: {}, remote_id: {}", messageId, remoteId);
        try {
            mq.produceMessage(topic, buildRequest(messageId, safeInputs, false, effectiveTimeout));
        } catch (RuntimeException error) {
            systemReplySub.unregisterCollector(messageId, remoteId, null);
            throw error;
        }

        CompletableFuture<Map<String, Object>> result = collector.result(effectiveTimeout).thenApply(this::asResultMap);
        return result.whenComplete((ignored, error) -> {
            if (isCancellation(error)) {
                LOGGER.info("[MqRemoteClient] Invoke {} cancelled, sending STOP", messageId);
                sendStopMessage(messageId);
            }
            systemReplySub.unregisterCollector(messageId, remoteId, null);
        });
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
        start().toCompletableFuture().join();
        Map<String, Object> safeInputs = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
        String messageId = UUID.randomUUID().toString();
        Double effectiveTimeout = effectiveTimeout(timeoutSeconds);
        LOGGER.info("[MQRemoteClient] Stream with message_id: {}", messageId);

        ResponseCollector collector = systemReplySub.registerCollector(messageId, remoteId, null, effectiveTimeout);
        try {
            mq.produceMessage(topic, buildRequest(messageId, safeInputs, true, effectiveTimeout));
            List<Object> chunks = collector.stream(effectiveTimeout).join();
            return chunks.iterator();
        } catch (CompletionException error) {
            if (isCancellation(error)) {
                LOGGER.info("[MQRemoteClient] Stream {} cancelled, sending STOP", messageId);
                sendStopMessage(messageId);
            }
            throw error;
        } finally {
            systemReplySub.unregisterCollector(messageId, remoteId, null);
        }
    }

    private void sendStopMessage(String messageId) {
        try {
            DmqRequestMessage stopMsg = new DmqRequestMessage();
            stopMsg.setType(DMessageType.STOP);
            stopMsg.setBody(Map.of());
            stopMsg.setMessageId(messageId);
            stopMsg.setSenderId(replyTopic);
            stopMsg.setReceiverId(remoteId);
            stopMsg.setExpireAt(nowSeconds() + RunnerConfigAccess.requestTimeout());
            mq.produceMessage(topic, stopMsg);
            LOGGER.info("[MqRemoteClient] Sent STOP message for {}", messageId);
        } catch (Exception error) {
            LOGGER.exception("[MqRemoteClient] Failed to send STOP message: {}", error, error.getMessage());
        }
    }

    private String buildMessageId(Map<String, Object> inputs) {
        String sessionId = inputs.get("conversation_id") == null
                ? "default_session"
                : String.valueOf(inputs.get("conversation_id"));
        return sessionId + "_" + UUID.randomUUID();
    }

    private DmqRequestMessage buildRequest(String messageId,
                                           Map<String, Object> inputs,
                                           boolean enableStream,
                                           Double timeoutSeconds) {
        DmqRequestMessage request = new DmqRequestMessage();
        request.setType(DMessageType.INPUT);
        request.setReplyTopic(replyTopic);
        request.setMessageId(messageId);
        request.setSenderId(replyTopic);
        request.setReceiverId(remoteId);
        request.setEnableStream(enableStream);
        request.setBody(inputs);
        if (timeoutSeconds != null) {
            request.setExpireAt(nowSeconds() + timeoutSeconds);
        }
        return request;
    }

    private Double effectiveTimeout(Double timeoutSeconds) {
        if (timeoutSeconds == null) {
            return RunnerConfigAccess.requestTimeout();
        }
        if (Double.compare(timeoutSeconds, 0.0d) == 0) {
            return null;
        }
        return timeoutSeconds;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asResultMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of("result", value);
    }

    private static boolean isCancellation(Throwable error) {
        Throwable current = unwrap(error);
        return current instanceof CancellationException || current instanceof TimeoutException;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof InvocationTargetException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static double nowSeconds() {
        return System.currentTimeMillis() / 1000.0d;
    }

    static final class RunnerRuntimeAccess {
        private RunnerRuntimeAccess() {
        }

        static MessageQueueBase messageQueue() {
            Object value = readRunnerMember("dist_pubsub", "distPubsub", "messageQueue");
            return value instanceof MessageQueueBase messageQueue ? messageQueue : null;
        }

        static ReplyTopicSubscription replySubscription() {
            Object value = readRunnerMember("system_reply_sub", "systemReplySub", "replySubscription");
            return value instanceof ReplyTopicSubscription replySubscription ? replySubscription : null;
        }

        private static Object readRunnerMember(String pythonFieldName, String javaFieldName, String methodName) {
            for (String className : List.of("com.openjiuwen.core.runner.Runner",
                    "com.openjiuwen.core.runner.drunner.DistributedRunner")) {
                Object value = readStaticField(className, pythonFieldName);
                if (value != null) {
                    return value;
                }
                value = readStaticField(className, javaFieldName);
                if (value != null) {
                    return value;
                }
                value = invokeStaticMethod(className, methodName);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private static Object readStaticField(String className, String fieldName) {
            try {
                Field field = Class.forName(className).getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static Object invokeStaticMethod(String className, String methodName) {
            try {
                Method method = Class.forName(className).getMethod(methodName);
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }
}
