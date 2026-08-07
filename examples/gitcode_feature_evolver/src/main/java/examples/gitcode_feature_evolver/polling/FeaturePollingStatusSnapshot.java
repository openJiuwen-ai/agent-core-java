/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.polling;

/**
 * Non-sensitive polling health snapshot.
 *
 * @param result last result class
 * @param lastAttemptAt last attempt epoch milliseconds
 * @param lastSuccessAt last complete success epoch milliseconds
 * @param summary bounded result summary
 * @since 0.1.12
 */
public record FeaturePollingStatusSnapshot(Result result, long lastAttemptAt,
                                           long lastSuccessAt, String summary) {
    /** Last polling result class. */
    public enum Result {
        NEVER_RUN,
        SUCCESS,
        FAILED
    }
}
