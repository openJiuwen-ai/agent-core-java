/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts rollout sequences into padded token IDs, attention masks, rewards,
 * and metadata components required for RL batch construction.
 * <p>
 * Mirrors Python's {@code RLBatchBuilder} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.batch_builder}.
 */
public class RLBatchBuilder {

    private final int maxPromptLength;
    private final int padTokenId;
    private final int maxResponseLength;

    public RLBatchBuilder(int maxPromptLength, int padTokenId, int maxResponseLength) {
        this.maxPromptLength = maxPromptLength;
        this.padTokenId = padTokenId;
        this.maxResponseLength = maxResponseLength;
    }

    /**
     * Left-pads the input ID sequence to a fixed length.
     * 
     * @param ids Input ID sequence
     * @param maxLength Maximum length
     * @param padTokenId Padding token ID
     * @return Array of [padded_ids, attention_mask]
     */
    public static List<Integer>[] getLeftPaddedIdsAndAttentionMask(
            List<Integer> ids, int maxLength, int padTokenId) {

        List<Integer> safeIds = ids == null ? List.of() : ids;
        int seqLen = safeIds.size();

        if (seqLen >= maxLength) {
            List<Integer> trimmed = new ArrayList<>(safeIds.subList(seqLen - maxLength, seqLen));
            List<Integer> attentionMask = new ArrayList<>(Collections.nCopies(maxLength, 1));
            return new List[] { trimmed, attentionMask };
        }

        int padLen = maxLength - seqLen;
        List<Integer> paddedIds = new ArrayList<>(Collections.nCopies(padLen, padTokenId));
        paddedIds.addAll(safeIds);

        List<Integer> attentionMask = new ArrayList<>(Collections.nCopies(padLen, 0));
        attentionMask.addAll(Collections.nCopies(seqLen, 1));

        return new List[] { paddedIds, attentionMask };
    }

    /**
     * Right-pads the input ID sequence to a fixed length.
     * 
     * @param ids Input ID sequence
     * @param maxLength Maximum length
     * @param padTokenId Padding token ID
     * @return Array of [padded_ids, attention_mask]
     */
    public static List<Integer>[] getRightPaddedIdsAndAttentionMask(
            List<Integer> ids, int maxLength, int padTokenId) {

        List<Integer> safeIds = ids == null ? List.of() : ids;
        int seqLen = safeIds.size();

        if (seqLen >= maxLength) {
            List<Integer> trimmed = new ArrayList<>(safeIds.subList(0, maxLength));
            List<Integer> attentionMask = new ArrayList<>(Collections.nCopies(maxLength, 1));
            return new List[] { trimmed, attentionMask };
        }

        int padLen = maxLength - seqLen;
        List<Integer> paddedIds = new ArrayList<>(safeIds);
        paddedIds.addAll(Collections.nCopies(padLen, padTokenId));

        List<Integer> attentionMask = new ArrayList<>(Collections.nCopies(seqLen, 1));
        attentionMask.addAll(Collections.nCopies(padLen, 0));

        return new List[] { paddedIds, attentionMask };
    }

    /**
     * Generates token-level reward scores by assigning each transition's reward
     * to the final valid token position.
     *
     * @param attentionMask combined prompt/response attention mask
     * @param positionIds position ids derived from the attention mask
     * @param scores scalar reward per transition
     * @param responseLength number of response columns to retain
     * @return token-level scores over the response window
     */
    public static double[][] createTokenLevelScores(
            long[][] attentionMask,
            long[][] positionIds,
            double[] scores,
            int responseLength) {
        int nTransition = attentionMask == null ? 0 : attentionMask.length;
        double[][] result = new double[nTransition][responseLength];
        for (int i = 0; i < nTransition; i++) {
            long[] maskRow = attentionMask[i];
            long[] positionRow = positionIds[i];
            int seqLen = maskRow.length;
            int eosPosition = 0;
            long maxValue = Long.MIN_VALUE;
            for (int j = 0; j < seqLen; j++) {
                long value = positionRow[j] * maskRow[j];
                if (value > maxValue) {
                    maxValue = value;
                    eosPosition = j;
                }
            }

            int responseStart = Math.max(0, seqLen - responseLength);
            int responseIndex = eosPosition - responseStart;
            if (responseIndex >= 0 && responseIndex < responseLength) {
                result[i][responseIndex] = i < scores.length ? scores[i] : 0.0d;
            }
        }
        return result;
    }

    /**
     * Converts rollout sequences into padded token IDs, masks, rewards, and
     * metadata components required for RL batch construction.
     *
     * @param rolloutDict rollout samples grouped by origin task id
     * @param maxPromptLength prompt length limit
     * @param maxResponseLength response length limit
     * @return generated components
     */
    public Components generateComponents(
            Map<String, List<RolloutWithReward>> rolloutDict,
            int maxPromptLength,
            int maxResponseLength) {
        Components components = new Components();
        int truncationCount = 0;
        for (Map.Entry<String, List<RolloutWithReward>> entry : safeRolloutMap(rolloutDict).entrySet()) {
            String dataId = entry.getKey();
            for (RolloutWithReward rollout : safeRolloutList(entry.getValue())) {
                if (rollout == null) {
                    continue;
                }
                ComponentItem item = buildOneComponentItem(rollout, maxPromptLength, maxResponseLength);
                appendComponentItem(components, item, dataId);
                if (item.truncated()) {
                    truncationCount++;
                }
            }
        }
        components.truncationCount = truncationCount;
        return components;
    }

    /**
     * Builds the full Java-native RL batch from rollout samples.
     *
     * @param rolloutDict rollout samples grouped by origin task id
     * @return assembled batch and non-tensor metadata
     */
    public RlBatchResult generateRlBatch(Map<String, List<RolloutWithReward>> rolloutDict) {
        Components components = generateComponents(rolloutDict, maxPromptLength, maxResponseLength);
        if (components.inputIds.isEmpty()) {
            throw new IllegalStateException("0 samples collected after rollout");
        }
        return assembleBatch(components);
    }

    /**
     * Device-compatible overload mirroring Python's `generate_rl_batch(..., device)`.
     * Java keeps tensors as JVM-native arrays/lists, so device is intentionally ignored.
     *
     * @param rolloutDict rollout samples grouped by origin task id
     * @param device ignored device marker
     * @return assembled batch and non-tensor metadata
     */
    public RlBatchResult generateRlBatch(Map<String, List<RolloutWithReward>> rolloutDict, Object device) {
        return generateRlBatch(rolloutDict);
    }

    private ComponentItem buildOneComponentItem(
            RolloutWithReward rollout,
            int maxPromptLength,
            int maxResponseLength) {
        List<Integer> originalPromptIds = safeIntList(rollout.getInputPromptIds());
        List<Integer> originalResponseIds = safeIntList(rollout.getOutputResponseIds());

        List<Integer> promptIds = new ArrayList<>(originalPromptIds);
        List<Integer> responseIds = new ArrayList<>(originalResponseIds);
        boolean isDrop = promptIds.size() > maxPromptLength;
        if (isDrop) {
            promptIds = new ArrayList<>(promptIds.subList(0, maxPromptLength));
        }
        if (responseIds.size() > maxResponseLength) {
            responseIds = new ArrayList<>(responseIds.subList(0, maxResponseLength));
        }

        List<Integer>[] paddedPrompt = getLeftPaddedIdsAndAttentionMask(promptIds, maxPromptLength, padTokenId);
        List<Integer>[] paddedResponse = getRightPaddedIdsAndAttentionMask(responseIds, maxResponseLength, padTokenId);

        List<Integer> paddedLossMask = null;
        if (rollout.getLossMask() != null) {
            List<Integer> rawMask = new ArrayList<>(rollout.getLossMask());
            if (rawMask.size() > maxResponseLength) {
                rawMask = new ArrayList<>(rawMask.subList(0, maxResponseLength));
            }
            paddedLossMask = new ArrayList<>(rawMask);
            paddedLossMask.addAll(Collections.nCopies(maxResponseLength - rawMask.size(), 0));
        }

        return new ComponentItem(
                paddedPrompt[0],
                paddedPrompt[1],
                paddedResponse[0],
                paddedResponse[1],
                rollout.getReward() != null ? rollout.getReward() : 0.0d,
                rollout.getTurnId() != null ? rollout.getTurnId() : 0,
                isDrop,
                paddedLossMask,
                rollout.getNTurns() != null ? rollout.getNTurns() : 0,
                originalResponseIds.size() > maxResponseLength
        );
    }

    private static void appendComponentItem(Components components, ComponentItem item, String dataId) {
        components.inputIds.add(item.inputIds());
        components.inputAttentionMask.add(item.inputAttentionMask());
        components.responseIds.add(item.responseIds());
        components.responseAttentionMask.add(item.responseAttentionMask());
        components.rewards.add(item.reward());
        components.turnIndices.add(item.turnIndex());
        components.isDrop.add(item.isDrop());
        components.dataIds.add(dataId);
        components.lossMasks.add(item.lossMask());
        components.nTurnsList.add(item.nTurns());
    }

    private RlBatchResult assembleBatch(Components components) {
        List<List<Integer>> prompts = deepCopyIntLists(components.inputIds);
        List<List<Integer>> responses = deepCopyIntLists(components.responseIds);
        List<List<Integer>> inputIds = new ArrayList<>();
        List<List<Integer>> attentionMask = new ArrayList<>();
        List<List<Integer>> positionIds = new ArrayList<>();
        long[][] maskArray = new long[components.inputIds.size()][maxPromptLength + maxResponseLength];
        long[][] positionArray = new long[components.inputIds.size()][maxPromptLength + maxResponseLength];
        double[] scoreArray = new double[components.rewards.size()];

        for (int i = 0; i < components.inputIds.size(); i++) {
            List<Integer> combinedIds = new ArrayList<>(components.inputIds.get(i));
            combinedIds.addAll(components.responseIds.get(i));
            inputIds.add(combinedIds);

            List<Integer> combinedMask = new ArrayList<>(components.inputAttentionMask.get(i));
            combinedMask.addAll(components.responseAttentionMask.get(i));
            attentionMask.add(combinedMask);

            List<Integer> position = new ArrayList<>(combinedMask.size());
            int cumulative = 0;
            for (int j = 0; j < combinedMask.size(); j++) {
                cumulative += combinedMask.get(j);
                int positionId = Math.max(cumulative - 1, 0);
                position.add(positionId);
                maskArray[i][j] = combinedMask.get(j);
                positionArray[i][j] = positionId;
            }
            positionIds.add(position);
            scoreArray[i] = components.rewards.get(i);
        }

        double[][] tokenLevelScores = createTokenLevelScores(
                maskArray,
                positionArray,
                scoreArray,
                maxResponseLength
        );
        List<List<Integer>> actorLossMask = buildActorLossMask(components.lossMasks, maxResponseLength);

        return new RlBatchResult(
                prompts,
                responses,
                inputIds,
                attentionMask,
                positionIds,
                new ArrayList<>(components.rewards),
                new ArrayList<>(components.isDrop),
                tokenLevelScores,
                new ArrayList<>(components.dataIds),
                new ArrayList<>(components.turnIndices),
                new ArrayList<>(components.nTurnsList),
                actorLossMask,
                components.truncationCount
        );
    }

    private static List<List<Integer>> buildActorLossMask(List<List<Integer>> lossMasks, int responseLength) {
        boolean hasLossMask = false;
        for (List<Integer> mask : lossMasks) {
            if (mask != null) {
                hasLossMask = true;
                break;
            }
        }
        if (!hasLossMask) {
            return List.of();
        }
        List<List<Integer>> filled = new ArrayList<>();
        for (List<Integer> mask : lossMasks) {
            if (mask == null) {
                filled.add(new ArrayList<>(Collections.nCopies(responseLength, 1)));
            } else {
                filled.add(new ArrayList<>(mask));
            }
        }
        return filled;
    }

    private static Map<String, List<RolloutWithReward>> safeRolloutMap(
            Map<String, List<RolloutWithReward>> rolloutDict) {
        return rolloutDict == null ? Map.of() : rolloutDict;
    }

    private static List<RolloutWithReward> safeRolloutList(List<RolloutWithReward> rollouts) {
        return rollouts == null ? List.of() : rollouts;
    }

    private static List<Integer> safeIntList(List<Integer> ids) {
        return ids == null ? List.of() : ids;
    }

    private static List<List<Integer>> deepCopyIntLists(List<List<Integer>> input) {
        List<List<Integer>> copy = new ArrayList<>();
        for (List<Integer> item : input) {
            copy.add(new ArrayList<>(item));
        }
        return copy;
    }

    public int getMaxPromptLength() { return maxPromptLength; }
    public int getPadTokenId() { return padTokenId; }
    public int getMaxResponseLength() { return maxResponseLength; }

    private record ComponentItem(
            List<Integer> inputIds,
            List<Integer> inputAttentionMask,
            List<Integer> responseIds,
            List<Integer> responseAttentionMask,
            double reward,
            int turnIndex,
            boolean isDrop,
            List<Integer> lossMask,
            int nTurns,
            boolean truncated) {
    }

    public static final class Components {
        public final List<List<Integer>> inputIds = new ArrayList<>();
        public final List<List<Integer>> inputAttentionMask = new ArrayList<>();
        public final List<List<Integer>> responseIds = new ArrayList<>();
        public final List<List<Integer>> responseAttentionMask = new ArrayList<>();
        public final List<Double> rewards = new ArrayList<>();
        public final List<Integer> turnIndices = new ArrayList<>();
        public final List<Boolean> isDrop = new ArrayList<>();
        public final List<String> dataIds = new ArrayList<>();
        public final List<List<Integer>> lossMasks = new ArrayList<>();
        public final List<Integer> nTurnsList = new ArrayList<>();
        public int truncationCount;
    }

    public static final class RlBatchResult {
        public final List<List<Integer>> prompts;
        public final List<List<Integer>> responses;
        public final List<List<Integer>> inputIds;
        public final List<List<Integer>> attentionMask;
        public final List<List<Integer>> positionIds;
        public final List<Double> rewards;
        public final List<Boolean> isDropMask;
        public final double[][] tokenLevelScores;
        public final List<String> dataIdList;
        public final List<Integer> turnIndexList;
        public final List<Integer> nTurnsList;
        public final List<List<Integer>> actorLossMask;
        public final int truncationCount;
        public final int batchSize;
        public final Map<String, Object> nonTensorBatch;

        private RlBatchResult(
                List<List<Integer>> prompts,
                List<List<Integer>> responses,
                List<List<Integer>> inputIds,
                List<List<Integer>> attentionMask,
                List<List<Integer>> positionIds,
                List<Double> rewards,
                List<Boolean> isDropMask,
                double[][] tokenLevelScores,
                List<String> dataIdList,
                List<Integer> turnIndexList,
                List<Integer> nTurnsList,
                List<List<Integer>> actorLossMask,
                int truncationCount) {
            this.prompts = prompts;
            this.responses = responses;
            this.inputIds = inputIds;
            this.attentionMask = attentionMask;
            this.positionIds = positionIds;
            this.rewards = rewards;
            this.isDropMask = isDropMask;
            this.tokenLevelScores = tokenLevelScores;
            this.dataIdList = dataIdList;
            this.turnIndexList = turnIndexList;
            this.nTurnsList = nTurnsList;
            this.actorLossMask = actorLossMask;
            this.truncationCount = truncationCount;
            this.batchSize = inputIds.size();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("data_id_list", dataIdList);
            metadata.put("turn_index_list", turnIndexList);
            metadata.put("n_turns_list", nTurnsList);
            this.nonTensorBatch = Map.copyOf(metadata);
        }

        public int getBatchSize() {
            return batchSize;
        }
    }
}
