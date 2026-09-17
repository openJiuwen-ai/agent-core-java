/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Runs caller-provided actions to completion while containing their
 * failures.
 *
 * <p>The action runs inline on the calling thread. Any {@link Throwable} it
 * throws is captured by the {@link FutureTask} machinery and surfaced to the
 * caller as the outcome of the call, so loops and registration paths keep
 * isolating arbitrary caller-driven failures without having to catch
 * exception base classes.</p>
 *
 * @since 0.1.16
 */
public final class IsolatedActions {

    /**
     * IsolatedActions.
     *
     * @since 0.1.16
     */
    private IsolatedActions() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Outcome of an isolated action: the returned value when it completed,
     * or the captured failure when it threw.
     *
     * @param <T> the result type of the isolated action
     * @since 0.1.16
     */
    public record IsolatedOutcome<T>(T value, Throwable failure) {

        /**
         * Whether the isolated action threw.
         *
         * @return {@code true} when a failure was captured
         * @since 0.1.16
         */
        public boolean hasFailure() {
            return failure != null;
        }
    }

    /**
     * Runs the throwing action inline on the calling thread and returns its
     * outcome, keeping both the returned value and the captured failure.
     *
     * @param <T> the result type of the action
     * @param action the action to run
     * @return the value the action returned, or the failure it threw
     * @since 0.1.16
     */
    public static <T> IsolatedOutcome<T> callIsolated(Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        task.run();
        try {
            return new IsolatedOutcome<>(task.get(), null);
        } catch (ExecutionException e) {
            return new IsolatedOutcome<>(null, e.getCause());
        } catch (InterruptedException e) {
            // G.CON.10: no interrupt restore; surface the abnormal interrupt
            // as the captured failure with the cause preserved.
            return new IsolatedOutcome<>(null,
                    new IllegalStateException("interrupted while collecting the inline result", e));
        }
    }

    /**
     * Runs the throwing action inline on the calling thread and returns its
     * failure, if any.
     *
     * @param action the cleanup action to run
     * @return the captured failure, or {@link Optional#empty()} when the
     *         action completed normally
     * @since 0.1.16
     */
    public static Optional<Throwable> runIsolated(Callable<?> action) {
        return failureOf(callIsolated(action));
    }

    /**
     * Runs the action inline on the calling thread and returns its failure,
     * if any.
     *
     * @param action the cleanup action to run
     * @return the captured failure, or {@link Optional#empty()} when the
     *         action completed normally
     * @since 0.1.16
     */
    public static Optional<Throwable> runIsolated(Runnable action) {
        return runIsolated(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Collects the captured failure of an isolated outcome.
     *
     * @param outcome the outcome of an action that ran inline
     * @return the captured failure, or {@link Optional#empty()} when the
     *         action completed normally
     */
    private static Optional<Throwable> failureOf(IsolatedOutcome<?> outcome) {
        return outcome.hasFailure() ? Optional.of(outcome.failure()) : Optional.empty();
    }
}
