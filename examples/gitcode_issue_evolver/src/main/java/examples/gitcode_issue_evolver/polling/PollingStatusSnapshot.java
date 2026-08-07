/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.polling;

/**
 * Non-sensitive polling state exposed by readiness diagnostics.
 *
 * @param result latest polling result
 * @param lastAttemptAt latest attempt epoch milliseconds, or zero before the first attempt
 * @param lastSuccessAt latest success epoch milliseconds, or zero before the first success
 * @since 0.1.12
 */
public record PollingStatusSnapshot(Result result, long lastAttemptAt, long lastSuccessAt) {
    /** Latest polling result. */
    public enum Result {
        NEVER,
        SUCCESS,
        FAILURE
    }
}
