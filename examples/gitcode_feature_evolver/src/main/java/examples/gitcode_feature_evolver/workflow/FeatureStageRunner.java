/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

/**
 * Executes one leased, bounded feature stage.
 *
 * @since 0.1.12
 */
@FunctionalInterface
public interface FeatureStageRunner {
    /**
     * Execute one trusted request.
     *
     * @param request leased stage request
     * @return next durable outcome
     */
    FeatureStageOutcome execute(FeatureStageExecutor.ExecutionRequest request);
}
