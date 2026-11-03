/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.Optional;

/**
 * Utility class that replaces the Python {@code @rail} decorator.
 * <p>
 * Fires lifecycle events (before/after/on_exception) around a method body,
 * with optional retry support via {@link RetryRequest}.
 * </p>
 * <p>
 * Usage:
 * 
 * <pre>{@code
 * return RailExecutor.execute(ctx, before, after, onException, () -> {
 *     // method body
 *     return result;
 * });
 * }</pre>
 * 
 * @since 0.1.7
 */
public final class RailExecutor {
    /**
     * RailExecutor.
     * 
     * @since 0.1.7
     */
    private RailExecutor() {
    }

    /**
     * Execute a callable with rail lifecycle events.
     * 
     * @param ctx the callback context
     * @param before event fired before execution (may be null)
     * @param after event fired after execution in finally block (may be null)
     * @param onException event fired when exception occurs (may be null)
     * @param body the callable to execute
     * @return the result from body, or {@link Optional#empty()} when execution is skipped
     * @since 0.1.7
     */
    public static <T> Optional<T> execute(AgentCallbackContext ctx, AgentCallbackEvent before, AgentCallbackEvent after,
            AgentCallbackEvent onException, RailBody<T> body) {
        int attempt = 0;
        while (true) {
            ctx.consumeRetryRequest();
            ctx.setRetryAttempt(attempt);
            ctx.setException(null);
            try {
                if (before != null) {
                    ctx.fire(before);
                }
                if (ctx.hasForceFinishRequest()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(body.execute());
            } catch (Exception e) {
                ctx.setException(e);
                if (onException != null) {
                    ctx.fire(onException);
                }
                RetryRequest retryRequest = ctx.consumeRetryRequest();
                if (retryRequest == null) {
                    if (e instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException(e);
                }
                if (retryRequest.getDelaySeconds() > 0) {
                    try {
                        Thread.sleep((long) (retryRequest.getDelaySeconds() * 1000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Rail retry interrupted", ie);
                    }
                }
                attempt++;
            } finally {
                if (after != null) {
                    try {
                        ctx.fire(after);
                    } catch (Exception e) {
                        Loggers.AGENT.warning("Error firing after-rail event: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Functional interface for the body of a railed method.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface RailBody<T> {
        /**
         * execute.
         * 
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        T execute() throws Exception;
    }
}
