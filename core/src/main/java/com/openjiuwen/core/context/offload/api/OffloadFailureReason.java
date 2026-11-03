/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

/**
 * Failure attribution enum attached to {@link OffloadMetric} log lines.
 *
 * <p>Every offload failure log line must carry one of these reasons (设计文档 §7.8 / D8);
 * stack-only failure logs are forbidden.</p>
 *
 * @since 2026-01-01
 */
public enum OffloadFailureReason {
    /** Filesystem full or quota exhausted. */
    ENOSPC,
    /** Generic IO error on the filesystem store. */
    IO_ERROR,
    /** Redis connection or command failure. */
    REDIS_DOWN,
    /** Payload sha256 verification mismatch. */
    SHA_MISMATCH,
    /** Payload JSON unparsable. */
    JSON_CORRUPT,
    /** Request namespace does not match the stored namespace. */
    NAMESPACE_MISMATCH,
    /** Illegal catalog state transition. */
    STATUS_INVALID
}
