/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.polling;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory status for the periodic polling loop.
 *
 * @since 0.1.12
 */
public final class PollingStatus {
    private final AtomicReference<PollingStatusSnapshot> snapshot = new AtomicReference<>(
            new PollingStatusSnapshot(PollingStatusSnapshot.Result.NEVER, 0L, 0L));

    /**
     * Record the start of a polling attempt.
     *
     * @param attemptedAt attempt instant
     */
    public void recordAttempt(Instant attemptedAt) {
        long attempt = attemptedAt.toEpochMilli();
        snapshot.updateAndGet(current -> new PollingStatusSnapshot(
                current.result(), attempt, current.lastSuccessAt()));
    }

    /**
     * Record a successful polling cycle.
     *
     * @param completedAt completion instant
     */
    public void recordSuccess(Instant completedAt) {
        long success = completedAt.toEpochMilli();
        snapshot.updateAndGet(current -> new PollingStatusSnapshot(
                PollingStatusSnapshot.Result.SUCCESS, current.lastAttemptAt(), success));
    }

    /** Record a failed polling cycle without exposing the failure details. */
    public void recordFailure() {
        snapshot.updateAndGet(current -> new PollingStatusSnapshot(
                PollingStatusSnapshot.Result.FAILURE, current.lastAttemptAt(), current.lastSuccessAt()));
    }

    /** @return current immutable polling status */
    public PollingStatusSnapshot snapshot() {
        return snapshot.get();
    }
}
