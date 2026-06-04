/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolloutProcessorsTest {

    @Test
    void defaultClassifierSplitsPositiveAndNonPositiveRewards() {
        List<RolloutWithReward>[] classified = RolloutProcessors.defaultClassifyRollouts(List.of(
                rollout(1.0d),
                rollout(0.0d),
                rollout(-0.5d),
                rollout(null)
        ));

        assertEquals(1, classified[0].size());
        assertEquals(3, classified[1].size());
        assertEquals(1.0d, classified[0].get(0).getReward());
    }

    @Test
    void defaultValidateStopMatchesPythonEarlyReturns() {
        List<RolloutWithReward> positives = List.of(rollout(0.4d), rollout(1.0d));
        List<RolloutWithReward> negatives = List.of(rollout(-0.1d));

        assertTrue(RolloutProcessors.defaultValidateStop(positives, negatives));
        assertFalse(RolloutProcessors.defaultValidateStop(List.of(rollout(1.0d)), List.of()));
        assertFalse(RolloutProcessors.defaultValidateStop(positives,
                List.of(rollout(-1.0d), rollout(-2.0d), rollout(-3.0d))));
        assertFalse(RolloutProcessors.defaultValidateStop(List.of(rollout(0.5d), rollout(0.8d)), List.of()));
    }

    @Test
    void balancedStopUsesPositiveAndNegativeTargets() {
        List<RolloutWithReward> positives = List.of(rollout(1.0d), rollout(0.8d));
        List<RolloutWithReward> twoNegatives = List.of(rollout(-0.1d), rollout(-0.2d));
        List<RolloutWithReward> threeNegatives = List.of(rollout(-0.1d), rollout(-0.2d), rollout(-0.3d));

        assertFalse(RolloutProcessors.validateStopBalanced(positives, twoNegatives, 5));
        assertTrue(RolloutProcessors.validateStopBalanced(positives, threeNegatives, 5));
    }

    @Test
    void downsampleOneUidBalancesThenFillsRemainingCapacity() {
        List<RolloutWithReward> positives = List.of(
                rollout(1.0d), rollout(2.0d), rollout(3.0d), rollout(4.0d), rollout(5.0d));
        List<RolloutWithReward> negatives = List.of(rollout(-1.0d));

        RolloutProcessors.RolloutPair selected = RolloutProcessors.downsampleOneUid(positives, negatives, 4);

        assertEquals(3, selected.positiveRollouts().size());
        assertEquals(1, selected.negativeRollouts().size());
        assertEquals(1.0d, selected.positiveRollouts().get(0).getReward());
        assertEquals(3.0d, selected.positiveRollouts().get(2).getReward());
    }

    @Test
    void defaultSamplingReturnsCopiedDictionaries() {
        RolloutWithReward original = rollout(1.0d);
        ArrayList<RolloutWithReward> source = new ArrayList<>(List.of(original));

        RolloutProcessors.SamplingResult result = RolloutProcessors.defaultSampling(
                Map.of("uid", source),
                Map.of("uid", List.of(rollout(-1.0d)))
        );
        source.clear();

        assertEquals(1, result.positiveRollouts().get("uid").size());
        assertNotSame(original, result.positiveRollouts().get("uid").get(0));
        assertEquals(-1.0d, result.negativeRollouts().get("uid").get(0).getReward());
    }

    @Test
    void samplingAdaUsesUnionOfUidsAndSkipsEmptyInputs() {
        RolloutProcessors.SamplingResult result = RolloutProcessors.samplingAda(
                Map.of("a", List.of(rollout(1.0d), rollout(0.8d)), "empty", List.of()),
                Map.of("b", List.of(rollout(-1.0d)), "empty", List.of()),
                2
        );

        assertTrue(result.positiveRollouts().containsKey("a"));
        assertTrue(result.negativeRollouts().containsKey("b"));
        assertFalse(result.positiveRollouts().containsKey("empty"));
        assertEquals(2, result.positiveRollouts().get("a").size());
        assertEquals(1, result.negativeRollouts().get("b").size());
    }

    @Test
    void registryLoadsAndRetrievesPredefinedProcessors() {
        ProcessorsRegistry registry = new ProcessorsRegistry();

        assertEquals(2, registry.getClassifier("default_classify_rollouts")
                .apply(List.of(rollout(1.0d), rollout(-1.0d)))
                .positiveRollouts()
                .size() + registry.getClassifier("default_classify_rollouts")
                .apply(List.of(rollout(1.0d), rollout(-1.0d)))
                .negativeRollouts()
                .size());
        assertTrue(registry.getValidator("default_validate_stop")
                .apply(List.of(rollout(0.5d), rollout(1.0d)), List.of()));
        assertEquals(1, registry.getSampler("sampling_ada")
                .apply(Map.of("uid", List.of(rollout(1.0d))), Map.of())
                .positiveRollouts()
                .get("uid")
                .size());
        assertThrows(IllegalArgumentException.class, () -> registry.getSampler("missing"));
    }

    private static RolloutWithReward rollout(Double reward) {
        RolloutWithReward rollout = new RolloutWithReward();
        rollout.setReward(reward);
        return rollout;
    }
}
