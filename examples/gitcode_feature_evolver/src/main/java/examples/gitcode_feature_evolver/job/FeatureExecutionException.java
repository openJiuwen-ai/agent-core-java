/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;

/**
 * Carries an already classified failure across the Worker boundary.
 *
 * @since 0.1.12
 */
public final class FeatureExecutionException extends RuntimeException {
    private final FeatureFailure failure;

    /**
     * Create a classified execution exception.
     *
     * @param failure sanitized Controller failure
     */
    public FeatureExecutionException(FeatureFailure failure) {
        super(Objects.requireNonNull(failure, "failure must not be null").diagnostic().summary());
        this.failure = failure;
    }

    /** @return Controller-classified failure */
    public FeatureFailure failure() {
        return failure;
    }
}
