/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.ProcessorsRegistry;
import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.RolloutProcessors;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RolloutClassifier, RolloutValidator, RolloutSampling, ProcessorsRegistry.
 * <p>
 * Mirrors Python's {@code test_processors.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/}.
 */
@DisplayName("Processors Tests")
class TestProcessors {

    @Nested
    @DisplayName("RolloutClassifier Tests")
    class TestRolloutClassifier {

        @Test
        void testClassifyPositiveNegativeSplit() {
            List<RolloutWithReward> mdpList = List.of(mdp(0.5), mdp(-0.1), mdp(1.0), mdp(0.0));

            RolloutProcessors.RolloutPair pair = RolloutProcessors.classifyRollouts(mdpList);

            assertThat(pair.positiveRollouts()).hasSize(2);
            assertThat(pair.negativeRollouts()).hasSize(2);
            assertThat(pair.positiveRollouts()).allMatch(mdp -> mdp.getReward() > 0);
            assertThat(pair.negativeRollouts()).allMatch(mdp -> mdp.getReward() <= 0);
        }

        @Test
        void testClassifyEmptyReturnsEmptyLists() {
            RolloutProcessors.RolloutPair pair = RolloutProcessors.classifyRollouts(List.of());

            assertThat(pair.positiveRollouts()).isEmpty();
            assertThat(pair.negativeRollouts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("RolloutValidator Tests")
    class TestRolloutValidator {

        @Test
        void testDefaultValidateStopTrueWhenTwoPosAndOneRewardOne() {
            assertThat(RolloutProcessors.defaultValidateStop(
                    List.of(mdp(0.5), mdp(1.0)),
                    List.of(mdp(-0.1)))).isTrue();
        }

        @Test
        void testDefaultValidateStopFalseWhenLessThanTwoPos() {
            assertThat(RolloutProcessors.defaultValidateStop(List.of(mdp(1.0)), List.of())).isFalse();
        }

        @Test
        void testDefaultValidateStopFalseWhenNoRewardOne() {
            assertThat(RolloutProcessors.defaultValidateStop(List.of(mdp(0.5), mdp(0.8)), List.of())).isFalse();
        }

        @Test
        void testValidateStopBalancedTrueWhenTargetsMet() {
            assertThat(RolloutProcessors.validateStopBalanced(
                    repeated(0.5, 4),
                    repeated(-0.1, 4),
                    8)).isTrue();
        }

        @Test
        void testValidateStopBalancedFalseWhenInsufficient() {
            assertThat(RolloutProcessors.validateStopBalanced(
                    repeated(0.5, 2),
                    repeated(-0.1, 2),
                    8)).isFalse();
        }

        @Test
        void testDefaultValidateStopEmptyListsFalse() {
            assertThat(RolloutProcessors.defaultValidateStop(List.of(), List.of())).isFalse();
        }
    }

    @Nested
    @DisplayName("RolloutSampling Tests")
    class TestRolloutSampling {

        @Test
        void testDefaultSamplingReturnsDeepcopyUnchangedCounts() {
            Map<String, List<RolloutWithReward>> posDict = Map.of(
                    "u1", List.of(mdp(0.5)),
                    "u2", List.of(mdp(0.6), mdp(0.7))
            );
            Map<String, List<RolloutWithReward>> negDict = Map.of(
                    "u1", List.of(mdp(-0.1)),
                    "u2", List.of(mdp(-0.2))
            );

            RolloutProcessors.SamplingResult result = RolloutProcessors.defaultSampling(posDict, negDict);

            assertThat(result.positiveRollouts()).hasSize(2);
            assertThat(result.negativeRollouts()).hasSize(2);
            assertThat(result.positiveRollouts().get("u1")).hasSize(1);
            assertThat(result.positiveRollouts().get("u2")).hasSize(2);
            assertThat(result.positiveRollouts()).isNotSameAs(posDict);
            assertThat(result.negativeRollouts()).isNotSameAs(negDict);
            assertThat(result.positiveRollouts().get("u1").get(0)).isNotSameAs(posDict.get("u1").get(0));
        }

        @Test
        void testDownsampleOneUidBalancedTargetTotal() {
            RolloutProcessors.RolloutPair selected = RolloutProcessors.downsampleOneUid(
                    repeated(0.5, 6),
                    repeated(-0.1, 6),
                    8);

            assertThat(selected.positiveRollouts().size() + selected.negativeRollouts().size()).isLessThanOrEqualTo(8);
            assertThat(selected.positiveRollouts()).hasSizeLessThanOrEqualTo(6);
            assertThat(selected.negativeRollouts()).hasSizeLessThanOrEqualTo(6);
        }

        @Test
        void testSamplingAdaPerUidDownsampled() {
            Map<String, List<RolloutWithReward>> posDict = Map.of(
                    "u1", repeated(0.5, 10),
                    "u2", repeated(0.6, 5)
            );
            Map<String, List<RolloutWithReward>> negDict = Map.of(
                    "u1", repeated(-0.1, 10),
                    "u2", repeated(-0.2, 5)
            );

            RolloutProcessors.SamplingResult result = RolloutProcessors.samplingAda(posDict, negDict, 8);

            assertThat(result.positiveRollouts().keySet()).containsExactlyInAnyOrder("u1", "u2");
            assertThat(result.negativeRollouts().keySet()).containsExactlyInAnyOrder("u1", "u2");
            for (String uid : List.of("u1", "u2")) {
                int total = result.positiveRollouts().get(uid).size() + result.negativeRollouts().get(uid).size();
                assertThat(total).isLessThanOrEqualTo(8);
            }
        }
    }

    @Nested
    @DisplayName("ProcessorsRegistry Tests")
    class TestProcessorsRegistry {

        @Test
        void testGetClassifierDefaultCallableMatchesStatic() {
            ProcessorsRegistry registry = new ProcessorsRegistry();
            RolloutProcessors.ClassifierProcessor fn = registry.getClassifier("default_classify_rollouts");

            RolloutProcessors.RolloutPair pair = fn.apply(List.of(mdp(0.5), mdp(-0.1)));

            assertThat(pair.positiveRollouts()).hasSize(1);
            assertThat(pair.negativeRollouts()).hasSize(1);
        }

        @Test
        void testGetValidatorValidateStopBalanced() {
            ProcessorsRegistry registry = new ProcessorsRegistry();
            RolloutProcessors.ValidatorProcessor fn = registry.getValidator("validate_stop_balanced");

            assertThat(fn.apply(repeated(0.5, 4), repeated(-0.1, 4))).isTrue();
        }

        @Test
        void testGetSamplerSamplingAda() {
            ProcessorsRegistry registry = new ProcessorsRegistry();
            RolloutProcessors.SamplerProcessor fn = registry.getSampler("sampling_ada");

            RolloutProcessors.SamplingResult result = fn.apply(
                    Map.of("u1", List.of(mdp(0.5))),
                    Map.of("u1", List.of(mdp(-0.1))));

            assertThat(result.positiveRollouts()).containsKey("u1");
            assertThat(result.negativeRollouts()).containsKey("u1");
        }

        @Test
        void testGetClassifierUnknownRaises() {
            ProcessorsRegistry registry = new ProcessorsRegistry();

            assertThatThrownBy(() -> registry.getClassifier("unknown_classifier"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void testGetValidatorUnknownRaises() {
            ProcessorsRegistry registry = new ProcessorsRegistry();

            assertThatThrownBy(() -> registry.getValidator("unknown_validator"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testGetSamplerUnknownRaises() {
            ProcessorsRegistry registry = new ProcessorsRegistry();

            assertThatThrownBy(() -> registry.getSampler("unknown_sampler"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static RolloutWithReward mdp(double reward) {
        RolloutWithReward rollout = new RolloutWithReward(List.of(1, 2), List.of(3, 4));
        rollout.setReward(reward);
        rollout.setNTurns(1);
        return rollout;
    }

    private static List<RolloutWithReward> repeated(double reward, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> mdp(reward))
                .toList();
    }
}
