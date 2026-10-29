/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import examples.gitcode_evolver_common.agent.EvolverGuardedModel;

/** Feature compatibility wrapper around the shared Evolver guarded model. */
final class FeatureGuardedModel extends EvolverGuardedModel {
    FeatureGuardedModel(ModelClientConfig client, ModelRequestConfig request) {
        super(client, request);
    }

}
