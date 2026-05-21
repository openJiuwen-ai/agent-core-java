/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes RolloutMessage objects into RolloutWithReward training samples.
 * <p>
 * Supports two modes:
 * - per-turn: each dialogue turn becomes a separate training sample
 * - whole-trajectory: the entire multi-turn conversation is one sample
 * <p>
 * Mirrors Python's {@code RolloutEncoder} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.encoding}.
 */
public class RolloutEncoder {

    private Object tokenizer; // Placeholder for Python tokenizer

    public RolloutEncoder(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    /**
     * Build rollout training samples in per-turn mode.
     * 
     * @param rolloutMsg Rollout message to encode
     * @return List of encoded rollouts with rewards
     */
    public List<Object> build(Object rolloutMsg) {
        // TODO: Implement actual encoding when schema is available
        // This requires:
        // 1. Extract rollout_info from rolloutMsg
        // 2. Apply chat template to each turn
        // 3. Split into prompt/response
        // 4. Tokenize
        // 5. Create RolloutWithReward objects
        
        return new ArrayList<>();
    }

    /**
     * Build rollout training samples in whole-trajectory mode.
     * 
     * @param rolloutMsg Rollout message to encode
     * @return List of encoded rollouts with rewards (single item for trajectory)
     */
    public List<Object> buildWholeTrajectory(Object rolloutMsg) {
        // TODO: Implement whole-trajectory encoding
        
        return new ArrayList<>();
    }

    public Object getTokenizer() { return tokenizer; }
    public void setTokenizer(Object tokenizer) { this.tokenizer = tokenizer; }
}