/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.Session;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

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
}