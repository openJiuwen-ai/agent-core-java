/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Thread-local bridge that exposes the active invocation's time budget to tool-lane threads.
 *
 * <p>Tool dispatch runs on lane threads whose callback contexts hold a copied extra map, so the
 * budget state is reachable there; the per-tool timeout resolution deeper in the call chain has
 * no context access at all. {@code AbilityManager} attaches the state around tool execution
 * (gated on time-budget presence) and this holder lets the timeout resolver clamp the wait to
 * the remaining budget. Absent state means the original path is byte-identical.</p>
 *
 * <p>{@link #attach} returns the displaced previous attachment and {@link #detach} restores it.
 * The attach interval covers only this thread's timeout reads: the tool body is submitted to a
 * worker thread unconditionally, so an agent-as-tool inner invoke does not inherit this
 * attachment — inner tools are not clamped by the outer budget, and the outer
 * {@code orTimeout} on the inner agent's own tool wait remains the only bound. The
 * displaced/restore pairing exists for correctness on reuse of lane threads, not for
 * cross-thread nesting.</p>
 *
 * @since 2026-10-08
 */
public final class RunBudgetContext {
    private static final ThreadLocal<RunBudgetState> CURRENT = new ThreadLocal<>();

    private RunBudgetContext() {
    }

    /**
     * Attaches the budget state to the current thread, displacing any outer attachment.
     *
     * @param state active invocation state
     * @return the displaced previous attachment, or empty when none was attached
     */
    public static Optional<RunBudgetState> attach(RunBudgetState state) {
        RunBudgetState previous = CURRENT.get();
        CURRENT.set(state);
        return Optional.ofNullable(previous);
    }

    /**
     * Restores the previous attachment displaced by the matching {@link #attach} call.
     *
     * @param previous the attachment returned by the matching attach, may be empty
     */
    public static void detach(Optional<RunBudgetState> previous) {
        if (previous.isPresent()) {
            CURRENT.set(previous.get());
            return;
        }
        CURRENT.remove();
    }

    /**
     * Returns the remaining time budget of the attached invocation, when time-budgeted.
     *
     * @return remaining seconds, or empty when no time-budgeted state is attached
     */
    public static OptionalDouble currentRemainingSeconds() {
        RunBudgetState state = CURRENT.get();
        if (state == null || !state.isTimeEnabled()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(state.remainingSeconds());
    }
}
