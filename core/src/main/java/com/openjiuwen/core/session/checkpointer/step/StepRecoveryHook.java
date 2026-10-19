/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

/**
 * Business hook deciding what happens to a leftover task found by recovery
 * or the dead-step sweeper.
 *
 * <p>Ordering contract (issue #195 section 3.5): recovery tries the hook
 * first; only when the hook is not registered, reports {@code NOT_HANDLED},
 * or throws does the caller fall back to the safe default. An undecided
 * external state must end in markLost, never in silent revival.</p>
 */
public interface StepRecoveryHook {

    /**
     * Attempt to recover one leftover session.
     *
     * @param request recovery request with the persisted cursor and session ids
     * @return the outcome; {@code NOT_HANDLED} lets the caller apply its default
     */
    RecoveryResult recover(RecoveryRequest request);

    /** Outcome of a recovery attempt. */
    enum Outcome {
        /** The hook resumed or re-queued the task; the caller stops here. */
        RECOVERED,
        /** The hook marked the task failed on purpose; the caller stops here. */
        MARKED_FAILED,
        /** The hook declined; the caller applies its own default policy. */
        NOT_HANDLED
    }

    /** Outcome values returned by {@link #recover}. */
    record RecoveryResult(Outcome outcome, String detail) {

        public static RecoveryResult recovered(String detail) {
            return new RecoveryResult(Outcome.RECOVERED, detail);
        }

        public static RecoveryResult markedFailed(String detail) {
            return new RecoveryResult(Outcome.MARKED_FAILED, detail);
        }

        public static RecoveryResult notHandled(String detail) {
            return new RecoveryResult(Outcome.NOT_HANDLED, detail);
        }
    }

    /**
     * Inputs handed to the hook for one leftover session.
     *
     * @param sessionId owning session id
     * @param agentId agent id within the session, empty when unknown
     * @param cursor last persisted step cursor, never null
     */
    record RecoveryRequest(String sessionId, String agentId, StepCursor cursor) {
    }
}
