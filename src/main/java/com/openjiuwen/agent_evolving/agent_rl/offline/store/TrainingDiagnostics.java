/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Consolidated training diagnostics for the RL training pipeline.
 *
 * <p>Mirrors Python's {@code TrainingDiagnostics} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/store/metrics_tracker.py}.</p>
 */
public class TrainingDiagnostics {

    private static final Logger LOGGER = Logger.getLogger(TrainingDiagnostics.class.getName());
    private final Object tokenizer;

    public TrainingDiagnostics() {
        this(null);
    }

    public TrainingDiagnostics(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    public static void diagEncoding(RolloutMessage rolloutMessage, int totalTurns, double globalReward) {
        if (rolloutMessage == null) {
            LOGGER.info("[DIAG_DATA] build: rollout message missing");
            return;
        }
        List<String> roundedRewards = new ArrayList<>();
        List<Double> rewardList = rolloutMessage.getRewardList();
        if (rewardList != null) {
            for (Double reward : rewardList) {
                roundedRewards.add(String.format(Locale.ROOT, "%.4f", reward != null ? reward : 0.0d));
            }
        }
        LOGGER.info(String.format(
                Locale.ROOT,
                "[DIAG_DATA] build: rollout_id=%s total_turns=%d global_reward=%.4f reward_list=%s origin_task_id=%s",
                rolloutMessage.getRolloutId(),
                totalTurns,
                globalReward,
                roundedRewards,
                rolloutMessage.getOriginTaskId()));
    }

    public static void diagBatchAssembly(BatchAssemblyDiag diag) {
        if (diag == null) {
            LOGGER.info("[DIAG_S0] missing diagnostics payload");
            return;
        }
        List<List<Double>> inputIds = numericMatrix(diag.getInputIds());
        List<List<Double>> responseIds = numericMatrix(diag.getResponseIds());
        List<List<Double>> attentionMask = numericMatrix(diag.getAttentionMask());
        List<List<Double>> positionIds = numericMatrix(diag.getPositionIds());
        List<List<Double>> tokenScores = numericMatrix(diag.getTokenScores());
        List<Double> scores = numericVector(diag.getScores());

        int promptLen = columnCount(inputIds);
        int respLen = columnCount(responseIds);
        List<String> problems = new ArrayList<>();
        int checked = Math.min(diag.getNTransition(), 4);
        for (int index = 0; index < checked; index++) {
            List<Double> posRow = row(positionIds, index);
            List<Double> attnRow = row(attentionMask, index);
            int eosAbsPos = argMax(multiplyRows(posRow, attnRow));
            boolean eosInResponse = eosAbsPos >= promptLen;
            int respActive = sumAsInt(slice(attnRow, promptLen, attnRow.size()));
            double rewardInResp = sum(row(tokenScores, index));
            double originalReward = value(scores, index);
            boolean rewardOk = Math.abs(rewardInResp - originalReward) < 1e-3d;
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[DIAG_S0] sample %d: eos_abs_pos=%d prompt_len=%d eos_in_response=%s resp_active_tokens=%d/%d reward_in_scores=%.4f original_reward=%.4f match=%s",
                    index,
                    eosAbsPos,
                    promptLen,
                    eosInResponse,
                    respActive,
                    respLen,
                    rewardInResp,
                    originalReward,
                    rewardOk));
            if (!eosInResponse) {
                problems.add(String.format(
                        Locale.ROOT,
                        "sample %d: eos_pos=%d < prompt_len=%d, reward in PROMPT part",
                        index,
                        eosAbsPos,
                        promptLen));
            }
            if (!rewardOk) {
                problems.add(String.format(
                        Locale.ROOT,
                        "sample %d: reward mismatch %.4f != %.4f",
                        index,
                        rewardInResp,
                        originalReward));
            }
        }
        if (problems.isEmpty()) {
            LOGGER.info(String.format(Locale.ROOT,
                    "[DIAG_S0] OK: all %d checked samples have correct EOS & reward placement",
                    checked));
        } else {
            LOGGER.warning("[DIAG_S0] PROBLEMS FOUND:\n  " + String.join("\n  ", problems));
        }
    }

    public static void diagAfterReward(Object batch) {
        Map<String, Object> batchMap = nestedMap(batch, "batch");
        Map<String, Object> nonTensorBatch = nestedMap(batch, "non_tensor_batch", "nonTensorBatch");
        List<List<Double>> responseMask = numericMatrix(batchMap.get("response_mask"));
        List<List<Double>> tokenScores = numericMatrix(batchMap.get("token_level_scores"));
        List<List<Double>> attentionMask = numericMatrix(batchMap.get("attention_mask"));
        int sampleCount = batchSize(batch, batchMap);

        List<Double> respSums = rowSums(responseMask);
        List<Double> attnSums = rowSums(attentionMask);
        List<Double> scoreSums = rowSums(tokenScores);

        LOGGER.info(String.format(
                Locale.ROOT,
                "[DIAG_S1] n_samples=%d resp_mask: mean=%.1f min=%d max=%d attn_mask: mean=%.1f score_sums: min=%.4f max=%.4f has_actor_loss_mask=%s",
                sampleCount,
                average(respSums),
                (int) min(respSums),
                (int) max(respSums),
                average(attnSums),
                min(scoreSums),
                max(scoreSums),
                batchMap.containsKey("actor_loss_mask")));

        List<Object> uids = objectList(nonTensorBatch.get("uid"));
        if (uids.isEmpty()) {
            return;
        }

        Map<String, List<Integer>> uidToIndices = groupIndices(uids);
        List<Integer> groupSizes = uidToIndices.values().stream().map(List::size).toList();
        int noVarianceCount = 0;
        for (List<Integer> indices : uidToIndices.values()) {
            List<String> rounded = new ArrayList<>();
            for (Integer index : indices) {
                rounded.add(String.format(Locale.ROOT, "%.4f", value(scoreSums, index)));
            }
            if (rounded.stream().distinct().count() <= 1) {
                noVarianceCount++;
            }
        }

        LOGGER.info(String.format(
                Locale.ROOT,
                "[DIAG_S1] groups=%d group_sizes=%s no_reward_variance=%d/%d",
                uidToIndices.size(),
                groupSizes.stream().distinct().sorted().toList(),
                noVarianceCount,
                uidToIndices.size()));

        int emitted = 0;
        for (Map.Entry<String, List<Integer>> entry : uidToIndices.entrySet()) {
            if (emitted++ >= 2) {
                break;
            }
            List<String> rewards = new ArrayList<>();
            List<Integer> masks = new ArrayList<>();
            for (Integer index : entry.getValue()) {
                rewards.add(String.format(Locale.ROOT, "%.4f", value(scoreSums, index)));
                masks.add((int) value(respSums, index));
            }
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[DIAG_S1] group=%s size=%d rewards=%s resp_masks=%s",
                    shortUid(entry.getKey()),
                    entry.getValue().size(),
                    rewards,
                    masks));
        }
    }

    public static void diagAfterOldLogProb(Object batch) {
        Map<String, Object> batchMap = nestedMap(batch, "batch");
        List<List<Double>> oldLogProbs = numericMatrix(batchMap.get("old_log_probs"));
        if (oldLogProbs.isEmpty()) {
            LOGGER.warning("[DIAG_S2] old_log_probs not found in batch!");
            return;
        }
        List<List<Double>> responseMask = numericMatrix(batchMap.get("response_mask"));
        List<Double> masked = flattenMasked(oldLogProbs, responseMask);
        int nanCount = countNaN(masked);
        int infCount = countInfinite(masked);
        int negInfCount = countNegativeInfinite(masked);
        List<Double> finite = masked.stream().filter(Double::isFinite).toList();

        LOGGER.info(String.format(
                Locale.ROOT,
                "[DIAG_S2] old_log_probs (on resp tokens): n=%d nan=%d inf=%d neg_inf=%d mean=%.4f std=%.4f min=%.4f max=%.4f pct_below_m10=%.2f%%",
                masked.size(),
                nanCount,
                infCount,
                negInfCount,
                average(finite),
                standardDeviation(finite),
                min(finite),
                max(finite),
                percentBelow(finite, -10.0d)));

        if (nanCount > 0 || negInfCount > 0) {
            LOGGER.warning(String.format(
                    Locale.ROOT,
                    "[DIAG_S2] PROBLEM: old_log_probs has %d NaN and %d -inf on response tokens! This will cause NaN in PPO ratio.",
                    nanCount,
                    negInfCount));
        }

        int checked = Math.min(oldLogProbs.size(), 4);
        for (int index = 0; index < checked; index++) {
            List<Double> row = maskedRow(oldLogProbs, responseMask, index);
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[DIAG_S2] sample %d: resp_tokens=%d lp_mean=%.4f lp_min=%.4f lp_max=%.4f",
                    index,
                    row.size(),
                    average(row),
                    min(row),
                    max(row)));
        }
    }

    public static void diagAfterAdvantages(Object batch) {
        Map<String, Object> batchMap = nestedMap(batch, "batch");
        List<List<Double>> advantages = numericMatrix(batchMap.get("advantages"));
        if (advantages.isEmpty()) {
            LOGGER.warning("[DIAG_S3] no 'advantages' in batch!");
            return;
        }
        List<List<Double>> responseMask = numericMatrix(batchMap.get("response_mask"));
        List<List<Double>> tokenRewards = numericMatrix(batchMap.get("token_level_rewards"));
        List<Double> advFlat = flattenMasked(advantages, responseMask);
        int nanCount = countNaN(advFlat);
        int infCount = countInfinite(advFlat);
        List<Double> finite = advFlat.stream().filter(Double::isFinite).toList();

        LOGGER.info(String.format(
                Locale.ROOT,
                "[DIAG_S3] advantages: n=%d nan=%d inf=%d mean=%.6f std=%.6f min=%.4f max=%.4f",
                advFlat.size(),
                nanCount,
                infCount,
                average(finite),
                standardDeviation(finite),
                min(finite),
                max(finite)));

        if (nanCount > 0 || infCount > 0) {
            LOGGER.warning(String.format(
                    Locale.ROOT,
                    "[DIAG_S3] PROBLEM: advantages has %d NaN and %d Inf!",
                    nanCount,
                    infCount));
        }

        if (!tokenRewards.isEmpty()) {
            List<Double> rewardFlat = flattenMasked(tokenRewards, responseMask);
            long nonZero = rewardFlat.stream().filter(value -> Math.abs(value) > 0.0d).count();
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[DIAG_S3] token_level_rewards: nonzero=%d/%d mean=%.6f min=%.4f max=%.4f",
                    nonZero,
                    rewardFlat.size(),
                    average(rewardFlat),
                    min(rewardFlat),
                    max(rewardFlat)));
        }

        List<String> perSample = new ArrayList<>();
        int checked = Math.min(advantages.size(), 8);
        for (int index = 0; index < checked; index++) {
            List<Double> advantageRow = maskedRow(advantages, responseMask, index);
            List<Double> rewardRow = maskedRow(tokenRewards, responseMask, index);
            perSample.add(String.format(
                    Locale.ROOT,
                    "s%d(r=%.2f,adv=%.4f)",
                    index,
                    sum(rewardRow),
                    average(advantageRow)));
        }
        LOGGER.info("[DIAG_S3] per_sample: " + String.join("  ", perSample));
    }

    public static void diagAfterActorUpdate(Map<String, ?> metrics) {
        if (metrics == null) {
            LOGGER.info("[DIAG_S4] actor_update: (no actor metrics found)");
            return;
        }
        List<String> keys = List.of(
                "actor/entropy_loss",
                "actor/pg_loss",
                "actor/actor_loss",
                "actor/pg_clipfrac",
                "actor/approx_kl",
                "actor/grad_norm");
        List<String> parts = new ArrayList<>();
        for (String key : keys) {
            Double value = number(metrics.get(key));
            if (value != null) {
                parts.add(key.substring(key.indexOf('/') + 1) + "=" + String.format(Locale.ROOT, "%.6f", value));
            }
        }
        LOGGER.info("[DIAG_S4] actor_update: " + (parts.isEmpty() ? "(no actor metrics found)" : String.join("  ", parts)));

        Double pgLoss = number(metrics.get("actor/pg_loss"));
        Double clipFrac = number(metrics.get("actor/pg_clipfrac"));
        Double approxKl = number(metrics.get("actor/approx_kl"));
        if (pgLoss != null && Math.abs(pgLoss) > 10.0d) {
            LOGGER.warning(String.format(Locale.ROOT,
                    "[DIAG_S4] PROBLEM: pg_loss=%.4f is very large (>10), possible divergence!",
                    pgLoss));
        }
        if (clipFrac != null && clipFrac > 0.5d) {
            LOGGER.warning(String.format(Locale.ROOT,
                    "[DIAG_S4] WARNING: clip_frac=%.4f > 0.5, policy changed too much",
                    clipFrac));
        }
        if (approxKl != null && approxKl > 0.1d) {
            LOGGER.warning(String.format(Locale.ROOT,
                    "[DIAG_S4] WARNING: approx_kl=%.4f > 0.1, large policy shift",
                    approxKl));
        }
    }

    public void diagnoseBatch(Object batch, int globalSteps) {
        Map<String, Object> batchMap = nestedMap(batch, "batch");
        Map<String, Object> nonTensorBatch = nestedMap(batch, "non_tensor_batch", "nonTensorBatch");
        List<Object> uids = objectList(nonTensorBatch.get("uid"));
        if (uids.isEmpty()) {
            LOGGER.warning("[diagnose] 'uid' not in non_tensor_batch, skipping");
            return;
        }

        List<List<Double>> inputIds = numericMatrix(batchMap.get("input_ids"));
        List<List<Double>> attentionMask = numericMatrix(batchMap.get("attention_mask"));
        List<List<Double>> scores = numericMatrix(batchMap.get("token_level_scores"));
        List<List<Double>> responseMask = numericMatrix(batchMap.get("response_mask"));
        List<List<Double>> lossMask = numericMatrix(batchMap.get("actor_loss_mask"));
        List<Object> turns = objectList(nonTensorBatch.get("n_turns_list"));
        int promptLen = columnCount(numericMatrix(batchMap.get("prompts")));

        Map<String, List<Integer>> uidToIndices = groupIndices(uids);
        int zeroRespMask = 0;
        int zeroLossMask = 0;
        int noVarianceGroups = 0;
        for (List<Integer> indices : uidToIndices.values()) {
            List<String> rewards = new ArrayList<>();
            for (Integer index : indices) {
                rewards.add(String.format(Locale.ROOT, "%.6f", sum(maskedRow(scores, responseMask, index))));
                if (sum(maskRow(responseMask, index)) == 0.0d) {
                    zeroRespMask++;
                }
                if (!lossMask.isEmpty() && sum(maskRow(lossMask, index)) == 0.0d) {
                    zeroLossMask++;
                }
            }
            if (rewards.stream().distinct().count() <= 1) {
                noVarianceGroups++;
            }
        }

        LOGGER.info(String.format(
                Locale.ROOT,
                "[diagnose] step=%d groups=%d samples=%d zero_resp_mask=%d zero_loss_mask=%d no_reward_variance_groups=%d",
                globalSteps,
                uidToIndices.size(),
                batchSize(batch, batchMap),
                zeroRespMask,
                zeroLossMask,
                noVarianceGroups));

        int emitted = 0;
        for (Map.Entry<String, List<Integer>> entry : uidToIndices.entrySet()) {
            if (emitted++ >= 2) {
                break;
            }
            List<String> lines = new ArrayList<>();
            lines.add("  [group uid=" + shortUid(entry.getKey()) + " size=" + entry.getValue().size() + "]");
            for (Integer index : entry.getValue()) {
                List<Double> ids = row(inputIds, index);
                List<Double> attn = row(attentionMask, index);
                List<Integer> promptActive = activeTokens(slice(ids, 0, Math.min(promptLen, ids.size())),
                        slice(attn, 0, Math.min(promptLen, attn.size())));
                List<Integer> respActive = activeTokens(slice(ids, Math.min(promptLen, ids.size()), ids.size()),
                        slice(attn, Math.min(promptLen, attn.size()), attn.size()));
                double reward = sum(maskedRow(scores, responseMask, index));
                int respMaskSum = (int) sum(maskRow(responseMask, index));
                int lossMaskSum = (int) sum(maskRow(lossMask, index));
                int turnCount = intValue(objectValue(turns, index));
                lines.add(String.format(
                        Locale.ROOT,
                        "  [sample %d] reward=%.4f n_turns=%d prompt_tokens=%d resp_tokens=%d resp_mask=%d loss_mask=%d",
                        index,
                        reward,
                        turnCount,
                        promptActive.size(),
                        respActive.size(),
                        respMaskSum,
                        lossMaskSum));
                lines.add("    prompt: " + decodeTokens(promptActive));
                lines.add("    response: " + decodeTokens(respActive));
            }
            LOGGER.info("[diagnose]\n" + String.join("\n", lines));
        }
    }

    /**
     * Inputs for DIAG_S0 batch assembly diagnostics.
     *
     * <p>Mirrors Python's {@code BatchAssemblyDiag} in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/store/metrics_tracker.py}.</p>
     */
    public static final class BatchAssemblyDiag {
        private final Object inputIds;
        private final Object responseIds;
        private final Object attentionMask;
        private final Object positionIds;
        private final Object tokenScores;
        private final Object scores;
        private final int nTransition;

        public BatchAssemblyDiag(Object inputIds,
                                 Object responseIds,
                                 Object attentionMask,
                                 Object positionIds,
                                 Object tokenScores,
                                 Object scores,
                                 int nTransition) {
            this.inputIds = inputIds;
            this.responseIds = responseIds;
            this.attentionMask = attentionMask;
            this.positionIds = positionIds;
            this.tokenScores = tokenScores;
            this.scores = scores;
            this.nTransition = nTransition;
        }

        public Object getInputIds() {
            return inputIds;
        }

        public Object getResponseIds() {
            return responseIds;
        }

        public Object getAttentionMask() {
            return attentionMask;
        }

        public Object getPositionIds() {
            return positionIds;
        }

        public Object getTokenScores() {
            return tokenScores;
        }

        public Object getScores() {
            return scores;
        }

        public int getNTransition() {
            return nTransition;
        }
    }

    private String decodeTokens(List<Integer> tokens) {
        if (tokenizer == null) {
            return tokens.toString();
        }
        Object decoded = invokeBestEffort(tokenizer, "decode", List.class, boolean.class, tokens, false);
        if (decoded == null) {
            decoded = invokeBestEffort(tokenizer, "decode", List.class, tokens);
        }
        return decoded != null ? String.valueOf(decoded) : tokens.toString();
    }

    private static Map<String, Object> nestedMap(Object source, String... names) {
        for (String name : names) {
            Object value = readMember(source, name);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copied.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return copied;
            }
        }
        return Collections.emptyMap();
    }

    private static Object readMember(Object source, String name) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            if (map.containsKey(name)) {
                return map.get(name);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (Objects.equals(String.valueOf(entry.getKey()), name)) {
                    return entry.getValue();
                }
            }
            return null;
        }
        try {
            Field field = source.getClass().getField(name);
            return field.get(source);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Object value = invokeBestEffort(source, getter);
        if (value != null) {
            return value;
        }
        return invokeBestEffort(source, name);
    }

    private static Object invokeBestEffort(Object source, String name, Class<?>... parameterTypesAndValues) {
        return null;
    }

    private static Object invokeBestEffort(Object source, String methodName) {
        if (source == null) {
            return null;
        }
        try {
            Method method = source.getClass().getMethod(methodName);
            return method.invoke(source);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Object invokeBestEffort(Object source,
                                           String methodName,
                                           Class<?> firstType,
                                           Class<?> secondType,
                                           Object firstArg,
                                           Object secondArg) {
        if (source == null) {
            return null;
        }
        try {
            Method method = source.getClass().getMethod(methodName, firstType, secondType);
            return method.invoke(source, firstArg, secondArg);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Object invokeBestEffort(Object source,
                                           String methodName,
                                           Class<?> firstType,
                                           Object firstArg) {
        if (source == null) {
            return null;
        }
        try {
            Method method = source.getClass().getMethod(methodName, firstType);
            return method.invoke(source, firstArg);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static List<List<Double>> numericMatrix(Object value) {
        List<Object> outer = objectList(value);
        List<List<Double>> matrix = new ArrayList<>();
        for (Object row : outer) {
            matrix.add(numericVector(row));
        }
        return matrix;
    }

    private static List<Double> numericVector(Object value) {
        List<Object> values = objectList(value);
        List<Double> numeric = new ArrayList<>();
        for (Object item : values) {
            Double number = number(item);
            numeric.add(number != null ? number : 0.0d);
        }
        return numeric;
    }

    private static List<Object> objectList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(Array.get(value, index));
            }
            return result;
        }
        return List.of(value);
    }

    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static int batchSize(Object batch, Map<String, Object> batchMap) {
        Object size = invokeBestEffort(batch, "size");
        if (size instanceof Number number) {
            return number.intValue();
        }
        for (Object value : batchMap.values()) {
            List<List<Double>> matrix = numericMatrix(value);
            if (!matrix.isEmpty()) {
                return matrix.size();
            }
        }
        return 0;
    }

    private static List<Double> row(List<List<Double>> matrix, int index) {
        if (index < 0 || index >= matrix.size()) {
            return List.of();
        }
        return matrix.get(index);
    }

    private static List<Double> maskRow(List<List<Double>> matrix, int index) {
        return row(matrix, index);
    }

    private static List<Double> multiplyRows(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        List<Double> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(left.get(index) * right.get(index));
        }
        return result;
    }

    private static List<Double> slice(List<Double> values, int from, int to) {
        if (values.isEmpty() || from >= values.size() || from >= to) {
            return List.of();
        }
        int upper = Math.min(to, values.size());
        return new ArrayList<>(values.subList(Math.max(0, from), upper));
    }

    private static int argMax(List<Double> values) {
        if (values.isEmpty()) {
            return -1;
        }
        int bestIndex = 0;
        double bestValue = values.get(0);
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index) > bestValue) {
                bestValue = values.get(index);
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static double sum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static int sumAsInt(List<Double> values) {
        return (int) Math.round(sum(values));
    }

    private static double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private static double min(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0d);
    }

    private static double max(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0d);
    }

    private static double standardDeviation(List<Double> values) {
        if (values == null || values.size() <= 1) {
            return 0.0d;
        }
        double mean = average(values);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0d);
        return Math.sqrt(variance);
    }

    private static double percentBelow(List<Double> values, double threshold) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        long count = values.stream().filter(value -> value < threshold).count();
        return count * 100.0d / values.size();
    }

    private static List<Double> rowSums(List<List<Double>> matrix) {
        List<Double> sums = new ArrayList<>();
        for (List<Double> row : matrix) {
            sums.add(sum(row));
        }
        return sums;
    }

    private static double value(List<Double> values, int index) {
        if (index < 0 || index >= values.size()) {
            return 0.0d;
        }
        return values.get(index);
    }

    private static Object objectValue(List<Object> values, int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private static int columnCount(List<List<Double>> matrix) {
        return matrix.isEmpty() ? 0 : matrix.get(0).size();
    }

    private static Map<String, List<Integer>> groupIndices(List<Object> uids) {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < uids.size(); index++) {
            String key = String.valueOf(uids.get(index));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
        }
        return grouped;
    }

    private static String shortUid(String uid) {
        if (uid == null) {
            return "null";
        }
        return uid.length() <= 8 ? uid : uid.substring(0, 8);
    }

    private static List<Double> flattenMasked(List<List<Double>> matrix, List<List<Double>> mask) {
        if (matrix.isEmpty()) {
            return List.of();
        }
        if (mask.isEmpty()) {
            List<Double> flattened = new ArrayList<>();
            for (List<Double> row : matrix) {
                flattened.addAll(row);
            }
            return flattened;
        }
        List<Double> flattened = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < matrix.size(); rowIndex++) {
            flattened.addAll(maskedRow(matrix, mask, rowIndex));
        }
        return flattened;
    }

    private static List<Double> maskedRow(List<List<Double>> matrix, List<List<Double>> mask, int rowIndex) {
        List<Double> values = row(matrix, rowIndex);
        if (mask.isEmpty()) {
            return values;
        }
        List<Double> maskRow = row(mask, rowIndex);
        List<Double> filtered = new ArrayList<>();
        int size = Math.min(values.size(), maskRow.size());
        for (int index = 0; index < size; index++) {
            if (Math.abs(maskRow.get(index)) > 0.0d) {
                filtered.add(values.get(index));
            }
        }
        return filtered;
    }

    private static int countNaN(List<Double> values) {
        return (int) values.stream().filter(value -> value != null && value.isNaN()).count();
    }

    private static int countInfinite(List<Double> values) {
        return (int) values.stream().filter(value -> value != null && value.isInfinite()).count();
    }

    private static int countNegativeInfinite(List<Double> values) {
        return (int) values.stream().filter(value -> value.equals(Double.NEGATIVE_INFINITY)).count();
    }

    private static List<Integer> activeTokens(List<Double> ids, List<Double> attention) {
        List<Integer> result = new ArrayList<>();
        int size = Math.min(ids.size(), attention.size());
        for (int index = 0; index < size; index++) {
            if (Math.abs(attention.get(index)) > 0.0d) {
                result.add(ids.get(index).intValue());
            }
        }
        return result;
    }
}
