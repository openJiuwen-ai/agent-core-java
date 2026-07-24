/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

/**
 * Typed GitCode failure; uncertain write failures require reconciliation before retry.
 *
 * @since 0.1.12
 */
public final class GitCodeApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int statusCode;
    private final boolean uncertain;

    /**
     * Create an API exception without a lower-level cause.
     *
     * @param message safe diagnostic message
     * @param statusCode HTTP status, or zero when no response was received
     * @param uncertain whether a remote write may have completed
     */
    public GitCodeApiException(String message, int statusCode, boolean uncertain) {
        this(message, statusCode, uncertain, null);
    }

    /**
     * Create an API exception while preserving its lower-level cause.
     *
     * @param message safe diagnostic message
     * @param statusCode HTTP status, or zero when no response was received
     * @param uncertain whether a remote write may have completed
     * @param cause lower-level transport or encoding failure
     */
    public GitCodeApiException(String message, int statusCode, boolean uncertain, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.uncertain = uncertain;
    }

    /**
     * Return the HTTP status associated with the failure.
     *
     * @return HTTP status, or zero when unavailable
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Report whether a write may have completed despite the failure.
     *
     * @return {@code true} when reconciliation is required
     */
    public boolean isUncertain() {
        return uncertain;
    }
}
