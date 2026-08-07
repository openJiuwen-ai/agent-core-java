/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.polling;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, non-sensitive polling status accumulator.
 *
 * @since 0.1.12
 */
final class FeaturePollingStatus {
    private final AtomicLong lastAttemptAt = new AtomicLong();
    private final AtomicLong lastSuccessAt = new AtomicLong();
    private final AtomicReference<FeaturePollingStatusSnapshot.Result> result =
            new AtomicReference<>(FeaturePollingStatusSnapshot.Result.NEVER_RUN);
    private final AtomicReference<String> summary = new AtomicReference<>("");

    void recordAttempt(Instant instant) {
        lastAttemptAt.set(instant.toEpochMilli());
    }

    void recordSuccess(Instant instant, String safeSummary) {
        lastSuccessAt.set(instant.toEpochMilli());
        result.set(FeaturePollingStatusSnapshot.Result.SUCCESS);
        summary.set(limit(safeSummary));
    }

    void recordFailure(String safeSummary) {
        result.set(FeaturePollingStatusSnapshot.Result.FAILED);
        summary.set(limit(safeSummary));
    }

    FeaturePollingStatusSnapshot snapshot() {
        return new FeaturePollingStatusSnapshot(result.get(), lastAttemptAt.get(),
                lastSuccessAt.get(), summary.get());
    }

    private static String limit(String text) {
        String value = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').strip();
        return value.substring(0, Math.min(value.length(), 300));
    }
}
