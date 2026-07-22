/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

/**
 * Stable failure categories produced by deterministic verification and its repair loop.
 *
 * @since 0.1.12
 */
public enum VerificationFailureType {
    NONE,
    CHECK_FAILED,
    CI_INFRASTRUCTURE_FAILED,
    AGENT_INFRASTRUCTURE_FAILED;

    /**
     * Return whether deployment or runtime repair is required before verification can continue.
     *
     * @return {@code true} for CI or Agent infrastructure failures
     */
    public boolean isInfrastructureFailure() {
        return this == CI_INFRASTRUCTURE_FAILED || this == AGENT_INFRASTRUCTURE_FAILED;
    }
}
