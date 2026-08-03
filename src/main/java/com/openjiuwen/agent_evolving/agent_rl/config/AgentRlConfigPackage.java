/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Package bridge for the offline RL config exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/agent_evolving/agent_rl/config/__init__.py}.
 */
public final class AgentRlConfigPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/agent_rl/config/__init__.py";
    public static final Class<AdaConfig> ADA_CONFIG = AdaConfig.class;
    public static final Class<AgentRuntimeConfig> AGENT_RUNTIME_CONFIG = AgentRuntimeConfig.class;
    public static final Class<JiuwenRLHydraCustomFn> JIUWEN_RL_HYDRA_CUSTOM_FN = JiuwenRLHydraCustomFn.class;
    public static final Class<JiuwenRLHydraOverlay> JIUWEN_RL_HYDRA_OVERLAY = JiuwenRLHydraOverlay.class;
    public static final Class<PersistenceConfig> PERSISTENCE_CONFIG = PersistenceConfig.class;
    public static final Class<RLConfig> RL_CONFIG = RLConfig.class;
    public static final Class<RolloutConfig> ROLLOUT_CONFIG = RolloutConfig.class;
    public static final Class<TrainingConfig> TRAINING_CONFIG = TrainingConfig.class;
    public static final Class<VerlActorFsdpHydraOverlay> VERL_ACTOR_FSDP_HYDRA_OVERLAY = VerlActorFsdpHydraOverlay.class;
    public static final Class<VerlActorHydraOverlay> VERL_ACTOR_HYDRA_OVERLAY = VerlActorHydraOverlay.class;
    public static final Class<VerlActorRolloutRefHydraOverlay> VERL_ACTOR_ROLLOUT_REF_HYDRA_OVERLAY =
            VerlActorRolloutRefHydraOverlay.class;
    public static final Class<VerlAlgorithmHydraOverlay> VERL_ALGORITHM_HYDRA_OVERLAY = VerlAlgorithmHydraOverlay.class;
    public static final Class<VerlDataHydraOverlay> VERL_DATA_HYDRA_OVERLAY = VerlDataHydraOverlay.class;
    public static final Class<VerlEngineKwargsHydraOverlay> VERL_ENGINE_KWARGS_HYDRA_OVERLAY =
            VerlEngineKwargsHydraOverlay.class;
    public static final Class<VerlHydraOverlay> VERL_HYDRA_OVERLAY = VerlHydraOverlay.class;
    public static final Class<VerlModelHydraOverlay> VERL_MODEL_HYDRA_OVERLAY = VerlModelHydraOverlay.class;
    public static final Class<VerlRefFsdpHydraOverlay> VERL_REF_FSDP_HYDRA_OVERLAY = VerlRefFsdpHydraOverlay.class;
    public static final Class<VerlRefHydraOverlay> VERL_REF_HYDRA_OVERLAY = VerlRefHydraOverlay.class;
    public static final Class<VerlRewardModelHydraOverlay> VERL_REWARD_MODEL_HYDRA_OVERLAY =
            VerlRewardModelHydraOverlay.class;
    public static final Class<VerlRolloutHydraOverlay> VERL_ROLLOUT_HYDRA_OVERLAY = VerlRolloutHydraOverlay.class;
    public static final Class<VerlRolloutMultiTurnHydraOverlay> VERL_ROLLOUT_MULTI_TURN_HYDRA_OVERLAY =
            VerlRolloutMultiTurnHydraOverlay.class;
    public static final Class<VerlTrainerHydraOverlay> VERL_TRAINER_HYDRA_OVERLAY = VerlTrainerHydraOverlay.class;
    public static final Class<VerlVllmEngineHydraKwargs> VERL_VLLM_ENGINE_HYDRA_KWARGS =
            VerlVllmEngineHydraKwargs.class;

    private AgentRlConfigPackage() {
    }
}
