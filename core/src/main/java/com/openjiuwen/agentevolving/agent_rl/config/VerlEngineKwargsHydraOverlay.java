/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Verl Engine kwargs Hydra overlay configuration.
 * <p>
 * Mirrors Python's {@code VerlEngineKwargsHydraOverlay} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class VerlEngineKwargsHydraOverlay {

    private VerlVllmEngineHydraKwargs vllm = new VerlVllmEngineHydraKwargs();

    public VerlVllmEngineHydraKwargs getVllm() { return vllm; }
    public void setVllm(VerlVllmEngineHydraKwargs vllm) { 
        this.vllm = vllm != null ? vllm : new VerlVllmEngineHydraKwargs(); 
    }
}
