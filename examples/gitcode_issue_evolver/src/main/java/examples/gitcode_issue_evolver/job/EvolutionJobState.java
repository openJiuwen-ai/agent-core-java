/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.util.Set;

/**
 * Durable states for an Issue evolution job.
 *
 * @since 0.1.12
 */
public enum EvolutionJobState {
    RECEIVED,
    PLANNING,
    IMPLEMENTING,
    VERIFYING,
    COMMITTED,
    PUBLISHING,
    PR_CREATED,
    WAITING_REVIEW,
    CANCEL_REQUESTED,
    MERGED,
    CLOSED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCELLED;

    private static final Set<EvolutionJobState> ACTIVE = Set.of(
            RECEIVED, PLANNING, IMPLEMENTING, VERIFYING, COMMITTED, PUBLISHING,
            PR_CREATED, WAITING_REVIEW, FAILED_RETRYABLE, CANCEL_REQUESTED);

    /**
     * Report whether the state reserves the Issue uniqueness slot.
     *
     * @return {@code true} for active states
     */
    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    /**
     * Report whether entering this state should release a worker lease.
     *
     * @return {@code true} when the lease must be released
     */
    public boolean releasesLease() {
        return this == WAITING_REVIEW || this == MERGED || this == CLOSED
                || this == FAILED_FINAL || this == CANCELLED;
    }

    /**
     * Check whether a requested state change follows the durable worker state machine.
     *
     * @param destination requested destination state
     * @return whether the transition is allowed
     */
    public boolean canTransitionTo(EvolutionJobState destination) {
        if (destination == null) {
            return false;
        }
        if (isActive() && this != CANCEL_REQUESTED && destination == FAILED_FINAL) {
            return true;
        }
        return switch (this) {
            case RECEIVED -> destination == PLANNING || destination == FAILED_RETRYABLE
                    || destination == CANCEL_REQUESTED;
            case PLANNING -> destination == IMPLEMENTING || destination == PR_CREATED
                    || destination == FAILED_RETRYABLE || destination == CANCEL_REQUESTED;
            case IMPLEMENTING -> destination == VERIFYING
                    || destination == FAILED_RETRYABLE || destination == CANCEL_REQUESTED;
            case VERIFYING -> destination == COMMITTED
                    || destination == FAILED_RETRYABLE || destination == CANCEL_REQUESTED;
            case COMMITTED -> destination == PUBLISHING
                    || destination == FAILED_RETRYABLE || destination == CANCEL_REQUESTED;
            case PUBLISHING -> destination == PR_CREATED
                    || destination == FAILED_RETRYABLE || destination == CANCEL_REQUESTED;
            case PR_CREATED -> destination == WAITING_REVIEW || destination == MERGED
                    || destination == CLOSED || destination == FAILED_RETRYABLE
                    || destination == CANCEL_REQUESTED;
            case WAITING_REVIEW -> destination == MERGED || destination == CLOSED
                    || destination == CANCEL_REQUESTED;
            case FAILED_RETRYABLE -> destination == PLANNING
                    || destination == FAILED_RETRYABLE || destination == CANCEL_REQUESTED;
            case CANCEL_REQUESTED -> destination == CANCELLED;
            case MERGED, CLOSED, FAILED_FINAL, CANCELLED -> false;
        };
    }
}
