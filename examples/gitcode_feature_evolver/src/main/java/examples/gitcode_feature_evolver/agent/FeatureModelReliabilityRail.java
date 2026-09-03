/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import examples.gitcode_evolver_common.agent.EvolverModelReliabilityRail;

/** Feature compatibility wrapper around the shared Evolver reliability rail. */
final class FeatureModelReliabilityRail extends EvolverModelReliabilityRail {
    FeatureModelReliabilityRail(String modelName) {
        super(modelName);
    }
}
