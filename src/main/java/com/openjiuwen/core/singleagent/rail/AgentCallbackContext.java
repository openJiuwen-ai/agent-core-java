/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.Session;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified context object passed to rail/callback hooks.
 *
 * <p>Attributes:
 * <ul>
 *   <li>agent: Reference to the BaseAgent instance</li>
 *   <li>event: Current callback event (set by fire())</li>
 *   <li>inputs: Current event input data (changes per event)</li>
 *   <li>config: Runtime configuration</li>
 *   <li>session: Current Session object</li>
 *   <li>context: Current ModelContext</li>
 *   <li>extra: Cross-rail communication dict</li>
 *   <li>exception: Exception object (set on error events)</li>
 *   <li>retryAttempt: Current failed-attempt index</li>
 * </ul>
 *
 * @since 0.1.7
 */
@Data
@Builder
public class AgentCallbackContext {
    private Object agent;
    private AgentCallbackEvent event;
    @Builder.Default
    private EventInputs inputs = null;
    private Object config;
    private Session session;
    private ModelContext context;
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
    private Exception exception;
    @Builder.Default
    private int retryAttempt = 0;
    private RetryRequest retryRequest;
    private ForceFinishRequest forceFinishRequest;

    /**
     * Trigger all registered callbacks for an event.
     *
     * @param event the event to fire
     */
    public void fire(AgentCallbackEvent event) {
        this.event = event;
        // Delegate to the agent's callback manager
        if (agent instanceof AgentCallbackFirer firer) {
            firer.fireCallbackEvent(event, this);
        }
    }

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
     * Request that the enclosing agent loop terminate immediately and return the provided result.
     *
     * @param result terminal result payload
     */
    public void requestForceFinish(Map<String, Object> result) {
        this.forceFinishRequest = ForceFinishRequest.builder()
                .result(result)
                .build();
    }

    /**
     * Read and clear the pending force-finish request.
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
     * @return true when force-finish has been requested
     */
    public boolean hasForceFinishRequest() {
        return this.forceFinishRequest != null;
    }

    /**
     * Execute a block of code wrapped in before/after lifecycle events.
     *
     * <p>Fires {@code before} on entry, then executes the body,
     * and fires {@code after} in the finally block (always).
     * Automatically saves and restores {@code inputs} so that
     * inner steps (model_call, tool_call) can freely overwrite
     * it without affecting the after event.</p>
     *
     * @param before event to fire on entry
     * @param after  event to fire on exit (always)
     * @param body   the code to execute between the events
     */
    public void lifecycle(AgentCallbackEvent before, AgentCallbackEvent after, Runnable body) {
        EventInputs savedInputs = this.inputs;
        fire(before);
        try {
            body.run();
        } finally {
            this.inputs = savedInputs;
            fire(after);
        }
    }

    /**
     * Force-finish request captured from rail callbacks.
     *
     * @since 0.1.7
     */
    @Data
    @Builder
    public static class ForceFinishRequest {
        private Map<String, Object> result;
    }
}
