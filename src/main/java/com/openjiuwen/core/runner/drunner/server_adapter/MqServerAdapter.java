/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.common.VirtualThreadSupport;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.RunnerTermination;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Server adapter responsible for handling distributed MQ requests.
 *
 * <p>Mirrors Python's {@code MqServerAdapter} in
 * {@code openjiuwen/core/runner/drunner/server_adapter/mq_server_adapter.py}.</p>
 */
public class MqServerAdapter {

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;

    private final String adapterId;
    private final String topic;
    private final Function<Map<String, Object>, Object> invokeHandler;
    private final Function<Map<String, Object>, Object> streamHandler;
    private final ExecutorService executor = VirtualThreadSupport.newThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, MessageTask> runningTasks = new ConcurrentHashMap<>();

    private MessageQueueBase mq;
    private SubscriptionBase subscription;
    private volatile boolean active;

    public MqServerAdapter(String adapterId,
                           String topic,
                           Function<Map<String, Object>, Object> invokeHandler,
                           Function<Map<String, Object>, Object> streamHandler) {
        this.adapterId = adapterId;
        this.topic = topic;
        this.invokeHandler = invokeHandler;
        this.streamHandler = streamHandler;
        this.mq = RunnerAccess.messageQueue();
    }

    public void start() {
        if (active) {
            return;
        }
        if (mq == null) {
            mq = RunnerAccess.messageQueue();
        }
        if (mq == null) {
            throw ErrorHelper.buildError(StatusCode.DIST_MESSAGE_QUEUE_CLIENT_START_ERROR,
                    "reason", "message queue not initialized");
        }
        subscription = mq.subscribe(topic);
        subscription.setMessageHandler(message -> {
            if (message instanceof DmqRequestMessage request) {
                handleMessage(request);
            }
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();
        active = true;
        LOGGER.info("[{}] Adapter started on {}", adapterId, topic);
    }

    public CompletionStage<Void> stop() {
        LOGGER.info("[{}] Stopping adapter...", adapterId);
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }
        active = false;
        if (subscription != null) {
            subscription.deactivate();
            if (mq != null) {
                mq.unsubscribe(topic);
            }
            subscription = null;
        }

        List<CompletableFuture<Void>> cancellations = new ArrayList<>();
        for (String msgId : new ArrayList<>(runningTasks.keySet())) {
            cancellations.add(cancelTask(msgId, true));
        }
        CompletableFuture<Void> combined = CompletableFuture.allOf(cancellations.toArray(CompletableFuture[]::new));
        return combined.whenComplete((ignored, error) -> {
            runningTasks.clear();
            executor.shutdownNow();
            scheduler.shutdownNow();
            LOGGER.info("[{}] Adapter stopped", adapterId);
        });
    }

    public boolean isActive() {
        return active;
    }

    public int runningTaskCount() {
        return runningTasks.size();
    }

    private void handleMessage(DmqRequestMessage message) {
        String msgId = message.getMessageId();
        LOGGER.info("[{}] Received message {}, message_type={}", adapterId, msgId, message.getType());

        if (message.getExpireAt() != null && message.getExpireAt() < nowSeconds()) {
            LOGGER.warning("[{}] Ignoring expired message {}, expire_at: {}, current_time: {}",
                    adapterId, msgId, message.getExpireAt(), nowSeconds());
            return;
        }

        if (message.getType() == DMessageType.STOP) {
            cancelTask(msgId, false);
            return;
        }

        if (runningTasks.containsKey(msgId)) {
            LOGGER.warning("[{}] Duplicate msg_id {}, replacing old task", adapterId, msgId);
            cancelTask(msgId, true).join();
        }

        Future<?> future = executor.submit(() -> processMessage(message));
        runningTasks.put(msgId, new MessageTask(message, future));
        if (message.getExpireAt() != null) {
            double delay = message.getExpireAt() - nowSeconds();
            if (delay > 0) {
                scheduler.schedule(() -> timeoutCancel(msgId), Math.round(delay * 1000.0d), TimeUnit.MILLISECONDS);
            }
        }
        LOGGER.info("[{}] Submitted task message_id={}", adapterId, msgId);
    }

    private void processMessage(DmqRequestMessage message) {
        try {
            Map<String, Object> payload = toPayloadMap(message.getBody());
            if (message.isEnableStream()) {
                int seq = 0;
                Iterator<Object> iterator = toIterator(streamHandler.apply(payload));
                while (iterator.hasNext()) {
                    mq.produceMessage(message.getReplyTopic(),
                            MqMessageUtils.buildStreamResponse(message, adapterId, iterator.next(), seq, false));
                    seq++;
                }
                mq.produceMessage(message.getReplyTopic(),
                        MqMessageUtils.buildFinalResponse(message, adapterId, seq));
            } else {
                Object result = awaitIfNeeded(invokeHandler.apply(payload));
                mq.produceMessage(message.getReplyTopic(),
                        MqMessageUtils.buildBatchResponse(message, adapterId, result));
            }
        } catch (CancellationException error) {
            LOGGER.info("[{}] Task {} cancelled", adapterId, message.getMessageId());
        } catch (RunnerTermination error) {
            LOGGER.info("[{}] Task {} cancelled", adapterId, message.getMessageId());
            throw error;
        } catch (BaseError error) {
            LOGGER.warning("[{}] adapter run error msg: {}: {}", adapterId, message.getMessageId(), error);
            mq.produceMessage(message.getReplyTopic(),
                    MqMessageUtils.buildErrorResponse(message, adapterId, error));
        } catch (Exception error) {
            LOGGER.exception("[{}] Unexpected error: {}", error, adapterId, error.getMessage());
            BaseError remoteError = ErrorHelper.buildError(
                    StatusCode.MESSAGE_QUEUE_MESSAGE_PROCESS_EXECUTION_ERROR,
                    "reason", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            mq.produceMessage(message.getReplyTopic(),
                    MqMessageUtils.buildErrorResponse(message, adapterId, remoteError));
        } finally {
            runningTasks.remove(message.getMessageId());
        }
    }

    private void timeoutCancel(String msgId) {
        MessageTask msgTask = runningTasks.get(msgId);
        if (msgTask == null) {
            return;
        }
        Future<?> task = msgTask.task();
        if (!task.isDone()) {
            LOGGER.warning("[{}] Task {} expired and will be cancelled", adapterId, msgId);
            task.cancel(true);
        }
    }

    private CompletableFuture<Void> cancelTask(String msgId, boolean innerCancel) {
        MessageTask msgTask = runningTasks.remove(msgId);
        if (msgTask == null) {
            LOGGER.info("[{}] No task found for msg_id {} during cancellation", adapterId, msgId);
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("[{}] Cancelling task {}", adapterId, msgId);
        msgTask.task().cancel(true);
        if (innerCancel) {
            try {
                BaseError error = ErrorHelper.buildError(
                        StatusCode.MESSAGE_QUEUE_MESSAGE_PROCESS_EXECUTION_ERROR,
                        "reason", "Task cancelled by adapter stop (" + adapterId + ")");
                mq.produceMessage(msgTask.message().getReplyTopic(),
                        MqMessageUtils.buildErrorResponse(msgTask.message(), adapterId, error));
            } catch (Exception error) {
                LOGGER.warning("[{}] Failed to send cancel error for task {}: {}",
                        adapterId, msgId, error.getMessage());
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private static Object awaitIfNeeded(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().join();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Iterator<Object> toIterator(Object value) {
        Object awaited = awaitIfNeeded(value);
        if (awaited instanceof Iterator<?> iterator) {
            return (Iterator<Object>) iterator;
        }
        if (awaited instanceof Iterable<?> iterable) {
            return (Iterator<Object>) iterable.iterator();
        }
        return List.of(awaited).iterator();
    }

    private static Map<String, Object> toPayloadMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private static double nowSeconds() {
        return System.currentTimeMillis() / 1000.0d;
    }

    /**
     * Reflection bridge for Python's lazy {@code Runner} imports used by the MQ server adapter.
     *
     * <p>Mirrors Python's {@code Runner} access in
     * {@code openjiuwen/core/runner/drunner/server_adapter/mq_server_adapter.py}.</p>
     */
    static final class RunnerAccess {
        private RunnerAccess() {
        }

        static MessageQueueBase messageQueue() {
            Object value = readRunnerMember("dist_pubsub", "distPubsub", "messageQueue");
            return value instanceof MessageQueueBase messageQueue ? messageQueue : null;
        }

        static Object runAgent(String agentId, Map<String, Object> inputs) {
            return invokeRunnerMethod("run_agent", "runAgent", agentId, inputs);
        }

        static Object runAgentStreaming(String agentId, Map<String, Object> inputs) {
            return invokeRunnerMethod("run_agent_streaming", "runAgentStreaming", agentId, inputs);
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
                value = invokeStaticNoArg(className, methodName);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private static Object invokeRunnerMethod(String pythonName, String javaName, Object... args) {
            for (String className : List.of("com.openjiuwen.core.runner.Runner")) {
                Object result = invokeStaticFlexible(className, pythonName, args);
                if (result != null) {
                    return result;
                }
                result = invokeStaticFlexible(className, javaName, args);
                if (result != null) {
                    return result;
                }
            }
            throw new IllegalStateException("Runner method not available: " + javaName);
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

        private static Object invokeStaticNoArg(String className, String methodName) {
            try {
                Method method = Class.forName(className).getMethod(methodName);
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static Object invokeStaticFlexible(String className, String methodName, Object... args) {
            try {
                Class<?> type = Class.forName(className);
                for (Method method : type.getMethods()) {
                    if (!method.getName().equals(methodName) || method.getParameterCount() < args.length) {
                        continue;
                    }
                    Object[] invocationArgs = new Object[method.getParameterCount()];
                    System.arraycopy(args, 0, invocationArgs, 0, args.length);
                    return method.invoke(null, invocationArgs);
                }
            } catch (ClassNotFoundException ignored) {
                return null;
            } catch (IllegalAccessException | InvocationTargetException error) {
                throw new CompletionException(error.getCause() == null ? error : error.getCause());
            }
            return null;
        }
    }
}
