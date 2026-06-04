/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.Session;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Unified context object passed to rail/callback hooks.
 *
 * <p>Mirrors Python's {@code AgentCallbackContext} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
public class AgentCallbackContext {

    /** Reference to the BaseAgent instance. */
    private Object agent;

    /** Current callback event. */
    private AgentCallbackEvent event;

    /** Current event input data. */
    @Builder.Default
    private EventInputs inputs = null;

    /** Runtime configuration. */
    private Object config;

    /** Current Session object. */
    private Session session;

    /** Current ModelContext. */
    private ModelContext context;

    /** Cross-rail communication dict. */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();

    /** Exception object (set on error events). */
    private Exception exception;

    /** Current failed-attempt index. */
    @Builder.Default
    private int retryAttempt = 0;

    /** Retry request. */
    private RetryRequest retryRequest;

    /** Force-finish request. */
    private ForceFinishRequest forceFinishRequest;

    /** Optional steering queue shared with event handlers. */
    private Queue<String> steeringQueue;

    /**
     * Request the wrapped rail method to retry once more.
     *
     * @param delaySeconds sleep duration before next attempt
     */
    public void requestRetry(double delaySeconds) {
        if (delaySeconds < 0) {
            delaySeconds = 0.0;
        }
        this.retryRequest = RetryRequest.builder()
                .delaySeconds(delaySeconds)
                .build();
    }

    /**
     * Read and clear pending retry request.
     *
     * @return the pending retry request, or null
     */
    public RetryRequest consumeRetryRequest() {
        RetryRequest request = this.retryRequest;
        this.retryRequest = null;
        return request;
    }

    /**
     * Request the agent loop to terminate and return a result immediately.
     *
     * @param result final result payload
     */
    public void requestForceFinish(Map<String, Object> result) {
        this.forceFinishRequest = ForceFinishRequest.builder()
                .result(result)
                .build();
    }

    /**
     * Read and clear a pending force-finish request.
     *
     * @return the pending force-finish request, or null
     */
    public ForceFinishRequest consumeForceFinish() {
        ForceFinishRequest request = this.forceFinishRequest;
        this.forceFinishRequest = null;
        return request;
    }

    /**
     * Check whether a force-finish request is pending.
     *
     * @return true if a force-finish request has been set
     */
    public boolean hasForceFinishRequest() {
        return this.forceFinishRequest != null;
    }

    /**
     * Bind an external steering queue shared with event handlers.
     *
     * @param queue the queue to use for steering messages
     */
    public void bindSteeringQueue(Queue<String> queue) {
        this.steeringQueue = queue;
    }

    /**
     * Push a steering message into the bound queue.
     *
     * <p>Safe no-op when no queue has been bound.</p>
     *
     * @param msg steering instruction text
     */
    public void pushSteering(String msg) {
        if (this.steeringQueue != null) {
            this.steeringQueue.offer(msg);
        }
    }

    /**
     * Drain all currently pending steering messages.
     *
     * @return drained messages in FIFO order, or an empty list when unbound
     */
    public List<String> drainSteering() {
        if (this.steeringQueue == null) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = this.steeringQueue.poll()) != null) {
            messages.add(message);
        }
        return messages;
    }

    /**
     * Check whether steering messages are pending.
     *
     * @return true when a bound queue contains at least one message
     */
    public boolean hasPendingSteering() {
        return this.steeringQueue != null && !this.steeringQueue.isEmpty();
    }
}
