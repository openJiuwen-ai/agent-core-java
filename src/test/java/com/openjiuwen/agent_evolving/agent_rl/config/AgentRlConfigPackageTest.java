/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRlConfigPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/agent_evolving/agent_rl/config/__init__.py", AgentRlConfigPackage.PYTHON_MODULE);
        assertEquals(AdaConfig.class, AgentRlConfigPackage.ADA_CONFIG);
        assertEquals(AgentRuntimeConfig.class, AgentRlConfigPackage.AGENT_RUNTIME_CONFIG);
        assertEquals(JiuwenRLHydraCustomFn.class, AgentRlConfigPackage.JIUWEN_RL_HYDRA_CUSTOM_FN);
        assertEquals(JiuwenRLHydraOverlay.class, AgentRlConfigPackage.JIUWEN_RL_HYDRA_OVERLAY);
        assertEquals(PersistenceConfig.class, AgentRlConfigPackage.PERSISTENCE_CONFIG);
        assertEquals(RLConfig.class, AgentRlConfigPackage.RL_CONFIG);
        assertEquals(RolloutConfig.class, AgentRlConfigPackage.ROLLOUT_CONFIG);
        assertEquals(TrainingConfig.class, AgentRlConfigPackage.TRAINING_CONFIG);
        assertEquals(VerlActorFsdpHydraOverlay.class, AgentRlConfigPackage.VERL_ACTOR_FSDP_HYDRA_OVERLAY);
        assertEquals(VerlActorHydraOverlay.class, AgentRlConfigPackage.VERL_ACTOR_HYDRA_OVERLAY);
        assertEquals(VerlActorRolloutRefHydraOverlay.class,
                AgentRlConfigPackage.VERL_ACTOR_ROLLOUT_REF_HYDRA_OVERLAY);
        assertEquals(VerlAlgorithmHydraOverlay.class, AgentRlConfigPackage.VERL_ALGORITHM_HYDRA_OVERLAY);
        assertEquals(VerlDataHydraOverlay.class, AgentRlConfigPackage.VERL_DATA_HYDRA_OVERLAY);
        assertEquals(VerlEngineKwargsHydraOverlay.class, AgentRlConfigPackage.VERL_ENGINE_KWARGS_HYDRA_OVERLAY);
        assertEquals(VerlHydraOverlay.class, AgentRlConfigPackage.VERL_HYDRA_OVERLAY);
        assertEquals(VerlModelHydraOverlay.class, AgentRlConfigPackage.VERL_MODEL_HYDRA_OVERLAY);
        assertEquals(VerlRefFsdpHydraOverlay.class, AgentRlConfigPackage.VERL_REF_FSDP_HYDRA_OVERLAY);
        assertEquals(VerlRefHydraOverlay.class, AgentRlConfigPackage.VERL_REF_HYDRA_OVERLAY);
        assertEquals(VerlRewardModelHydraOverlay.class, AgentRlConfigPackage.VERL_REWARD_MODEL_HYDRA_OVERLAY);
        assertEquals(VerlRolloutHydraOverlay.class, AgentRlConfigPackage.VERL_ROLLOUT_HYDRA_OVERLAY);
        assertEquals(VerlRolloutMultiTurnHydraOverlay.class,
                AgentRlConfigPackage.VERL_ROLLOUT_MULTI_TURN_HYDRA_OVERLAY);
        assertEquals(VerlTrainerHydraOverlay.class, AgentRlConfigPackage.VERL_TRAINER_HYDRA_OVERLAY);
        assertEquals(VerlVllmEngineHydraKwargs.class, AgentRlConfigPackage.VERL_VLLM_ENGINE_HYDRA_KWARGS);
    }
}
