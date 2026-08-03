/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.store.TrainingDiagnostics;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Converts rollout sequences into padded token ids, masks, scores, and metadata
 * required for offline RL batch assembly.
 *
 * <p>Mirrors Python's {@code RLBatchBuilder} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.</p>
 */
public class RLBatchBuilder {

    private static final Logger LOGGER = Logger.getLogger(RLBatchBuilder.class.getName());

    private final int maxPromptLength;
    private final int padTokenId;
    private final int maxResponseLength;

    public RLBatchBuilder(int maxPromptLength, int padTokenId, int maxResponseLength) {
        this.maxPromptLength = maxPromptLength;
        this.padTokenId = padTokenId;
        this.maxResponseLength = maxResponseLength;
    }

    public int getMaxPromptLength() {
        return maxPromptLength;
    }

    public int getPadTokenId() {
        return padTokenId;
    }

    public int getMaxResponseLength() {
        return maxResponseLength;
    }

    public static PaddingResult getLeftPaddedIdsAndAttentionMask(List<Integer> ids, int maxLength, int padTokenId) {
        List<Integer> values = safeInts(ids);
        int sequenceLength = values.size();
        if (sequenceLength >= maxLength) {
            List<Integer> trimmed = new ArrayList<>(values.subList(sequenceLength - maxLength, sequenceLength));
            return new PaddingResult(trimmed, filledList(maxLength, 1));
        }

        int padLength = maxLength - sequenceLength;
        List<Integer> paddedIds = new ArrayList<>(maxLength);
        List<Integer> attentionMask = new ArrayList<>(maxLength);
        paddedIds.addAll(filledList(padLength, padTokenId));
        paddedIds.addAll(values);
        attentionMask.addAll(filledList(padLength, 0));
        attentionMask.addAll(filledList(sequenceLength, 1));
        return new PaddingResult(paddedIds, attentionMask);
    }

    public static PaddingResult getRightPaddedIdsAndAttentionMask(List<Integer> ids, int maxLength, int padTokenId) {
        List<Integer> values = safeInts(ids);
        int sequenceLength = values.size();
        if (sequenceLength >= maxLength) {
            List<Integer> trimmed = new ArrayList<>(values.subList(0, maxLength));
            return new PaddingResult(trimmed, filledList(maxLength, 1));
        }

        int padLength = maxLength - sequenceLength;
        List<Integer> paddedIds = new ArrayList<>(maxLength);
        List<Integer> attentionMask = new ArrayList<>(maxLength);
        paddedIds.addAll(values);
        paddedIds.addAll(filledList(padLength, padTokenId));
        attentionMask.addAll(filledList(sequenceLength, 1));
        attentionMask.addAll(filledList(padLength, 0));
        return new PaddingResult(paddedIds, attentionMask);
    }

    public static double[][] createTokenLevelScores(
            long[][] attentionMask,
            long[][] positionIds,
            double[] scores,
            int responseLength
    ) {
        int transitionCount = attentionMask.length;
        double[][] tokenScores = new double[transitionCount][attentionMask.length == 0 ? 0 : attentionMask[0].length];

        for (int rowIndex = 0; rowIndex < transitionCount; rowIndex++) {
            long[] attentionRow = attentionMask[rowIndex];
            long[] positionRow = positionIds[rowIndex];
            int eosPosition = 0;
            long bestValue = Long.MIN_VALUE;
            for (int columnIndex = 0; columnIndex < Math.min(attentionRow.length, positionRow.length); columnIndex++) {
                long candidate = attentionRow[columnIndex] == 0 ? 0L : positionRow[columnIndex] * attentionRow[columnIndex];
                if (candidate > bestValue) {
                    bestValue = candidate;
                    eosPosition = columnIndex;
                }
            }
            tokenScores[rowIndex][eosPosition] = rowIndex < scores.length ? scores[rowIndex] : 0.0d;
        }

        double[][] responseOnly = new double[transitionCount][responseLength];
        for (int rowIndex = 0; rowIndex < transitionCount; rowIndex++) {
            int sourceLength = tokenScores[rowIndex].length;
            int startIndex = Math.max(0, sourceLength - responseLength);
            for (int columnIndex = 0; columnIndex < responseLength; columnIndex++) {
                int sourceIndex = startIndex + columnIndex;
                if (sourceIndex < sourceLength) {
                    responseOnly[rowIndex][columnIndex] = tokenScores[rowIndex][sourceIndex];
                }
            }
        }
        return responseOnly;
    }

    public TensorBatch assembleTensorBatch(Components components, Object device) {
        Objects.requireNonNull(components, "components");
        int transitionCount = components.inputIds().size();

        long[][] inputIds = toLongMatrix(components.inputIds());
        long[][] inputMask = toLongMatrix(components.inputAttentionMask());
        long[][] responseIds = toLongMatrix(components.responseIds());
        long[][] responseMask = toLongMatrix(components.responseAttentionMask());

        long[][] sequenceIds = concatRows(inputIds, responseIds);
        long[][] attentionMask = concatRows(inputMask, responseMask);
        long[][] positionIds = createPositionIds(attentionMask);

        double[] scores = toDoubleArray(components.rewards());
        int responseLength = responseIds.length == 0 ? 0 : responseIds[0].length;
        double[][] tokenScores = createTokenLevelScores(attentionMask, positionIds, scores, responseLength);

        TrainingDiagnostics.diagBatchAssembly(
                new TrainingDiagnostics.BatchAssemblyDiag(
                        inputIds,
                        responseIds,
                        attentionMask,
                        positionIds,
                        tokenScores,
                        scores,
                        transitionCount
                )
        );

        long[][] actorLossMask = null;
        if (components.hasAnyLossMask()) {
            actorLossMask = new long[transitionCount][responseLength];
            for (int rowIndex = 0; rowIndex < transitionCount; rowIndex++) {
                List<Integer> mask = components.lossMasks().get(rowIndex);
                List<Integer> filledMask = mask != null ? mask : filledList(responseLength, 1);
                for (int columnIndex = 0; columnIndex < responseLength && columnIndex < filledMask.size(); columnIndex++) {
                    actorLossMask[rowIndex][columnIndex] = filledMask.get(columnIndex);
                }
            }
        }

        return new TensorBatch(
                inputIds,
                responseIds,
                sequenceIds,
                attentionMask,
                positionIds,
                toBooleanArray(components.isDrop()),
                tokenScores,
                actorLossMask
        );
    }

    public GeneratedRlBatch generateRlBatch(Map<String, List<RolloutWithReward>> rolloutDict, Object device) {
        Components components = generateComponents(rolloutDict, maxPromptLength, maxResponseLength);
        if (components.inputIds().isEmpty()) {
            LOGGER.warning(
                    "generate_rl_batch: 0 samples collected after rollout, skipping tensor assembly to avoid empty batch"
            );
            throw buildTrainerError("0 samples collected after rollout");
        }

        TensorBatch batch = assembleTensorBatch(components, device);
        NonTensorMetadata metadata = new NonTensorMetadata(
                components.dataIds(),
                components.turnIndices(),
                components.nTurnsList()
        );
        return new GeneratedRlBatch(batch, metadata);
    }

    public Components generateComponents(
            Map<String, List<RolloutWithReward>> rolloutDict,
            int maxPromptLength,
            int maxResponseLength
    ) {
        Components components = Components.empty();
        int truncationCount = 0;

        for (Map.Entry<String, List<RolloutWithReward>> entry : safeRolloutMap(rolloutDict).entrySet()) {
            truncationCount += processRolloutList(
                    components,
                    entry.getKey(),
                    entry.getValue(),
                    maxPromptLength,
                    maxResponseLength
            );
        }

        components.setTruncationCount(truncationCount);
        LOGGER.info(
                String.format(
                        Locale.ROOT,
                        "Processed %d samples, truncated %d",
                        components.inputIds().size(),
                        truncationCount
                )
        );
        return components;
    }

    private int processRolloutList(
            Components components,
            Object dataId,
            List<RolloutWithReward> rolloutList,
            int maxPromptLength,
            int maxResponseLength
    ) {
        int truncationCount = 0;
        for (RolloutWithReward rollout : safeRollouts(rolloutList)) {
            ComponentBuildResult result = buildOneComponentItem(rollout, maxPromptLength, maxResponseLength);
            components.append(result.item(), dataId);
            if (result.truncated()) {
                truncationCount += 1;
            }
        }
        return truncationCount;
    }

    private ComponentBuildResult buildOneComponentItem(
            RolloutWithReward rollout,
            int maxPromptLength,
            int maxResponseLength
    ) {
        TruncationResult truncation = truncatePromptAndResponse(
                rollout.getInputPromptIds(),
                rollout.getOutputResponseIds(),
                maxPromptLength,
                maxResponseLength
        );

        PaddingResult paddedPrompt = getLeftPaddedIdsAndAttentionMask(
                truncation.promptIds(),
                maxPromptLength,
                padTokenId
        );
        PaddingResult paddedResponse = getRightPaddedIdsAndAttentionMask(
                truncation.responseIds(),
                maxResponseLength,
                padTokenId
        );

        List<Integer> paddedLossMask = null;
        if (rollout.getLossMask() != null) {
            List<Integer> rawMask = new ArrayList<>(rollout.getLossMask());
            if (rawMask.size() > maxResponseLength) {
                rawMask = new ArrayList<>(rawMask.subList(0, maxResponseLength));
            }
            paddedLossMask = new ArrayList<>(rawMask);
            paddedLossMask.addAll(filledList(maxResponseLength - rawMask.size(), 0));
        }

        ComponentItem item = new ComponentItem(
                paddedPrompt.ids(),
                paddedPrompt.attentionMask(),
                paddedResponse.ids(),
                paddedResponse.attentionMask(),
                rollout.getReward() != null ? rollout.getReward() : 0.0d,
                rollout.getTurnId() != null ? rollout.getTurnId() : 0,
                truncation.isDrop(),
                paddedLossMask,
                rollout.getNTurns() != null ? rollout.getNTurns() : 0
        );
        boolean truncated = safeInts(rollout.getOutputResponseIds()).size() > maxResponseLength;
        return new ComponentBuildResult(item, truncated);
    }

    private static TruncationResult truncatePromptAndResponse(
            List<Integer> promptIds,
            List<Integer> responseIds,
            int maxPromptLength,
            int maxResponseLength
    ) {
        List<Integer> prompt = new ArrayList<>(safeInts(promptIds));
        List<Integer> response = new ArrayList<>(safeInts(responseIds));
        boolean isDrop = prompt.size() > maxPromptLength;
        if (isDrop) {
            prompt = new ArrayList<>(prompt.subList(0, maxPromptLength));
        }
        if (response.size() > maxResponseLength) {
            response = new ArrayList<>(response.subList(0, maxResponseLength));
        }
        return new TruncationResult(prompt, response, isDrop);
    }

    private static BaseError buildTrainerError(String message) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_TRAINER_EXECUTION_ERROR,
                "error_msg",
                message
        );
    }

    private static Map<String, List<RolloutWithReward>> safeRolloutMap(Map<String, List<RolloutWithReward>> input) {
        return input == null ? Map.of() : input;
    }

    private static List<RolloutWithReward> safeRollouts(List<RolloutWithReward> input) {
        return input == null ? List.of() : input;
    }

    private static List<Integer> safeInts(List<Integer> values) {
        return values == null ? List.of() : values;
    }

    private static List<Integer> filledList(int size, int value) {
        List<Integer> result = new ArrayList<>(Math.max(size, 0));
        for (int index = 0; index < size; index++) {
            result.add(value);
        }
        return result;
    }

    private static long[][] toLongMatrix(List<List<Integer>> values) {
        int rows = values.size();
        int cols = rows == 0 ? 0 : values.get(0).size();
        long[][] matrix = new long[rows][cols];
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            List<Integer> row = values.get(rowIndex);
            for (int columnIndex = 0; columnIndex < cols && columnIndex < row.size(); columnIndex++) {
                matrix[rowIndex][columnIndex] = row.get(columnIndex);
            }
        }
        return matrix;
    }

    private static double[] toDoubleArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static boolean[] toBooleanArray(List<Boolean> values) {
        boolean[] result = new boolean[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = Boolean.TRUE.equals(values.get(index));
        }
        return result;
    }

    private static long[][] concatRows(long[][] left, long[][] right) {
        int rows = left.length;
        int leftCols = rows == 0 ? 0 : left[0].length;
        int rightCols = right.length == 0 ? 0 : right[0].length;
        long[][] result = new long[rows][leftCols + rightCols];
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            System.arraycopy(left[rowIndex], 0, result[rowIndex], 0, leftCols);
            if (rowIndex < right.length) {
                System.arraycopy(right[rowIndex], 0, result[rowIndex], leftCols, rightCols);
            }
        }
        return result;
    }

    private static long[][] createPositionIds(long[][] attentionMask) {
        long[][] result = new long[attentionMask.length][attentionMask.length == 0 ? 0 : attentionMask[0].length];
        for (int rowIndex = 0; rowIndex < attentionMask.length; rowIndex++) {
            long running = -1L;
            for (int columnIndex = 0; columnIndex < attentionMask[rowIndex].length; columnIndex++) {
                if (attentionMask[rowIndex][columnIndex] != 0L) {
                    running += 1L;
                }
                result[rowIndex][columnIndex] = Math.max(running, 0L);
            }
        }
        return result;
    }

    /**
     * Fixed-size padded ids plus attention mask.
     */
    /**
     * Mirrors Python's left/right padding tuple output in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    public record PaddingResult(List<Integer> ids, List<Integer> attentionMask) {
        public PaddingResult {
            ids = List.copyOf(ids);
            attentionMask = List.copyOf(attentionMask);
        }
    }

    /**
     * One fully prepared transition row prior to batch tensor assembly.
     */
    /**
     * Mirrors Python's per-sample component payload in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    public record ComponentItem(
            List<Integer> inputIds,
            List<Integer> inputAttentionMask,
            List<Integer> responseIds,
            List<Integer> responseAttentionMask,
            double reward,
            int turnIndex,
            boolean isDrop,
            List<Integer> lossMask,
            int nTurns
    ) {
        public ComponentItem {
            inputIds = List.copyOf(inputIds);
            inputAttentionMask = List.copyOf(inputAttentionMask);
            responseIds = List.copyOf(responseIds);
            responseAttentionMask = List.copyOf(responseAttentionMask);
            lossMask = lossMask == null ? null : List.copyOf(lossMask);
        }
    }

    /**
     * Component accumulator for one rollout batch.
     */
    /**
     * Mirrors Python's component accumulation dict in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    public static final class Components {
        private final List<List<Integer>> inputIds = new ArrayList<>();
        private final List<List<Integer>> inputAttentionMask = new ArrayList<>();
        private final List<List<Integer>> responseIds = new ArrayList<>();
        private final List<List<Integer>> responseAttentionMask = new ArrayList<>();
        private final List<Double> rewards = new ArrayList<>();
        private final List<Integer> turnIndices = new ArrayList<>();
        private final List<Boolean> isDrop = new ArrayList<>();
        private final List<Object> dataIds = new ArrayList<>();
        private final List<List<Integer>> lossMasks = new ArrayList<>();
        private final List<Integer> nTurnsList = new ArrayList<>();
        private int truncationCount;

        public static Components empty() {
            return new Components();
        }

        public void append(ComponentItem item, Object dataId) {
            inputIds.add(item.inputIds());
            inputAttentionMask.add(item.inputAttentionMask());
            responseIds.add(item.responseIds());
            responseAttentionMask.add(item.responseAttentionMask());
            rewards.add(item.reward());
            turnIndices.add(item.turnIndex());
            isDrop.add(item.isDrop());
            dataIds.add(dataId);
            lossMasks.add(item.lossMask());
            nTurnsList.add(item.nTurns());
        }

        public List<List<Integer>> inputIds() {
            return inputIds;
        }

        public List<List<Integer>> inputAttentionMask() {
            return inputAttentionMask;
        }

        public List<List<Integer>> responseIds() {
            return responseIds;
        }

        public List<List<Integer>> responseAttentionMask() {
            return responseAttentionMask;
        }

        public List<Double> rewards() {
            return rewards;
        }

        public List<Integer> turnIndices() {
            return turnIndices;
        }

        public List<Boolean> isDrop() {
            return isDrop;
        }

        public List<Object> dataIds() {
            return dataIds;
        }

        public List<List<Integer>> lossMasks() {
            return lossMasks;
        }

        public List<Integer> nTurnsList() {
            return nTurnsList;
        }

        public int truncationCount() {
            return truncationCount;
        }

        public void setTruncationCount(int truncationCount) {
            this.truncationCount = truncationCount;
        }

        public boolean hasAnyLossMask() {
            for (List<Integer> mask : lossMasks) {
                if (mask != null) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Batch tensor payload represented with strong Java types instead of raw maps.
     */
    /**
     * Mirrors Python's assembled tensor batch surface in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    public static final class TensorBatch {
        private final long[][] prompts;
        private final long[][] responses;
        private final long[][] inputIds;
        private final long[][] attentionMask;
        private final long[][] positionIds;
        private final boolean[] isDropMask;
        private final double[][] tokenLevelScores;
        private final long[][] actorLossMask;

        public TensorBatch(
                long[][] prompts,
                long[][] responses,
                long[][] inputIds,
                long[][] attentionMask,
                long[][] positionIds,
                boolean[] isDropMask,
                double[][] tokenLevelScores,
                long[][] actorLossMask
        ) {
            this.prompts = prompts;
            this.responses = responses;
            this.inputIds = inputIds;
            this.attentionMask = attentionMask;
            this.positionIds = positionIds;
            this.isDropMask = isDropMask;
            this.tokenLevelScores = tokenLevelScores;
            this.actorLossMask = actorLossMask;
        }

        public long[][] prompts() {
            return prompts;
        }

        public long[][] responses() {
            return responses;
        }

        public long[][] inputIds() {
            return inputIds;
        }

        public long[][] attentionMask() {
            return attentionMask;
        }

        public long[][] positionIds() {
            return positionIds;
        }

        public boolean[] isDropMask() {
            return isDropMask;
        }

        public double[][] tokenLevelScores() {
            return tokenLevelScores;
        }

        public long[][] actorLossMask() {
            return actorLossMask;
        }

        public int batchSize() {
            return prompts.length;
        }

        public Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("prompts", prompts);
            values.put("responses", responses);
            values.put("input_ids", inputIds);
            values.put("attention_mask", attentionMask);
            values.put("position_ids", positionIds);
            values.put("is_drop_mask", isDropMask);
            values.put("token_level_scores", tokenLevelScores);
            if (actorLossMask != null) {
                values.put("actor_loss_mask", actorLossMask);
            }
            return values;
        }
    }

    /**
     * Typed non-tensor metadata that Python stores as numpy object arrays.
     */
    /**
     * Mirrors Python's non-tensor metadata payload in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    public record NonTensorMetadata(
            List<Object> dataIdList,
            List<Integer> turnIndexList,
            List<Integer> nTurnsList
    ) {
        public NonTensorMetadata {
            dataIdList = List.copyOf(dataIdList);
            turnIndexList = List.copyOf(turnIndexList);
            nTurnsList = List.copyOf(nTurnsList);
        }

        public Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("data_id_list", dataIdList);
            values.put("turn_index_list", turnIndexList);
            values.put("n_turns_list", nTurnsList);
            return values;
        }
    }

    /**
     * Final batch assembly result.
     */
    /**
     * Mirrors Python's {@code (TensorDict, non_tensor_dict)} return contract in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    public record GeneratedRlBatch(TensorBatch batch, NonTensorMetadata nonTensorMetadata) {
    }

    /**
     * Mirrors Python's internal one-item build result in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    private record ComponentBuildResult(ComponentItem item, boolean truncated) {
    }

    /**
     * Mirrors Python's internal prompt/response truncation result in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/batch_builder.py}.
     */
    private record TruncationResult(List<Integer> promptIds, List<Integer> responseIds, boolean isDrop) {
    }
}
