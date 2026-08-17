/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;

import java.util.Objects;
import java.util.Optional;

/**
 * One bounded stage outcome for durable worker persistence.
 *
 * @param mutation required next workflow update
 * @param pullRequest optional new canonical feature PR binding
 * @param systemTestPullRequest optional new canonical system-test PR binding
 * @since 0.1.12
 */
public record FeatureStageOutcome(FeatureJobMutation mutation,
                                  Optional<FeatureJob.PullRequest> pullRequest,
                                  Optional<FeatureJob.PullRequest> systemTestPullRequest) {
    /** Validate and freeze the outcome. */
    public FeatureStageOutcome {
        mutation = Objects.requireNonNull(mutation, "mutation must not be null");
        pullRequest = pullRequest == null ? Optional.empty() : pullRequest;
        systemTestPullRequest = systemTestPullRequest == null
                ? Optional.empty() : systemTestPullRequest;
    }

    /** Construct a feature-stage outcome without a system-test PR update. */
    public FeatureStageOutcome(FeatureJobMutation mutation,
                               Optional<FeatureJob.PullRequest> pullRequest) {
        this(mutation, pullRequest, Optional.empty());
    }

    /** Create an outcome without a PR binding update. */
    public static FeatureStageOutcome transition(FeatureJobMutation mutation) {
        return new FeatureStageOutcome(mutation, Optional.empty(), Optional.empty());
    }
}
