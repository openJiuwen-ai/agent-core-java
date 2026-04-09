  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.common.logging.Loggers;

/**
 * Utility class that replaces the Python {@code @rail} decorator.
 *
 * <p>Fires lifecycle events (before/after/on_exception) around a method body,
 * with optional retry support via {@link RetryRequest}.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * return RailExecutor.execute(ctx, before, after, onException, () -> {
 *     // method body
 *     return result;
 * });
 * }</pre>
 */
public final class RailExecutor {

    private RailExecutor() {}

    /**
     * Execute a callable with rail lifecycle events.
     *
     * @param ctx         the callback context
     * @param before      event fired before execution (may be null)
     * @param after       event fired after execution in finally block (may be null)
     * @param onException event fired when exception occurs (may be null)
     * @param body        the callable to execute
     * @param <T>         return type
     * @return the result from body
     */
    public static <T> T execute(
            AgentCallbackContext ctx,
            AgentCallbackEvent before,
            AgentCallbackEvent after,
            AgentCallbackEvent onException,
            RailBody<T> body
    ) {
        int attempt = 0;
        while (true) {
            ctx.consumeRetryRequest();
            ctx.setRetryAttempt(attempt);
            ctx.setException(null);
            try {
                if (before != null) {
                    ctx.fire(before);
                }
                return body.execute();
            } catch (Exception e) {
                ctx.setException(e);
                if (onException != null) {
                    ctx.fire(onException);
                }
                RetryRequest retryRequest = ctx.consumeRetryRequest();
                if (retryRequest == null) {
                    if (e instanceof RuntimeException re) {
                        throw re;
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
     * @param <T> return type
     */
    @FunctionalInterface
    public interface RailBody<T> {
        T execute() throws Exception;
    }
}
