/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.util.ArrayList;
import java.util.List;

/**
 * Rollout processors: classifier, validator, sampler utilities.
 * <p>
 * Mirrors Python's {@code RolloutClassifier} and {@code RolloutValidator} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.processors}.
 */
public class RolloutProcessors {

    /**
     * Classify rollout steps into positive and negative lists based on reward.
     * 
     * @param mdpList List of rollouts with rewards
     * @return Array of [positive_rollouts, negative_rollouts]
     */
    public static List<RolloutWithReward>[] defaultClassifyRollouts(List<RolloutWithReward> mdpList) {
        List<RolloutWithReward> posRollouts = new ArrayList<>();
        List<RolloutWithReward> negRollouts = new ArrayList<>();
        
        for (RolloutWithReward mdp : mdpList) {
            if (mdp.isPositive()) {
                posRollouts.add(mdp);
            } else {
                negRollouts.add(mdp);
            }
        }
        
        return new List[] { posRollouts, negRollouts };
    }

    /**
     * Default stop condition validation.
     * Stop when at least two positive samples exist and one achieves reward >= 1.0.
     * 
     * @param posRolloutList Positive rollouts
     * @param negRolloutList Negative rollouts
     * @return true if should stop
     */
    public static boolean defaultValidateStop(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList) {
        
        if (negRolloutList.size() > posRolloutList.size()) {
            return false;
        }
        if (posRolloutList.size() < 2) {
            return false;
        }
        
        for (RolloutWithReward rollout : posRolloutList) {
            Double reward = rollout.getReward();
            if (reward != null && reward == 1.0) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Balanced stopping rule: stop only when both positive and negative
     * counts meet their target amounts.
     * 
     * @param posRolloutList Positive rollouts
     * @param negRolloutList Negative rollouts
     * @param finalKeepPerPrompt Target samples per prompt
     * @return true if should stop
     */
    public static boolean validateStopBalanced(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList,
            int finalKeepPerPrompt) {
        
        int half = finalKeepPerPrompt / 2;
        return posRolloutList.size() >= half && negRolloutList.size() >= half;
    }

    /**
     * Default sampling function.
     * 
     * @param posRolloutList Positive rollouts
     * @param negRolloutList Negative rollouts
     * @param maxSamples Maximum samples to return
     * @return Combined sampled list
     */
    public static List<RolloutWithReward> defaultSampling(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList,
            int maxSamples) {
        
        List<RolloutWithReward> result = new ArrayList<>();
        
        // Sample from positive rollouts
        int posCount = Math.min(posRolloutList.size(), maxSamples / 2);
        for (int i = 0; i < posCount; i++) {
            result.add(posRolloutList.get(i));
        }
        
        // Sample from negative rollouts
        int negCount = Math.min(negRolloutList.size(), maxSamples / 2);
        for (int i = 0; i < negCount; i++) {
            result.add(negRolloutList.get(i));
        }
        
        return result;
    }
}