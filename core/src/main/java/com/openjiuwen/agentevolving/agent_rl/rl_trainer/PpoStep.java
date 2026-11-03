// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agentevolving.agent_rl.rl_trainer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PPO training step orchestration.
 * <p>
 * Mirrors Python's {@code run_ppo_step} in
 * {@code openjiuwen/agent_evolving/agent_rl/rl_trainer/ppo_step.py}.
 */
public final class PpoStep {

    private static final List<String> CLAMP_KEYS = List.of(
            "advantages",
            "old_log_probs",
            "token_level_rewards"
    );

    private PpoStep() {
    }

    /**
     * Executor contract used by the shared PPO/GRPO pipeline.
     */
    public interface TrainingExecutor {
        int getMiniBatchSize();

        Batch computeBaseline(Object originBatch, Batch batch);

        Batch computeReward(Batch batch, Map<String, Object> metrics);

        Batch computeOldLogProb(Batch batch, Map<String, Object> metrics);

        Batch computeReferenceLogProb(Batch batch);

        Batch computeValues(Batch batch);

        Batch computeAdvantages(Batch batch, Map<String, Object> metrics);

        Batch filterEffectiveGroups(Batch batch, Map<String, Object> metrics);

        void balanceBatch(Batch batch, Map<String, Object> metrics);

        void updateCritic(Batch batch, Map<String, Object> metrics);

        void updateActor(Batch batch, Map<String, Object> metrics);

        Map<String, Object> processMetrics(Batch batch,
                                           Map<String, Object> metrics,
                                           Map<String, Double> timingRaw);
    }

    /**
     * Java representation of the small DataProto surface needed by
     * {@code ppo_step.py}: length, first-dimension selection, and reorder.
     */
    public static final class Batch {
        private final Map<String, Object> batch;
        private final Map<String, Object> nonTensors;
        private final int length;

        public Batch(Map<String, Object> batch) {
            this(batch, Map.of(), inferLength(batch));
        }

        public Batch(Map<String, Object> batch, Map<String, Object> nonTensors) {
            this(batch, nonTensors, inferLength(batch));
        }

        public Batch(Map<String, Object> batch, Map<String, Object> nonTensors, int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length must be non-negative");
            }
            this.batch = new LinkedHashMap<>(batch != null ? batch : Map.of());
            this.nonTensors = new LinkedHashMap<>(nonTensors != null ? nonTensors : Map.of());
            this.length = length;
        }

        public static Batch of(Map<String, Object> batch) {
            return new Batch(batch);
        }

        public static Batch of(Map<String, Object> batch, int length) {
            return new Batch(batch, Map.of(), length);
        }

        public int length() {
            return length;
        }

        public Map<String, Object> getBatch() {
            return batch;
        }

        public Map<String, Object> getNonTensors() {
            return nonTensors;
        }

        public Object get(String key) {
            return batch.get(key);
        }

        public boolean contains(String key) {
            return batch.containsKey(key);
        }

        public Batch select(List<Integer> indices) {
            List<Integer> safeIndices = indices != null ? indices : List.of();
            return new Batch(
                    selectMap(batch, safeIndices, length),
                    selectMap(nonTensors, safeIndices, length),
                    safeIndices.size()
            );
        }

        public Batch reorder(List<Integer> indices) {
            List<Integer> safeIndices = indices != null ? indices : List.of();
            Map<String, Object> reorderedBatch = selectMap(batch, safeIndices, length);
            Map<String, Object> reorderedNonTensors = selectMap(nonTensors, safeIndices, length);
            batch.clear();
            batch.putAll(reorderedBatch);
            nonTensors.clear();
            nonTensors.putAll(reorderedNonTensors);
            return new Batch(batch, nonTensors, safeIndices.size());
        }

        private static Map<String, Object> selectMap(Map<String, Object> source, List<Integer> indices, int length) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                out.put(entry.getKey(), selectValue(entry.getValue(), indices, length));
            }
            return out;
        }

        private static Object selectValue(Object value, List<Integer> indices, int length) {
            int size = firstDimension(value);
            if (size != length) {
                return value;
            }
            if (value instanceof List<?> list) {
                List<Object> selected = new ArrayList<>(indices.size());
                for (int index : indices) {
                    selected.add(list.get(index));
                }
                return selected;
            }
            if (value != null && value.getClass().isArray()) {
                Class<?> component = value.getClass().getComponentType();
                Object selected = Array.newInstance(component, indices.size());
                for (int i = 0; i < indices.size(); i++) {
                    Array.set(selected, i, Array.get(value, indices.get(i)));
                }
                return selected;
            }
            return value;
        }

        private static int inferLength(Map<String, Object> values) {
            if (values == null || values.isEmpty()) {
                return 0;
            }
            for (Object value : values.values()) {
                int size = firstDimension(value);
                if (size >= 0) {
                    return size;
                }
            }
            return 0;
        }
    }

    /**
     * Backward-compatible two-argument entry point.
     *
     * @param executor executor implementing the Python-compatible training hooks
     * @param batch batch to train on, with {@code originBatch} treated as {@code null}
     * @return training metrics
     */
    public static Map<String, Object> runPpoStep(Object executor, Object batch) {
        if (!(executor instanceof TrainingExecutor trainingExecutor)) {
            throw new IllegalArgumentException("executor must implement PpoStep.TrainingExecutor");
        }
        if (!(batch instanceof Batch dataBatch)) {
            throw new IllegalArgumentException("batch must be a PpoStep.Batch");
        }
        return runPpoStep(trainingExecutor, null, dataBatch);
    }

    /**
     * Run the full PPO training step.
     *
     * @param executor training executor implementing the sub-steps
     * @param originBatch original rollout batch used for baseline computation
     * @param batch mutable training batch
     * @return training metrics
     */
    public static Map<String, Object> runPpoStep(TrainingExecutor executor, Object originBatch, Batch batch) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        Map<String, Double> timingRaw = new LinkedHashMap<>();

        Batch stepResult = timed("step", timingRaw, () -> {
            Batch afterBaseline = timed("gen_max", timingRaw, () -> executor.computeBaseline(originBatch, batch));
            Batch afterReward = timed("reward", timingRaw, () -> executor.computeReward(afterBaseline, metrics));
            Batch afterOldLogProb = timed("old_log_prob", timingRaw,
                    () -> executor.computeOldLogProb(afterReward, metrics));
            Batch afterRef = timed("ref", timingRaw, () -> executor.computeReferenceLogProb(afterOldLogProb));
            Batch afterValues = timed("values", timingRaw, () -> executor.computeValues(afterRef));
            Batch afterAdvantages = timed("adv", timingRaw, () -> executor.computeAdvantages(afterValues, metrics));
            Batch afterFilter = timed("filter_groups", timingRaw,
                    () -> executor.filterEffectiveGroups(afterAdvantages, metrics));
            Batch aligned = timed("data_alignment", timingRaw, () -> runDataAlignment(executor, afterFilter, metrics));

            if (aligned.length() == 0) {
                metrics.put("training/skipped_empty_batch", 1);
                return aligned;
            }

            clampNonFinite(aligned);
            timedVoid("balance_batch", timingRaw, () -> executor.balanceBatch(aligned, metrics));
            timedVoid("update_critic", timingRaw, () -> executor.updateCritic(aligned, metrics));
            timedVoid("update_actor", timingRaw, () -> executor.updateActor(aligned, metrics));
            return aligned;
        });

        if (stepResult.length() == 0 && metrics.containsKey("training/skipped_empty_batch")) {
            return metrics;
        }
        Map<String, Object> processed = executor.processMetrics(stepResult, metrics, timingRaw);
        return processed != null ? processed : metrics;
    }

    /**
     * Drop long prompts and floor batch size to mini-batch size.
     */
    public static Batch runDataAlignment(TrainingExecutor executor, Batch batch, Map<String, Object> metrics) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }

        Batch current = batch;
        if (current.contains("is_drop_mask")) {
            List<Integer> keepIndices = keepIndices(current.get("is_drop_mask"), current.length());
            metrics.put("training/n_triplets_prompt_too_long", current.length() - keepIndices.size());
            current = current.select(keepIndices);
        }

        int miniBatchSize = executor.getMiniBatchSize();
        if (miniBatchSize <= 0) {
            throw new IllegalArgumentException("miniBatchSize must be positive");
        }

        int nTransition = current.length();
        if (nTransition % miniBatchSize != 0) {
            List<Integer> randomIndices = range(nTransition);
            Collections.shuffle(randomIndices);
            current = current.select(randomIndices);
            int nRemained = nTransition / miniBatchSize * miniBatchSize;
            current = current.select(range(nRemained));
            metrics.put("training/n_triplets_dropped_remainder", nTransition - nRemained);
        } else {
            metrics.put("training/n_triplets_dropped_remainder", 0);
        }
        return current;
    }

    /**
     * Clamp NaN/Inf in key tensors to zero before model update.
     */
    public static void clampNonFinite(Batch batch) {
        if (batch == null) {
            return;
        }
        for (String key : CLAMP_KEYS) {
            if (batch.getBatch().containsKey(key)) {
                batch.getBatch().put(key, clampValue(batch.getBatch().get(key)));
            }
        }
    }

    /**
     * Compute advantages using Generalized Advantage Estimation.
     */
    public static List<Double> computeAdvantages(List<Double> rewards, List<Double> values, double gamma, double lambda) {
        if (rewards == null || rewards.isEmpty()) {
            return new ArrayList<>();
        }
        if (values == null || values.size() < rewards.size()) {
            throw new IllegalArgumentException("values must have at least rewards.size() entries");
        }

        int n = rewards.size();
        List<Double> advantages = new ArrayList<>(Collections.nCopies(n, 0.0));
        double gae = 0.0;
        for (int t = n - 1; t >= 0; t--) {
            double nextValue = (t < n - 1) ? values.get(t + 1) : 0.0;
            double delta = rewards.get(t) + gamma * nextValue - values.get(t);
            gae = delta + gamma * lambda * gae;
            advantages.set(t, gae);
        }
        return advantages;
    }

    /**
     * Normalize advantages to mean zero and standard deviation one.
     */
    public static List<Double> normalizeAdvantages(List<Double> advantages) {
        if (advantages == null || advantages.isEmpty()) {
            return new ArrayList<>();
        }

        double mean = 0.0;
        for (Double advantage : advantages) {
            mean += advantage;
        }
        mean /= advantages.size();

        double variance = 0.0;
        for (Double advantage : advantages) {
            variance += (advantage - mean) * (advantage - mean);
        }
        double std = Math.sqrt(variance / advantages.size());

        List<Double> normalized = new ArrayList<>();
        for (Double advantage : advantages) {
            normalized.add(std > 0 ? (advantage - mean) / std : 0.0);
        }
        return normalized;
    }

    /**
     * Compute the PPO clipped objective for scalar tests and diagnostics.
     */
    public static double computeClippedObjective(double oldProb, double newProb, double advantage, double clipRatio) {
        double ratio = newProb / oldProb;
        double clipped = Math.max(Math.min(ratio, 1 + clipRatio), 1 - clipRatio);
        return Math.min(ratio * advantage, clipped * advantage);
    }

    private static List<Integer> keepIndices(Object mask, int fallbackLength) {
        int length = firstDimension(mask);
        int limit = length >= 0 ? length : fallbackLength;
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            if (!asBoolean(valueAt(mask, i))) {
                keep.add(i);
            }
        }
        return keep;
    }

    private static List<Integer> range(int size) {
        List<Integer> out = new ArrayList<>(Math.max(size, 0));
        for (int i = 0; i < size; i++) {
            out.add(i);
        }
        return out;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object valueAt(Object value, int index) {
        if (value instanceof List<?> list) {
            return list.get(index);
        }
        if (value != null && value.getClass().isArray()) {
            return Array.get(value, index);
        }
        return value;
    }

    private static int firstDimension(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value != null && value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return -1;
    }

    private static Object clampValue(Object value) {
        if (value instanceof Double number) {
            return Double.isFinite(number) ? number : 0.0;
        }
        if (value instanceof Float number) {
            return Float.isFinite(number) ? number : 0.0f;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(clampValue(item));
            }
            return out;
        }
        if (value != null && value.getClass().isArray()) {
            Class<?> component = value.getClass().getComponentType();
            int length = Array.getLength(value);
            Object out = Array.newInstance(component, length);
            for (int i = 0; i < length; i++) {
                Array.set(out, i, clampValue(Array.get(value, i)));
            }
            return out;
        }
        return value;
    }

    private static <T> T timed(String name, Map<String, Double> timingRaw, Operation<T> operation) {
        long start = System.nanoTime();
        try {
            return operation.run();
        } finally {
            timingRaw.merge(name, (System.nanoTime() - start) / 1_000_000_000.0, Double::sum);
        }
    }

    private static void timedVoid(String name, Map<String, Double> timingRaw, VoidOperation operation) {
        long start = System.nanoTime();
        try {
            operation.run();
        } finally {
            timingRaw.merge(name, (System.nanoTime() - start) / 1_000_000_000.0, Double::sum);
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run();
    }

    @FunctionalInterface
    private interface VoidOperation {
        void run();
    }
}
