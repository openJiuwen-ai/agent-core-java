/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.coordinator;

import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutWithReward;
import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's rollout processor tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_processors.py}.
 */
class RolloutProcessorsTest {

    @Test
    void classifyPositiveNegativeSplit() {
        List<RolloutWithReward>[] classified = RolloutProcessors.defaultClassifyRollouts(List.of(
                rollout(0.5d),
                rollout(-0.1d),
                rollout(1.0d),
                rollout(0.0d)
        ));

        assertEquals(2, classified[0].size());
        assertEquals(2, classified[1].size());
        assertTrue(classified[0].stream().allMatch(m -> m.getReward() > 0.0d));
        assertTrue(classified[1].stream().allMatch(m -> m.getReward() <= 0.0d));
    }

    @Test
    void classifyEmptyReturnsEmptyLists() {
        List<RolloutWithReward>[] classified = RolloutProcessors.defaultClassifyRollouts(List.of());
        assertEquals(List.of(), classified[0]);
        assertEquals(List.of(), classified[1]);
    }

    @Test
    void defaultValidateStopTrueWhenTwoPosAndOneRewardOne() {
        assertTrue(RolloutProcessors.defaultValidateStop(
                List.of(rollout(0.5d), rollout(1.0d)),
                List.of(rollout(-0.1d))
        ));
    }

    @Test
    void defaultValidateStopFalseWhenLessThanTwoPos() {
        assertFalse(RolloutProcessors.defaultValidateStop(List.of(rollout(1.0d)), List.of()));
    }

    @Test
    void defaultValidateStopFalseWhenNoRewardOne() {
        assertFalse(RolloutProcessors.defaultValidateStop(
                List.of(rollout(0.5d), rollout(0.8d)),
                List.of()
        ));
    }

    @Test
    void validateStopBalancedTrueWhenTargetsMet() {
        assertTrue(RolloutProcessors.validateStopBalanced(
                List.of(rollout(0.5d), rollout(0.5d), rollout(0.5d), rollout(0.5d)),
                List.of(rollout(-0.1d), rollout(-0.1d), rollout(-0.1d), rollout(-0.1d)),
                8
        ));
    }

    @Test
    void validateStopBalancedFalseWhenInsufficient() {
        assertFalse(RolloutProcessors.validateStopBalanced(
                List.of(rollout(0.5d), rollout(0.5d)),
                List.of(rollout(-0.1d), rollout(-0.1d)),
                8
        ));
    }

    @Test
    void defaultValidateStopEmptyListsFalse() {
        assertFalse(RolloutProcessors.defaultValidateStop(List.of(), List.of()));
    }

    @Test
    void defaultSamplingReturnsDeepCopyUnchangedCounts() {
        Map<String, List<RolloutWithReward>> pos = Map.of(
                "u1", new ArrayList<>(List.of(rollout(0.5d))),
                "u2", new ArrayList<>(List.of(rollout(0.6d), rollout(0.7d)))
        );
        Map<String, List<RolloutWithReward>> neg = Map.of(
                "u1", new ArrayList<>(List.of(rollout(-0.1d))),
                "u2", new ArrayList<>(List.of(rollout(-0.2d)))
        );

        RolloutProcessors.SamplingResult result = RolloutProcessors.defaultSampling(pos, neg);

        assertEquals(2, result.positiveRollouts().size());
        assertEquals(2, result.negativeRollouts().size());
        assertEquals(1, result.positiveRollouts().get("u1").size());
        assertEquals(2, result.positiveRollouts().get("u2").size());
        assertNotSame(pos, result.positiveRollouts());
        assertNotSame(neg, result.negativeRollouts());
    }

    @Test
    void downsampleOneUidBalancedTargetTotal() {
        RolloutProcessors.RolloutPair pair = RolloutProcessors.downsampleOneUid(
                repeatRollout(0.5d, 6),
                repeatRollout(-0.1d, 6),
                8
        );

        int total = pair.positiveRollouts().size() + pair.negativeRollouts().size();
        assertTrue(total <= 8);
        assertTrue(pair.positiveRollouts().size() <= 6);
        assertTrue(pair.negativeRollouts().size() <= 6);
    }

    @Test
    void samplingAdaPerUidDownsampled() {
        RolloutProcessors.SamplingResult result = RolloutProcessors.samplingAda(
                Map.of(
                        "u1", repeatRollout(0.5d, 10),
                        "u2", repeatRollout(0.6d, 5)
                ),
                Map.of(
                        "u1", repeatRollout(-0.1d, 10),
                        "u2", repeatRollout(-0.2d, 5)
                ),
                8
        );

        assertEquals(Set.of("u1", "u2"), result.positiveRollouts().keySet());
        assertEquals(Set.of("u1", "u2"), result.negativeRollouts().keySet());
        for (String uid : List.of("u1", "u2")) {
            int total = result.positiveRollouts().get(uid).size() + result.negativeRollouts().get(uid).size();
            assertTrue(total <= 8);
        }
    }

    @Test
    void getClassifierDefaultCallableMatchesStatic() {
        ProcessorsRegistry registry = new ProcessorsRegistry();
        RolloutProcessors.RolloutPair pair = registry.getClassifier("default_classify_rollouts")
                .apply(List.of(rollout(0.5d), rollout(-0.1d)));

        assertEquals(1, pair.positiveRollouts().size());
        assertEquals(1, pair.negativeRollouts().size());
    }

    @Test
    void getValidatorValidateStopBalanced() {
        ProcessorsRegistry registry = new ProcessorsRegistry();

        assertTrue(registry.getValidator("validate_stop_balanced").apply(
                repeatRollout(0.5d, 4),
                repeatRollout(-0.1d, 4),
                8
        ));
    }

    @Test
    void getSamplerSamplingAda() {
        ProcessorsRegistry registry = new ProcessorsRegistry();
        RolloutProcessors.SamplingResult result = registry.getSampler("sampling_ada").apply(
                Map.of("u1", List.of(rollout(0.5d))),
                Map.of("u1", List.of(rollout(-0.1d))),
                8
        );

        assertTrue(result.positiveRollouts().containsKey("u1"));
        assertTrue(result.negativeRollouts().containsKey("u1"));
    }

    @Test
    void getClassifierUnknownRaises() {
        BaseError error = assertThrows(BaseError.class, () -> new ProcessorsRegistry().getClassifier("unknown_classifier"));
        assertTrue(error.getMessage().toLowerCase().contains("not found")
                || error.getMessage().toLowerCase().contains("unknown"));
    }

    @Test
    void getValidatorUnknownRaises() {
        assertThrows(BaseError.class, () -> new ProcessorsRegistry().getValidator("unknown_validator"));
    }

    @Test
    void getSamplerUnknownRaises() {
        assertThrows(BaseError.class, () -> new ProcessorsRegistry().getSampler("unknown_sampler"));
    }

    private static RolloutWithReward rollout(Double reward) {
        RolloutWithReward rollout = new RolloutWithReward();
        rollout.setInputPromptIds(List.of(1, 2));
        rollout.setOutputResponseIds(List.of(3, 4));
        rollout.setReward(reward);
        rollout.setNTurns(1);
        return rollout;
    }

    private static List<RolloutWithReward> repeatRollout(Double reward, int count) {
        List<RolloutWithReward> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(rollout(reward));
        }
        return values;
    }
}
