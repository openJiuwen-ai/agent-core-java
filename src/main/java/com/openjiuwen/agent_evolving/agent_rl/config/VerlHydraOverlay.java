/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Structured defaults merged on top of Verl's ppo_trainer Hydra config.
 * <p>
 * Mirrors Python's {@code VerlHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlHydraOverlay {

    private VerlDataHydraOverlay data = new VerlDataHydraOverlay();
    private VerlAlgorithmHydraOverlay algorithm = new VerlAlgorithmHydraOverlay();
    private VerlActorRolloutRefHydraOverlay actorRolloutRef = new VerlActorRolloutRefHydraOverlay();
    private VerlTrainerHydraOverlay trainer = new VerlTrainerHydraOverlay();
    private VerlRewardModelHydraOverlay rewardModel = new VerlRewardModelHydraOverlay();
    private JiuwenRLHydraOverlay jiuwenRL = new JiuwenRLHydraOverlay();

    public VerlDataHydraOverlay getData() { return data; }
    public void setData(VerlDataHydraOverlay data) { 
        this.data = data != null ? data : new VerlDataHydraOverlay(); 
    }
    public VerlAlgorithmHydraOverlay getAlgorithm() { return algorithm; }
    public void setAlgorithm(VerlAlgorithmHydraOverlay algorithm) { 
        this.algorithm = algorithm != null ? algorithm : new VerlAlgorithmHydraOverlay(); 
    }
    public VerlActorRolloutRefHydraOverlay getActorRolloutRef() { return actorRolloutRef; }
    public void setActorRolloutRef(VerlActorRolloutRefHydraOverlay actorRolloutRef) { 
        this.actorRolloutRef = actorRolloutRef != null ? actorRolloutRef : new VerlActorRolloutRefHydraOverlay(); 
    }
    public VerlTrainerHydraOverlay getTrainer() { return trainer; }
    public void setTrainer(VerlTrainerHydraOverlay trainer) { 
        this.trainer = trainer != null ? trainer : new VerlTrainerHydraOverlay(); 
    }
    public VerlRewardModelHydraOverlay getRewardModel() { return rewardModel; }
    public void setRewardModel(VerlRewardModelHydraOverlay rewardModel) { 
        this.rewardModel = rewardModel != null ? rewardModel : new VerlRewardModelHydraOverlay(); 
    }
    public JiuwenRLHydraOverlay getJiuwenRL() { return jiuwenRL; }
    public void setJiuwenRL(JiuwenRLHydraOverlay jiuwenRL) { 
        this.jiuwenRL = jiuwenRL != null ? jiuwenRL : new JiuwenRLHydraOverlay(); 
    }
}
