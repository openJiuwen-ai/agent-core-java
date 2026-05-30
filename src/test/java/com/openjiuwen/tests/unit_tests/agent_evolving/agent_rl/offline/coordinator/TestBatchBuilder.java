/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RLBatchBuilder: padding, token_level_scores, generate_components, assemble, generate_rl_batch.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_batch_builder.py}.
 */
class TestBatchBuilder {

    private RLBatchBuilder builder;

    @BeforeEach
    void createBuilder() {
        builder = new RLBatchBuilder(16, 0, 8);
    }

    // -- helper factories ---------------------------------------------------

    private static RolloutWithReward rollout(int pidLen, int ridLen, double reward, List<Integer> lossMask) {
        RolloutWithReward r = new RolloutWithReward();
        r.inputPromptIds = promptIds(pidLen);
        r.outputResponseIds = responseIds(ridLen);
        r.reward = reward;
        r.nTurns = 1;
        r.lossMask = lossMask;
        return r;
    }

    private static RolloutWithReward rollout(int pidLen, int ridLen, double reward) {
        return rollout(pidLen, ridLen, reward, null);
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

    // -- TestPaddingAndMask -------------------------------------------------

    @Nested
    @DisplayName("TestPaddingAndMask")
    class TestPaddingAndMask {

        @Test
        @DisplayName("test_left_pad_length_and_mask")
        void testLeftPadLengthAndMask() {
            List<Integer> ids = Arrays.asList(1, 2, 3);
            RLBatchBuilder.PadResult result = builder.getLeftPaddedIdsAndAttentionMask(ids, 8, 0);

            assertThat(result.padded).hasSize(8);
            assertThat(result.padded.subList(0, 5)).containsExactly(0, 0, 0, 0, 0);
            assertThat(result.padded.subList(5, 8)).containsExactly(1, 2, 3);
            assertThat(result.mask.subList(0, 5)).containsExactly(0, 0, 0, 0, 0);
            assertThat(result.mask.subList(5, 8)).containsExactly(1, 1, 1);
        }

        @Test
        @DisplayName("test_left_pad_truncate_when_over_max")
        void testLeftPadTruncateWhenOverMax() {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                ids.add(i);
            }
            List<Integer> expectedLast8 = ids.subList(ids.size() - 8, ids.size());

            RLBatchBuilder.PadResult result = builder.getLeftPaddedIdsAndAttentionMask(ids, 8, 0);

            assertThat(result.padded).hasSize(8);
            assertThat(result.padded).containsExactlyElementsOf(expectedLast8);
            assertThat(result.mask).containsExactly(1, 1, 1, 1, 1, 1, 1, 1);
        }

        @Test
        @DisplayName("test_right_pad_length_and_mask")
        void testRightPadLengthAndMask() {
            List<Integer> ids = Arrays.asList(1, 2, 3);
            RLBatchBuilder.PadResult result = builder.getRightPaddedIdsAndAttentionMask(ids, 8, 0);

            assertThat(result.padded).hasSize(8);
            assertThat(result.padded.subList(0, 3)).containsExactly(1, 2, 3);
            assertThat(result.padded.subList(3, 8)).containsExactly(0, 0, 0, 0, 0);
            assertThat(result.mask.subList(0, 3)).containsExactly(1, 1, 1);
            assertThat(result.mask.subList(3, 8)).containsExactly(0, 0, 0, 0, 0);
        }
    }

    // -- TestCreateTokenLevelScores -----------------------------------------

    @Nested
    @DisplayName("TestCreateTokenLevelScores")
    class TestCreateTokenLevelScores {

        @Test
        @DisplayName("test_token_level_scores_shape_and_reward_at_eos")
        void testTokenLevelScoresShapeAndRewardAtEos() {
            int nTransition = 2;
            int respLen = 4;

            long[][] attn = {
                    {0, 0, 1, 1, 1, 1},
                    {0, 1, 1, 1, 1, 0}
            };
            long[][] positionIds = {
                    {0, 0, 1, 2, 3, 4},
                    {0, 1, 2, 3, 4, 0}
            };
            double[] scores = {0.5, -0.2};

            double[][] tokenScores = RLBatchBuilder.createTokenLevelScores(
                    attn, positionIds, scores, respLen);

            assertThat(tokenScores).hasDimensions(nTransition, respLen);

            double sum0 = 0;
            for (int j = 0; j < respLen; j++) {
                sum0 += tokenScores[0][j];
            }
            assertThat(sum0).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-2));

            double sum1 = 0;
            for (int j = 0; j < respLen; j++) {
                sum1 += tokenScores[1][j];
            }
            assertThat(sum1).isCloseTo(-0.2, org.assertj.core.data.Offset.offset(1e-2));
        }
    }

    // -- TestGenerateRlBatch ------------------------------------------------

    @Nested
    @DisplayName("TestGenerateRlBatch")
    class TestGenerateRlBatch {

        @Test
        @DisplayName("test_generate_rl_batch_single_entry_returns_batch_and_meta")
        void testGenerateRlBatchSingleEntryReturnsBatchAndMeta() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("uid1", Collections.singletonList(rollout(4, 3, 0.5)));

            RLBatchBuilder.RlBatchResult batch = builder.generateRlBatch(rolloutDict);

            assertThat(batch.inputIds).isNotEmpty();
            assertThat(batch.inputIds).hasSize(1);
            assertThat(batch.prompts).isNotEmpty();
            assertThat(batch.responses).isNotEmpty();
            assertThat(batch.prompts.get(0)).hasSize(16);
            assertThat(batch.dataIdList).containsExactly("uid1");
        }

        @Test
        @DisplayName("test_generate_rl_batch_empty_dict_throws")
        void testGenerateRlBatchEmptyDictThrows() {
            Map<String, List<RolloutWithReward>> empty = new LinkedHashMap<>();
            assertThatThrownBy(() -> builder.generateRlBatch(empty))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("test_generate_components_truncation_and_padding")
        void testGenerateComponentsTruncationAndPadding() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(20, 10, 0.5)));

            RLBatchBuilder.Components comp = builder.generateComponents(
                    rolloutDict, 16, 8);

            assertThat(comp.inputIds).hasSize(1);
            assertThat(comp.inputIds.get(0)).hasSize(16);
            assertThat(comp.responseIds.get(0)).hasSize(8);
        }

        @Test
        @DisplayName("test_generate_components_multiple_entries")
        void testGenerateComponentsMultipleEntries() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(4, 3, 0.5)));
            rolloutDict.put("u2", Arrays.asList(
                    rollout(3, 2, 0.8),
                    rollout(4, 3, -0.1)
            ));

            RLBatchBuilder.Components comp = builder.generateComponents(
                    rolloutDict, 16, 8);

            assertThat(comp.inputIds).hasSize(3);
            assertThat(comp.dataIds).containsExactly("u1", "u2", "u2");
            assertThat(comp.rewards).containsExactly(0.5, 0.8, -0.1);
        }

        @Test
        @DisplayName("test_generate_components_with_loss_mask")
        void testGenerateComponentsWithLossMask() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(
                    rollout(4, 3, 0.5, Arrays.asList(1, 0, 1))
            ));

            RLBatchBuilder.Components comp = builder.generateComponents(
                    rolloutDict, 16, 8);

            assertThat(comp.lossMasks).hasSize(1);
            List<Integer> mask = comp.lossMasks.get(0);
            assertThat(mask).isNotNull();
            assertThat(mask.subList(0, 3)).containsExactly(1, 0, 1);
            assertThat(mask.subList(3, 8)).containsExactly(0, 0, 0, 0, 0);
        }

        @Test
        @DisplayName("test_generate_components_truncation_count")
        void testGenerateComponentsTruncationCount() {
            Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
            rolloutDict.put("u1", Collections.singletonList(rollout(20, 10, 0.5)));

            RLBatchBuilder.Components comp = builder.generateComponents(
                    rolloutDict, 16, 8);

            assertThat(comp.truncationCount).isEqualTo(1);
        }
    }

    // -- inner data model classes -------------------------------------------

    static class RolloutWithReward {
        Integer turnId;
        String taskId;
        String rolloutId;
        List<Integer> inputPromptIds;
        List<Integer> outputResponseIds;
        Double reward;
        Integer nTurns;
        List<Integer> lossMask;
    }

    static class RLBatchBuilder {

        private final int maxPromptLength;
        private final int padTokenId;
        private final int maxResponseLength;

        RLBatchBuilder(int maxPromptLength, int padTokenId, int maxResponseLength) {
            this.maxPromptLength = maxPromptLength;
            this.padTokenId = padTokenId;
            this.maxResponseLength = maxResponseLength;
        }

        static class PadResult {
            final List<Integer> padded;
            final List<Integer> mask;

            PadResult(List<Integer> padded, List<Integer> mask) {
                this.padded = padded;
                this.mask = mask;
            }
        }

        PadResult getLeftPaddedIdsAndAttentionMask(List<Integer> ids, int maxLength, int padTokenId) {
            int seqLen = ids.size();

            if (seqLen >= maxLength) {
                List<Integer> trimmed = ids.subList(seqLen - maxLength, seqLen);
                List<Integer> attnMask = new ArrayList<>(Collections.nCopies(maxLength, 1));
                return new PadResult(new ArrayList<>(trimmed), attnMask);
            }

            int padLen = maxLength - seqLen;
            List<Integer> paddedIds = new ArrayList<>(Collections.nCopies(padLen, padTokenId));
            paddedIds.addAll(ids);
            List<Integer> attnMask = new ArrayList<>(Collections.nCopies(padLen, 0));
            attnMask.addAll(Collections.nCopies(seqLen, 1));
            return new PadResult(paddedIds, attnMask);
        }

        PadResult getRightPaddedIdsAndAttentionMask(List<Integer> ids, int maxLength, int padTokenId) {
            int seqLen = ids.size();

            if (seqLen >= maxLength) {
                List<Integer> trimmed = new ArrayList<>(ids.subList(0, maxLength));
                List<Integer> attnMask = new ArrayList<>(Collections.nCopies(maxLength, 1));
                return new PadResult(trimmed, attnMask);
            }

            int padLen = maxLength - seqLen;
            List<Integer> paddedIds = new ArrayList<>(ids);
            paddedIds.addAll(Collections.nCopies(padLen, padTokenId));
            List<Integer> attnMask = new ArrayList<>(Collections.nCopies(seqLen, 1));
            attnMask.addAll(Collections.nCopies(padLen, 0));
            return new PadResult(paddedIds, attnMask);
        }

        static double[][] createTokenLevelScores(
                long[][] attentionMask, long[][] positionIds,
                double[] scores, int responseLength) {
            int nTransition = attentionMask.length;
            int seqLen = attentionMask[0].length;

            double[][] tokenScores = new double[nTransition][seqLen];

            for (int i = 0; i < nTransition; i++) {
                long eosPos = 0;
                long maxVal = -1;
                for (int j = 0; j < seqLen; j++) {
                    long val = positionIds[i][j] * attentionMask[i][j];
                    if (val > maxVal) {
                        maxVal = val;
                        eosPos = j;
                    }
                }
                tokenScores[i][(int) eosPos] = scores[i];
            }

            int start = seqLen - responseLength;
            double[][] result = new double[nTransition][responseLength];
            for (int i = 0; i < nTransition; i++) {
                System.arraycopy(tokenScores[i], start, result[i], 0, responseLength);
            }
            return result;
        }

        static class Components {
            final List<List<Integer>> inputIds = new ArrayList<>();
            final List<List<Integer>> inputAttentionMask = new ArrayList<>();
            final List<List<Integer>> responseIds = new ArrayList<>();
            final List<List<Integer>> responseAttentionMask = new ArrayList<>();
            final List<Double> rewards = new ArrayList<>();
            final List<Integer> turnIndices = new ArrayList<>();
            final List<Boolean> isDrop = new ArrayList<>();
            final List<String> dataIds = new ArrayList<>();
            final List<List<Integer>> lossMasks = new ArrayList<>();
            final List<Integer> nTurnsList = new ArrayList<>();
            int truncationCount;
        }

        Components generateComponents(
                Map<String, List<RolloutWithReward>> rolloutDict,
                int maxPromptLen, int maxResponseLen) {
            Components components = new Components();
            int truncationCount = 0;

            for (Map.Entry<String, List<RolloutWithReward>> entry : rolloutDict.entrySet()) {
                String dataId = entry.getKey();
                for (RolloutWithReward rollout : entry.getValue()) {
                    List<Integer> promptIds = new ArrayList<>(rollout.inputPromptIds);
                    List<Integer> respIds = new ArrayList<>(rollout.outputResponseIds);

                    boolean isDrop = promptIds.size() > maxPromptLen;
                    if (isDrop) {
                        promptIds = new ArrayList<>(promptIds.subList(0, maxPromptLen));
                    }
                    if (respIds.size() > maxResponseLen) {
                        respIds = new ArrayList<>(respIds.subList(0, maxResponseLen));
                    }

                    PadResult paddedPrompt = getLeftPaddedIdsAndAttentionMask(
                            promptIds, maxPromptLen, padTokenId);
                    PadResult paddedResponse = getRightPaddedIdsAndAttentionMask(
                            respIds, maxResponseLen, padTokenId);

                    List<Integer> paddedLossMask = null;
                    if (rollout.lossMask != null) {
                        List<Integer> rawMask = new ArrayList<>(rollout.lossMask);
                        if (rawMask.size() > maxResponseLen) {
                            rawMask = new ArrayList<>(rawMask.subList(0, maxResponseLen));
                        }
                        int padLen = maxResponseLen - rawMask.size();
                        paddedLossMask = new ArrayList<>(rawMask);
                        paddedLossMask.addAll(Collections.nCopies(padLen, 0));
                    }

                    components.inputIds.add(paddedPrompt.padded);
                    components.inputAttentionMask.add(paddedPrompt.mask);
                    components.responseIds.add(paddedResponse.padded);
                    components.responseAttentionMask.add(paddedResponse.mask);
                    components.rewards.add(rollout.reward != null ? rollout.reward : 0.0);
                    components.turnIndices.add(rollout.turnId != null ? rollout.turnId : 0);
                    components.isDrop.add(isDrop);
                    components.dataIds.add(dataId);
                    components.lossMasks.add(paddedLossMask);
                    components.nTurnsList.add(rollout.nTurns != null ? rollout.nTurns : 0);

                    if (rollout.outputResponseIds.size() > maxResponseLen) {
                        truncationCount++;
                    }
                }
            }
            components.truncationCount = truncationCount;
            return components;
        }

        static class RlBatchResult {
            final List<List<Integer>> prompts;
            final List<List<Integer>> responses;
            final List<List<Integer>> inputIds;
            final List<List<Integer>> attentionMask;
            final List<List<Integer>> positionIds;
            final List<Double> rewards;
            final List<String> dataIdList;
            final List<Integer> turnIndexList;
            final List<Integer> nTurnsList;

            RlBatchResult(List<List<Integer>> prompts, List<List<Integer>> responses,
                          List<List<Integer>> inputIds, List<List<Integer>> attentionMask,
                          List<List<Integer>> positionIds, List<Double> rewards,
                          List<String> dataIdList, List<Integer> turnIndexList,
                          List<Integer> nTurnsList) {
                this.prompts = prompts;
                this.responses = responses;
                this.inputIds = inputIds;
                this.attentionMask = attentionMask;
                this.positionIds = positionIds;
                this.rewards = rewards;
                this.dataIdList = dataIdList;
                this.turnIndexList = turnIndexList;
                this.nTurnsList = nTurnsList;
            }
        }

        RlBatchResult generateRlBatch(Map<String, List<RolloutWithReward>> rolloutDict) {
            Components components = generateComponents(
                    rolloutDict, maxPromptLength, maxResponseLength);

            if (components.inputIds.isEmpty()) {
                throw new IllegalStateException("0 samples collected after rollout");
            }

            List<List<Integer>> prompts = components.inputIds;
            List<List<Integer>> responses = components.responseIds;

            List<List<Integer>> combinedIds = new ArrayList<>();
            List<List<Integer>> combinedMask = new ArrayList<>();
            for (int i = 0; i < components.inputIds.size(); i++) {
                List<Integer> ids = new ArrayList<>(components.inputIds.get(i));
                ids.addAll(components.responseIds.get(i));
                combinedIds.add(ids);

                List<Integer> mask = new ArrayList<>(components.inputAttentionMask.get(i));
                mask.addAll(components.responseAttentionMask.get(i));
                combinedMask.add(mask);
            }

            List<List<Integer>> posIds = new ArrayList<>();
            for (List<Integer> mask : combinedMask) {
                List<Integer> pos = new ArrayList<>();
                long cumSum = 0;
                for (int m : mask) {
                    cumSum += m;
                    pos.add((int) Math.max(cumSum - 1, 0));
                }
                posIds.add(pos);
            }

            return new RlBatchResult(
                    prompts, responses, combinedIds, combinedMask, posIds,
                    components.rewards, components.dataIds,
                    components.turnIndices, components.nTurnsList);
        }
    }
}
