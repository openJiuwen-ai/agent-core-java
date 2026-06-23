/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Utility holder for rail event metadata.
 *
 * <p>Mirrors Python's {@code rail} decorator support and {@code EVENT_METHOD_MAP} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public final class Rails {
    private static final Map<AgentCallbackEvent, String> EVENT_METHOD_MAP = new EnumMap<>(AgentCallbackEvent.class);

    static {
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_INVOKE, "before_invoke");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_INVOKE, "after_invoke");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_MODEL_CALL, "before_model_call");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_MODEL_CALL, "after_model_call");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.ON_MODEL_EXCEPTION, "on_model_exception");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_TOOL_CALL, "before_tool_call");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_TOOL_CALL, "after_tool_call");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.ON_TOOL_EXCEPTION, "on_tool_exception");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.BEFORE_TASK_ITERATION, "before_task_iteration");
        EVENT_METHOD_MAP.put(AgentCallbackEvent.AFTER_TASK_ITERATION, "after_task_iteration");
    }

    private Rails() {
    }

    public static Map<AgentCallbackEvent, String> eventMethodMap() {
        return Map.copyOf(EVENT_METHOD_MAP);
    }

    public static Object run(AgentCallbackContext context,
                             AgentCallbackEvent before,
                             AgentCallbackEvent after,
                             AgentCallbackEvent onException,
                             RailedOperation operation) {
        int attempt = 0;
        while (true) {
            RuntimeException exceptionToRaise = null;
            boolean cancelled = false;
            try {
                context.consumeRetryRequest();
                context.setRetryAttempt(attempt);
                context.setException(null);
                if (before != null) {
                    context.fire(before);
                }
                if (context.hasForceFinishRequest()) {
                    ForceFinishRequest request = context.consumeForceFinish();
                    return request == null ? null : request.getResult();
                }
                return operation.execute();
            } catch (CancellationException exception) {
                cancelled = true;
                throw exception;
            } catch (RuntimeException exception) {
                exceptionToRaise = exception;
                context.setException(exception);
                if (onException != null) {
                    try {
                        context.fire(onException);
                    } catch (RuntimeException ignored) {
                        // Preserve the original exception, matching Python's callback-error masking guard.
                    }
                }
                RetryRequest retryRequest = context.consumeRetryRequest();
                if (retryRequest == null) {
                    throw exception;
                }
                if (retryRequest.getDelaySeconds() > 0) {
                    try {
                        Thread.sleep(Math.round(retryRequest.getDelaySeconds() * 1000.0D));
                    } catch (InterruptedException interrupted) {
                        cancelled = true;
                        Thread.currentThread().interrupt();
                        throw new CancellationException("retry sleep interrupted");
                    }
                }
                exceptionToRaise = null;
                attempt++;
            } finally {
                if (after != null && !cancelled) {
                    try {
                        context.fire(after);
                    } catch (RuntimeException callbackException) {
                        if (exceptionToRaise == null) {
                            throw callbackException;
                        }
                    }
                }
            }
        }
    }

    /**
     * Callable body wrapped by {@link Rails#run}.
     *
     * <p>Mirrors Python's wrapped callable accepted by {@code rail} in
     * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
     */
    @FunctionalInterface
    public interface RailedOperation {
        Object execute();
    }
}
