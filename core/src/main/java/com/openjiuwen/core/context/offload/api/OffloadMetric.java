/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import com.openjiuwen.core.context.offload.model.OffloadBackend;

/**
 * Structured one-line metric for an offload save/load operation.
 *
 * <p>{@link #toLogLine()} renders the shared key=value format (架构文档 §7.8):
 * {@code [Offload] event=save algorithmId=... handle=... backend=... success=...
 * failureReason=... preTokens=... postTokens=... durationMs=... ns=t/u/s/c}.</p>
 *
 * @since 2026-01-01
 */
public record OffloadMetric(String algorithmId, String handle, boolean isSuccess, String failureReason,
                            long preTokenCount, long postTokenCount, long durationMs, OffloadBackend backend) {
    private static final String NS_PLACEHOLDER = "-";

    /**
     * Renders the one-line key=value log payload for {@code event=save}.
     *
     * @return the formatted log line body for the {@code save} event
     */
    public String toLogLine() {
        return toLogLine("save");
    }

    /**
     * Renders the one-line key=value log payload for the given event name.
     *
     * @param event event name, typically {@code save} or {@code load}
     * @return formatted log line body
     */
    public String toLogLine(String event) {
        return "[Offload] event=" + nullSafe(event)
                + " algorithmId=" + nullSafe(algorithmId)
                + " handle=" + nullSafe(handle)
                + " backend=" + (backend == null ? NS_PLACEHOLDER : backend.name())
                + " success=" + isSuccess
                + " failureReason=" + (failureReason == null || failureReason.isBlank()
                ? NS_PLACEHOLDER : failureReason)
                + " preTokens=" + preTokenCount
                + " postTokens=" + postTokenCount
                + " durationMs=" + durationMs;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? NS_PLACEHOLDER : value;
    }
}
