/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.Objects;

/**
 * Optimistic workflow update applied after one bounded controller action.
 *
 * @param stage next persisted stage
 * @param resumeStage optional pause/retry resume stage
 * @param gateRound current review round
 * @param taskAttempt current TDD task attempt
 * @param error sanitized error or transition note
 * @since 0.1.12
 */
public record FeatureJobMutation(FeatureStage stage, FeatureStage resumeStage, int gateRound,
                                 int taskAttempt, String error) {
    /** Validate and normalize the update. */
    public FeatureJobMutation {
        stage = Objects.requireNonNull(stage, "stage must not be null");
        if (gateRound < 0 || taskAttempt < 0) {
            throw new IllegalArgumentException("workflow counters must not be negative");
        }
        error = error == null ? "" : error;
    }

    /**
     * Create an update that preserves counters from the supplied job.
     *
     * @param job current job
     * @param next next stage
     * @param message transition note
     * @return immutable mutation
     */
    public static FeatureJobMutation transition(FeatureJob job, FeatureStage next, String message) {
        FeatureJob.Progress current = Objects.requireNonNull(job, "job must not be null").progress();
        return new FeatureJobMutation(next, current.resumeStage(), current.gateRound(),
                current.taskAttempt(), message);
    }
}
