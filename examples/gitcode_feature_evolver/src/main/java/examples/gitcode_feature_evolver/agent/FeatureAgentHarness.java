/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import examples.gitcode_evolver_common.agent.EvolverAgentHarness;

/** Feature compatibility entrypoint for the shared Evolver Agent harness. */
final class FeatureAgentHarness {
    static final double CONTEXT_PRESSURE_RATIO = EvolverAgentHarness.CONTEXT_PRESSURE_RATIO;
    static final double MODEL_TIMEOUT_SECONDS = EvolverAgentHarness.MODEL_TIMEOUT_SECONDS;
    static final int COMPACTION_OUTPUT_TOKENS = EvolverAgentHarness.COMPACTION_OUTPUT_TOKENS;
    static final int COMPACTION_MESSAGES_TO_KEEP =
            EvolverAgentHarness.COMPACTION_MESSAGES_TO_KEEP;

    private FeatureAgentHarness() {
    }

    static FeatureModelReliabilityRail install(ReActAgent agent,
                                                ReActAgentConfig configuration) {
        return EvolverAgentHarness.install(agent, configuration,
                FeatureGuardedModel::new, FeatureModelReliabilityRail::new);
    }

    static int contextPressureTokens(String modelName) {
        return EvolverAgentHarness.contextPressureTokens(modelName);
    }
}
