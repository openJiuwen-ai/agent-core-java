/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.RLBatchBuilder;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RLBatchBuilder: padding, token_level_scores, generate_components,
 * assemble, generate_rl_batch.
 * <p>
 * Mirrors Python's
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_batch_builder.py}.
 */
class TestBatchBuilder {

    private RLBatchBuilder builder;

    @BeforeEach
    void createBuilder() {
        builder = new RLBatchBuilder(16, 0, 8);
    }

    @Nested
    @DisplayName("TestPaddingAndMask")
    class TestPaddingAndMask {

        @Test
        void testLeftPadLengthAndMask() {
            List<Integer>[] result = RLBatchBuilder.getLeftPaddedIdsAndAttentionMask(List.of(1, 2, 3), 8, 0);

            assertThat(result[0]).hasSize(8);
            assertThat(result[0].subList(0, 5)).containsExactly(0, 0, 0, 0, 0);
            assertThat(result[0].subList(5, 8)).containsExactly(1, 2, 3);
            assertThat(result[1].subList(0, 5)).containsExactly(0, 0, 0, 0, 0);
            assertThat(result[1].subList(5, 8)).containsExactly(1, 1, 1);
        }

        @Test
        void testLeftPadTruncateWhenOverMax() {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                ids.add(i);
            }
            List<Integer>[] result = RLBatchBuilder.getLeftPaddedIdsAndAttentionMask(ids, 8, 0);

            assertThat(result[0]).hasSize(8);
            assertThat(result[0]).containsExactlyElementsOf(ids.subList(12, 20));
            assertThat(result[1]).containsExactly(1, 1, 1, 1, 1, 1, 1, 1);
        }

        @Test
        void testRightPadLengthAndMask() {
            List<Integer>[] result = RLBatchBuilder.getRightPaddedIdsAndAttentionMask(List.of(1, 2, 3), 8, 0);

            assertThat(result[0]).hasSize(8);
            assertThat(result[0].subList(0, 3)).containsExactly(1, 2, 3);
            assertThat(result[0].subList(3, 8)).containsExactly(0, 0, 0, 0, 0);
            assertThat(result[1].subList(0, 3)).containsExactly(1, 1, 1);
            assertThat(result[1].subList(3, 8)).containsExactly(0, 0, 0, 0, 0);
        }
    }

    @Nested
    @DisplayName("TestCreateTokenLevelScores")
    class TestCreateTokenLevelScores {

        @Test
        void testTokenLevelScoresShapeAndRewardAtEos() {
            long[][] attn = {
                    {0, 0, 1, 1, 1, 1},
                    {0, 1, 1, 1, 1, 0}
            };
            long[][] positionIds = {
                    {0, 0, 1, 2, 3, 4},
                    {0, 1, 2, 3, 4, 0}
            };
            double[][] tokenScores = RLBatchBuilder.createTokenLevelScores(
                    attn, positionIds, new double[] {0.5, -0.2}, 4);

            assertThat(tokenScores).hasDimensions(2, 4);
            assertThat(Arrays.stream(tokenScores[0]).sum())
                    .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-2));
            assertThat(Arrays.stream(tokenScores[1]).sum())
                    .isCloseTo(-0.2, org.assertj.core.data.Offset.offset(1e-2));
        }
    }

    @Nested
    @DisplayName("TestGenerateRlBatch")
    class TestGenerateRlBatch {

        @Test
        void testGenerateRlBatchSingleEntryReturnsBatchAndMeta() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("uid1", Collections.singletonList(rollout(4, 3, 0.5)));

            RLBatchBuilder.RlBatchResult batch = builder.generateRlBatch(rolloutDict, "cpu");

            assertThat(batch.inputIds).hasSize(1);
            assertThat(batch.prompts).hasSize(1);
            assertThat(batch.responses).hasSize(1);
            assertThat(batch.prompts.get(0)).hasSize(16);
            assertThat(batch.dataIdList).containsExactly("uid1");
            assertThat(batch.nonTensorBatch).containsKey("data_id_list");
        }

        @Test
        void testGenerateRlBatchEmptyDictThrows() {
            assertThatThrownBy(() -> builder.generateRlBatch(Map.of(), "cpu"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("0 samples collected after rollout");
        }

        @Test
        void testGenerateComponentsTruncationAndPadding() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(20, 10, 0.5)));

            RLBatchBuilder.Components comp = builder.generateComponents(rolloutDict, 16, 8);

            assertThat(comp.inputIds).hasSize(1);
            assertThat(comp.inputIds.get(0)).hasSize(16);
            assertThat(comp.responseIds.get(0)).hasSize(8);
        }

        @Test
        void testGenerateComponentsMultipleEntries() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(4, 3, 0.5)));
            rolloutDict.put("u2", Arrays.asList(
                    rollout(3, 2, 0.8),
                    rollout(4, 3, -0.1)
            ));

            RLBatchBuilder.Components comp = builder.generateComponents(rolloutDict, 16, 8);

            assertThat(comp.inputIds).hasSize(3);
            assertThat(comp.dataIds).containsExactly("u1", "u2", "u2");
            assertThat(comp.rewards).containsExactly(0.5, 0.8, -0.1);
        }

        @Test
        void testGenerateComponentsWithLossMask() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(4, 3, 0.5, List.of(1, 0, 1))));

            RLBatchBuilder.Components comp = builder.generateComponents(rolloutDict, 16, 8);

            assertThat(comp.lossMasks).hasSize(1);
            assertThat(comp.lossMasks.get(0).subList(0, 3)).containsExactly(1, 0, 1);
            assertThat(comp.lossMasks.get(0).subList(3, 8)).containsExactly(0, 0, 0, 0, 0);
        }

        @Test
        void testGenerateComponentsTruncationCount() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(20, 10, 0.5)));

            RLBatchBuilder.Components comp = builder.generateComponents(rolloutDict, 16, 8);

            assertThat(comp.truncationCount).isEqualTo(1);
        }
    }

    private static RolloutWithReward rollout(int pidLen, int ridLen, double reward) {
        return rollout(pidLen, ridLen, reward, null);
    }

    private static RolloutWithReward rollout(int pidLen, int ridLen, double reward, List<Integer> lossMask) {
        RolloutWithReward rollout = new RolloutWithReward(
                0,
                null,
                null,
                promptIds(pidLen),
                responseIds(ridLen),
                reward,
                1
        );
        rollout.setLossMask(lossMask);
        return rollout;
    }

    private static List<Integer> promptIds(int length) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            ids.add(i + 1);
        }
        return ids;
    }

    private static List<Integer> responseIds(int length) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            ids.add((i + 1) * 10);
        }
        return ids;
    }
}
