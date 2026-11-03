/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import java.util.List;

/**
 * Immutable per-request access and budget context for material resolution.
 *
 * <p>Roots are canonicalized absolute path strings resolved by the harness
 * side factory; this type performs no file I/O. {@code bytesAlreadyConsumed}
 * accumulates resolved image bytes so each item can be pre-checked against
 * the remaining total budget before reading.</p>
 *
 * @param allowedRoots canonicalized directories a path source may read from
 * @param assetRoot canonicalized directory owning version-pinned assets
 * @param maxImageBytes per-image byte budget
 * @param maxTotalImageBytes per-request total byte budget
 * @param readTimeoutMillis read deadline in milliseconds; values {@code <= 0}
 *                          expire immediately
 * @param bytesAlreadyConsumed image bytes resolved earlier in the same request
 * @since 0.1.17
 */
public record MaterialAccessContext(List<String> allowedRoots, String assetRoot, long maxImageBytes,
                                    long maxTotalImageBytes, long readTimeoutMillis, long bytesAlreadyConsumed) {

    /** Default per-image byte budget (20 MiB). */
    public static final long DEFAULT_MAX_IMAGE_BYTES = 20L * 1024L * 1024L;

    /** Default per-request total image byte budget (80 MiB). */
    public static final long DEFAULT_MAX_TOTAL_IMAGE_BYTES = 80L * 1024L * 1024L;

    /** Default single material read timeout in milliseconds. */
    public static final long DEFAULT_READ_TIMEOUT_MILLIS = 10_000L;

    /**
     * Create a context with an immutable copy of the allowed roots.
     *
     * @param allowedRoots canonicalized allowed directories; {@code null} becomes empty
     * @param assetRoot canonicalized asset root directory
     * @param maxImageBytes per-image byte budget
     * @param maxTotalImageBytes per-request total byte budget
     * @param readTimeoutMillis read deadline in milliseconds
     * @param bytesAlreadyConsumed bytes resolved earlier in this request
     */
    public MaterialAccessContext {
        allowedRoots = allowedRoots == null ? List.of() : List.copyOf(allowedRoots);
    }

    /**
     * Return a copy carrying an updated consumed-bytes counter.
     *
     * @param consumed total bytes consumed so far in this request
     * @return new context instance sharing the same limits and roots
     */
    public MaterialAccessContext withConsumedBytes(long consumed) {
        return new MaterialAccessContext(allowedRoots, assetRoot, maxImageBytes,
                maxTotalImageBytes, readTimeoutMillis, consumed);
    }

    /**
     * Return the remaining total budget for this request.
     *
     * @return non-negative remaining byte budget
     */
    public long remainingTotalBytes() {
        return Math.max(0L, maxTotalImageBytes - bytesAlreadyConsumed);
    }
}