/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Verl Actor Rollout Reference Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlActorRolloutRefHydraOverlay} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class VerlActorRolloutRefHydraOverlay {

    private VerlModelHydraOverlay model = new VerlModelHydraOverlay();
    private VerlActorHydraOverlay actor = new VerlActorHydraOverlay();
    private VerlRefHydraOverlay ref = new VerlRefHydraOverlay();
    private VerlRolloutHydraOverlay rollout = new VerlRolloutHydraOverlay();

    public VerlModelHydraOverlay getModel() { return model; }
    public void setModel(VerlModelHydraOverlay model) { 
        this.model = model != null ? model : new VerlModelHydraOverlay(); 
    }
    public VerlActorHydraOverlay getActor() { return actor; }
    public void setActor(VerlActorHydraOverlay actor) { 
        this.actor = actor != null ? actor : new VerlActorHydraOverlay(); 
    }
    public VerlRefHydraOverlay getRef() { return ref; }
    public void setRef(VerlRefHydraOverlay ref) { 
        this.ref = ref != null ? ref : new VerlRefHydraOverlay(); 
    }
    public VerlRolloutHydraOverlay getRollout() { return rollout; }
    public void setRollout(VerlRolloutHydraOverlay rollout) { 
        this.rollout = rollout != null ? rollout : new VerlRolloutHydraOverlay(); 
    }
}