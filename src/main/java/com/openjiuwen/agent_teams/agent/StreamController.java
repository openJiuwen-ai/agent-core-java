/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stream controller for agent teams.
 * <p>
 * Manages streaming communication between agents using reactive streams.
 * <p>
 * Mirrors Python's {@code StreamController} in
 * {@code openjiuwen.agent_teams.agent.stream_controller}.
 */
public class StreamController implements Flow.Publisher<Object>, Flow.Subscriber<Object> {
    public static final int MAX_RETRY_ATTEMPTS = 10;
    public static final int RETRYABLE_MODEL_CALL_FAILED_CODE = 181001;
    public static final String RETRY_QUERY = "Continue from the previous interrupted attempt.";
    private static final Pattern CODE_PREFIX = Pattern.compile("^\\[(\\d+)]\\s*(.*)$", Pattern.DOTALL);
    
    private String teamName;
    private SubmissionPublisher<Object> publisher;
    private Map<String, Flow.Subscription> subscriptions;
    private List<Flow.Subscriber<? super Object>> subscribers;
    private final Queue<Object> streamQueue;
    private RoundExecutor roundExecutor;
    private Consumer<ExecutionStatus> executionUpdater;
    
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
        this.streamQueue = new ArrayDeque<>();
    }

    public StreamController(String teamName, RoundExecutor roundExecutor, Consumer<ExecutionStatus> executionUpdater) {
        this(teamName);
        this.roundExecutor = roundExecutor;
        this.executionUpdater = executionUpdater;
    }

    public void setRoundExecutor(RoundExecutor roundExecutor) {
        this.roundExecutor = roundExecutor;
    }

    public void setExecutionUpdater(Consumer<ExecutionStatus> executionUpdater) {
        this.executionUpdater = executionUpdater;
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
        streamQueue.add(message);
        publisher.submit(message);
    }

    public void executeRound(String query) {
        if (roundExecutor == null) {
            throw new IllegalStateException("roundExecutor is required");
        }
        updateExecution(ExecutionStatus.RUNNING);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        int retryCount = 0;
        while (true) {
            TaskFailed failure = null;
            try {
                Iterable<Object> chunks = roundExecutor.execute(input);
                if (chunks != null) {
                    for (Object chunk : chunks) {
                        TaskFailed detected = detectTaskFailed(chunk);
                        if (detected != null) {
                            failure = detected;
                            break;
                        }
                        publish(chunk);
                    }
                }
            } catch (BaseError e) {
                updateExecution(ExecutionStatus.FAILED);
                throw e;
            } catch (java.util.concurrent.CancellationException e) {
                throw e;
            } catch (RuntimeException e) {
                updateExecution(ExecutionStatus.FAILED);
                throw new BaseError(StatusCode.AGENT_TEAM_EXECUTION_ERROR, e.getMessage(), null, e);
            }

            if (failure == null) {
                updateExecution(ExecutionStatus.COMPLETED);
                return;
            }
            if (failure.code() != null
                    && failure.code() == RETRYABLE_MODEL_CALL_FAILED_CODE
                    && retryCount < MAX_RETRY_ATTEMPTS) {
                retryCount++;
                input = new LinkedHashMap<>();
                input.put("query", RETRY_QUERY);
                continue;
            }
            updateExecution(ExecutionStatus.FAILED);
            throw new BaseError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "Team round failed: " + failure.text(),
                    null,
                    null
            );
        }
    }

    public List<Object> drainStreamQueue() {
        List<Object> drained = new ArrayList<>();
        while (!streamQueue.isEmpty()) {
            drained.add(streamQueue.poll());
        }
        return drained;
    }

    public List<Object> getStreamQueueSnapshot() {
        return new ArrayList<>(streamQueue);
    }

    public static TaskFailed detectTaskFailed(Object chunk) {
        Object payload = readValue(chunk, "payload");
        if (payload == null) {
            return null;
        }
        Object type = readValue(payload, "type");
        if (!"task_failed".equals(type)) {
            return null;
        }
        String text = firstText(readValue(payload, "data"));
        Matcher matcher = CODE_PREFIX.matcher(text);
        if (matcher.matches()) {
            return new TaskFailed(Integer.parseInt(matcher.group(1)), text);
        }
        return new TaskFailed(null, text);
    }
    
    /**
     * Close the stream controller.
     */
    public void close() {
        publisher.close();
        subscriptions.clear();
        subscribers.clear();
        streamQueue.clear();
    }
    
    /**
     * Get subscriber count.
     *
     * @return Number of subscribers
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    private void updateExecution(ExecutionStatus status) {
        if (executionUpdater != null && status != null) {
            executionUpdater.accept(status);
        }
    }

    private static String firstText(Object data) {
        if (data instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            Object text = readValue(first, "text");
            return text != null ? String.valueOf(text) : "";
        }
        return "";
    }

    private static Object readValue(Object target, String key) {
        if (target == null || key == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(key);
        }
        String getter = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                var field = target.getClass().getDeclaredField(key);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    @FunctionalInterface
    public interface RoundExecutor {
        Iterable<Object> execute(Map<String, Object> inputs);
    }

    public record TaskFailed(Integer code, String text) {
    }
}
