// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.rl_trainer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * VERL training executor.
 * <p>
 * Mirrors Python's {@code BaseVerlTrainingExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/rl_trainer/verl_executor.py}.
 */
public class VerlExecutor implements PpoStep.TrainingExecutor {

    private static volatile boolean initialized;
    private static volatile Object currentConfig;
    private static volatile VerlExecutor currentExecutor;

    private final Object config;
    private final TrainingBackend backend;
    private RolloutManager asyncRolloutManager;
    private CheckpointManager checkpointManager;
    private Object logger;
    private Map<String, Object> lastLoggedMetrics = Map.of();
    private int lastLoggedStep = -1;
    private int miniBatchSize;
    private int globalSteps;
    private int padSize;
    private boolean useReferencePolicy;
    private boolean useCritic;
    private boolean useRewardModel;
    private boolean balanceBatch;
    private int criticWarmup;
    private int gpuCount;

    public VerlExecutor(Object config) {
        this(config, new DefaultTrainingBackend());
    }

    public VerlExecutor(Object config, TrainingBackend backend) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.backend = backend != null ? backend : new DefaultTrainingBackend();
        this.miniBatchSize = intConfig(1, "actor_rollout_ref", "actor", "ppo_mini_batch_size");
        this.globalSteps = intConfig(0, "global_steps");
        this.useReferencePolicy = booleanConfig(false, "use_reference_policy");
        this.useCritic = booleanConfig(false, "use_critic");
        this.useRewardModel = booleanConfig(false, "use_rm");
        this.balanceBatch = booleanConfig(false, "trainer", "balance_batch");
        this.criticWarmup = intConfig(0, "trainer", "critic_warmup");
        this.gpuCount = intConfig(1, "resource_pool_manager", "n_gpus");
    }

    /**
     * Execute a training step with the statically initialized offline executor.
     */
    public static Object executeStep(Object batch) {
        VerlExecutor executor = currentExecutor;
        if (!initialized || executor == null) {
            throw new IllegalStateException("VerlExecutor is not initialized");
        }
        PpoStep.Batch dataBatch = executor.getRlFormatData(batch);
        return executor.trainStep(dataBatch, dataBatch);
    }

    /**
     * Initialize a backward-compatible offline VERL training executor.
     */
    public static void initialize(Object config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        currentConfig = config;
        currentExecutor = new OfflineVerlTrainingExecutor(config);
        initialized = true;
    }

    /**
     * Shutdown static training state.
     */
    public static void shutdown() {
        currentExecutor = null;
        currentConfig = null;
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Object getCurrentConfig() {
        return currentConfig;
    }

    public static Optional<VerlExecutor> getCurrentExecutor() {
        return Optional.ofNullable(currentExecutor);
    }

    public Object setupLogger() {
        this.logger = backend.setupLogger(this);
        return logger;
    }

    public void logMetrics(Map<String, Object> metrics, int step) {
        this.lastLoggedMetrics = new LinkedHashMap<>(metrics != null ? metrics : Map.of());
        this.lastLoggedStep = step;
        backend.logMetrics(this, this.lastLoggedMetrics, step);
    }

    public void saveCheckpoint() {
        backend.saveCheckpoint(this);
    }

    public void loadCheckpoint() {
        backend.loadCheckpoint(this);
    }

    public void sleepRollout() {
    }

    public List<String> wakeUpRollout() {
        return List.of();
    }

    public PpoStep.Batch getRlFormatData(Object batchData) {
        if (batchData instanceof PpoStep.Batch batch) {
            return batch;
        }
        if (batchData instanceof VerlConverter.DataProto proto) {
            return new PpoStep.Batch(proto.batch(), proto.nonTensors(), proto.length());
        }
        if (batchData instanceof Map<?, ?> map) {
            return new PpoStep.Batch(castMap(map));
        }
        throw new IllegalArgumentException("batch data must be a Map, DataProto, or PpoStep.Batch");
    }

    public Map<String, Object> trainStep(Object originBatch, Object batch) {
        return PpoStep.runPpoStep(this, originBatch, getRlFormatData(batch));
    }

    @Override
    public int getMiniBatchSize() {
        return miniBatchSize;
    }

    @Override
    public PpoStep.Batch computeBaseline(Object originBatch, PpoStep.Batch batch) {
        return backend.computeBaseline(this, originBatch, batch);
    }

    @Override
    public PpoStep.Batch computeReward(PpoStep.Batch batch, Map<String, Object> metrics) {
        return backend.computeReward(this, batch, metrics);
    }

    @Override
    public PpoStep.Batch computeOldLogProb(PpoStep.Batch batch, Map<String, Object> metrics) {
        return backend.computeOldLogProb(this, batch, metrics);
    }

    @Override
    public PpoStep.Batch computeReferenceLogProb(PpoStep.Batch batch) {
        return backend.computeReferenceLogProb(this, batch);
    }

    @Override
    public PpoStep.Batch computeValues(PpoStep.Batch batch) {
        return backend.computeValues(this, batch);
    }

    @Override
    public PpoStep.Batch computeAdvantages(PpoStep.Batch batch, Map<String, Object> metrics) {
        return backend.computeAdvantages(this, batch, metrics);
    }

    @Override
    public PpoStep.Batch filterEffectiveGroups(PpoStep.Batch batch, Map<String, Object> metrics) {
        return backend.filterEffectiveGroups(this, batch, metrics);
    }

    @Override
    public void balanceBatch(PpoStep.Batch batch, Map<String, Object> metrics) {
        backend.balanceBatch(this, batch, metrics);
    }

    @Override
    public void updateCritic(PpoStep.Batch batch, Map<String, Object> metrics) {
        backend.updateCritic(this, batch, metrics);
    }

    @Override
    public void updateActor(PpoStep.Batch batch, Map<String, Object> metrics) {
        backend.updateActor(this, batch, metrics);
    }

    @Override
    public Map<String, Object> processMetrics(PpoStep.Batch batch,
                                              Map<String, Object> metrics,
                                              Map<String, Double> timingRaw) {
        return backend.processMetrics(this, batch, metrics, timingRaw);
    }

    public Object getConfig() {
        return config;
    }

    public Object getLogger() {
        return logger;
    }

    public Map<String, Object> getLastLoggedMetrics() {
        return lastLoggedMetrics;
    }

    public int getLastLoggedStep() {
        return lastLoggedStep;
    }

    public int getGlobalSteps() {
        return globalSteps;
    }

    public void setGlobalSteps(int globalSteps) {
        this.globalSteps = globalSteps;
    }

    public int getPadSize() {
        return padSize;
    }

    public void setPadSize(int padSize) {
        this.padSize = Math.max(0, padSize);
    }

    public boolean isUseReferencePolicy() {
        return useReferencePolicy;
    }

    public void setUseReferencePolicy(boolean useReferencePolicy) {
        this.useReferencePolicy = useReferencePolicy;
    }

    public boolean isUseCritic() {
        return useCritic;
    }

    public void setUseCritic(boolean useCritic) {
        this.useCritic = useCritic;
    }

    public boolean isUseRewardModel() {
        return useRewardModel;
    }

    public void setUseRewardModel(boolean useRewardModel) {
        this.useRewardModel = useRewardModel;
    }

    public boolean isBalanceBatch() {
        return balanceBatch;
    }

    public void setBalanceBatch(boolean balanceBatch) {
        this.balanceBatch = balanceBatch;
    }

    public int getCriticWarmup() {
        return criticWarmup;
    }

    public void setCriticWarmup(int criticWarmup) {
        this.criticWarmup = Math.max(0, criticWarmup);
    }

    public int getGpuCount() {
        return gpuCount;
    }

    public void setGpuCount(int gpuCount) {
        this.gpuCount = Math.max(1, gpuCount);
    }

    public RolloutManager getAsyncRolloutManager() {
        return asyncRolloutManager;
    }

    public void setAsyncRolloutManager(RolloutManager asyncRolloutManager) {
        this.asyncRolloutManager = asyncRolloutManager;
    }

    public CheckpointManager getCheckpointManager() {
        return checkpointManager;
    }

    public void setCheckpointManager(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    protected int intConfig(int fallback, String... path) {
        Optional<Object> value = configValue(path);
        if (value.isEmpty()) {
            return fallback;
        }
        Object raw = value.get();
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    protected boolean booleanConfig(boolean fallback, String... path) {
        Optional<Object> value = configValue(path);
        if (value.isEmpty()) {
            return fallback;
        }
        Object raw = value.get();
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private Optional<Object> configValue(String... path) {
        Object current = config;
        for (String part : path) {
            Optional<Object> next = readProperty(current, part);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.ofNullable(current);
    }

    private static Optional<Object> readProperty(Object target, String property) {
        if (target instanceof Map<?, ?> map) {
            if (map.containsKey(property)) {
                return Optional.ofNullable(map.get(property));
            }
            return Optional.empty();
        }
        if (target == null) {
            return Optional.empty();
        }
        String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /**
     * Extension point for Java integrations that have real VERL workers.
     */
    public interface TrainingBackend {
        default Object setupLogger(VerlExecutor executor) {
            return new LinkedHashMap<String, Object>();
        }

        default void logMetrics(VerlExecutor executor, Map<String, Object> metrics, int step) {
        }

        default void saveCheckpoint(VerlExecutor executor) {
        }

        default void loadCheckpoint(VerlExecutor executor) {
        }

        PpoStep.Batch computeBaseline(VerlExecutor executor, Object originBatch, PpoStep.Batch batch);

        PpoStep.Batch computeReward(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        PpoStep.Batch computeOldLogProb(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        PpoStep.Batch computeReferenceLogProb(VerlExecutor executor, PpoStep.Batch batch);

        PpoStep.Batch computeValues(VerlExecutor executor, PpoStep.Batch batch);

        PpoStep.Batch computeAdvantages(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        PpoStep.Batch filterEffectiveGroups(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        void balanceBatch(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        void updateCritic(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        void updateActor(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics);

        Map<String, Object> processMetrics(VerlExecutor executor,
                                           PpoStep.Batch batch,
                                           Map<String, Object> metrics,
                                           Map<String, Double> timingRaw);
    }

    public interface RolloutManager {
        void sleep();

        void wakeUp();

        List<String> getServerAddresses();
    }

    public interface CheckpointManager {
        void sleepReplicas();

        void updateWeights(int globalSteps);
    }

    public static class OfflineVerlTrainingExecutor extends VerlExecutor {
        public OfflineVerlTrainingExecutor(Object config) {
            super(config);
        }

        public OfflineVerlTrainingExecutor(Object config, TrainingBackend backend) {
            super(config, backend);
        }

        @Override
        public void sleepRollout() {
            RolloutManager manager = getAsyncRolloutManager();
            if (manager != null) {
                manager.sleep();
            }
        }

        @Override
        public List<String> wakeUpRollout() {
            RolloutManager manager = getAsyncRolloutManager();
            if (manager == null) {
                return List.of();
            }
            manager.wakeUp();
            return manager.getServerAddresses();
        }
    }

    public static class OnlineVerlTrainingExecutor extends VerlExecutor {
        public OnlineVerlTrainingExecutor(Object config) {
            super(config);
        }

        public OnlineVerlTrainingExecutor(Object config, TrainingBackend backend) {
            super(config, backend);
        }

        @Override
        public void sleepRollout() {
            CheckpointManager manager = getCheckpointManager();
            if (manager != null) {
                manager.sleepReplicas();
            }
        }

        @Override
        public List<String> wakeUpRollout() {
            CheckpointManager manager = getCheckpointManager();
            if (manager != null) {
                manager.updateWeights(getGlobalSteps());
            }
            RolloutManager rolloutManager = getAsyncRolloutManager();
            return rolloutManager != null ? rolloutManager.getServerAddresses() : List.of();
        }
    }

    private static final class DefaultTrainingBackend implements TrainingBackend {
        @Override
        public PpoStep.Batch computeBaseline(VerlExecutor executor, Object originBatch, PpoStep.Batch batch) {
            return batch;
        }

        @Override
        public PpoStep.Batch computeReward(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            Object dataIds = batch.getNonTensors().get("data_id_list");
            if (dataIds != null) {
                batch.getNonTensors().put("uid", dataIds);
            }
            if (!batch.contains("response_mask")) {
                Object responses = batch.get("responses");
                Object mask = onesLikeFirstTwoDimensions(responses);
                if (mask instanceof int[][]) {
                    batch.getBatch().put("response_mask", mask);
                }
            }
            Object attentionMask = batch.get("attention_mask");
            if (attentionMask instanceof int[][] matrix) {
                List<Integer> globalTokenNum = new ArrayList<>();
                for (int[] row : matrix) {
                    int total = 0;
                    for (int item : row) {
                        total += item;
                    }
                    globalTokenNum.add(total);
                }
                batch.getNonTensors().put("global_token_num", globalTokenNum);
            }
            return batch;
        }

        @Override
        public PpoStep.Batch computeOldLogProb(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            if (!batch.contains("old_log_probs")) {
                batch.getBatch().put("old_log_probs", zerosLike(batch.get("response_mask")));
            }
            metrics.putIfAbsent("actor/entropy_loss", 0.0);
            return batch;
        }

        @Override
        public PpoStep.Batch computeReferenceLogProb(VerlExecutor executor, PpoStep.Batch batch) {
            if (executor.isUseReferencePolicy() && !batch.contains("ref_log_prob")) {
                batch.getBatch().put("ref_log_prob", zerosLike(batch.get("response_mask")));
            }
            return batch;
        }

        @Override
        public PpoStep.Batch computeValues(VerlExecutor executor, PpoStep.Batch batch) {
            if (executor.isUseCritic() && !batch.contains("values")) {
                batch.getBatch().put("values", new double[batch.length()]);
            }
            return batch;
        }

        @Override
        public PpoStep.Batch computeAdvantages(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            if (!batch.contains("token_level_rewards")) {
                Object tokenScores = batch.get("token_level_scores");
                batch.getBatch().put("token_level_rewards", tokenScores != null ? tokenScores : zerosLike(batch.get("response_mask")));
            }
            if (!batch.contains("advantages")) {
                batch.getBatch().put("advantages", rowSums(batch.get("token_level_rewards")));
            }
            NonFiniteCount count = countNonFinite(batch.get("advantages"));
            if (count.nanCount() > 0 || count.infCount() > 0) {
                metrics.put("training/advantage_nan_count", count.nanCount());
                metrics.put("training/advantage_inf_count", count.infCount());
            }
            PpoStep.clampNonFinite(batch);
            return batch;
        }

        @Override
        public PpoStep.Batch filterEffectiveGroups(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            if (!executor.booleanConfig(false, "algorithm", "filter_groups")) {
                return batch;
            }
            Object uidValue = batch.getNonTensors().get("uid");
            if (!(uidValue instanceof List<?> uids)) {
                return batch;
            }
            double[] perSampleReward = toDoubleArray(rowSums(batch.get("token_level_scores")));
            List<?> turnCounts = batch.getNonTensors().get("n_turns_list") instanceof List<?> turns ? turns : List.of();
            Map<Object, List<Integer>> grouped = new LinkedHashMap<>();
            for (int i = 0; i < uids.size(); i++) {
                grouped.computeIfAbsent(uids.get(i), ignored -> new ArrayList<>()).add(i);
            }

            List<Integer> kept = new ArrayList<>();
            int singleton = 0;
            int noVariance = 0;
            int noPositiveWithTool = 0;
            for (List<Integer> group : grouped.values()) {
                if (group.size() < 2) {
                    singleton++;
                    continue;
                }
                if (std(perSampleReward, group) < 1e-6) {
                    noVariance++;
                    continue;
                }
                if (!hasPositiveWithTool(perSampleReward, turnCounts, group)) {
                    noPositiveWithTool++;
                    continue;
                }
                kept.addAll(group);
            }
            Collections.sort(kept);
            metrics.put("training/filter_groups_total", grouped.size());
            metrics.put("training/filter_groups_kept", grouped.size() - singleton - noVariance - noPositiveWithTool);
            metrics.put("training/filter_groups_no_variance", noVariance);
            metrics.put("training/filter_groups_singleton", singleton);
            metrics.put("training/filter_groups_no_positive_with_tool", noPositiveWithTool);
            return kept.size() < batch.length() ? batch.select(kept) : batch;
        }

        @Override
        public void balanceBatch(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            if (executor.isBalanceBatch()) {
                metrics.put("training/balance_batch", 1);
            }
        }

        @Override
        public void updateCritic(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            if (executor.isUseCritic()) {
                metrics.put("critic/update_called", 1);
            }
        }

        @Override
        public void updateActor(VerlExecutor executor, PpoStep.Batch batch, Map<String, Object> metrics) {
            if (executor.getCriticWarmup() <= executor.getGlobalSteps()) {
                metrics.put("actor/update_called", 1);
            }
        }

        @Override
        public Map<String, Object> processMetrics(VerlExecutor executor,
                                                  PpoStep.Batch batch,
                                                  Map<String, Object> metrics,
                                                  Map<String, Double> timingRaw) {
            Map<String, Object> processed = new LinkedHashMap<>(metrics);
            processed.put("training/batch_size", batch.length());
            processed.put("training/n_gpus", executor.getGpuCount());
            processed.put("timing_raw", new LinkedHashMap<>(timingRaw));
            return processed;
        }

        private static Object onesLikeFirstTwoDimensions(Object reference) {
            if (reference instanceof int[][] matrix) {
                int[][] out = new int[matrix.length][];
                for (int i = 0; i < matrix.length; i++) {
                    out[i] = new int[matrix[i].length];
                    for (int j = 0; j < matrix[i].length; j++) {
                        out[i][j] = 1;
                    }
                }
                return out;
            }
            return new Object();
        }

        private static Object zerosLike(Object reference) {
            if (reference instanceof int[][] matrix) {
                double[][] out = new double[matrix.length][];
                for (int i = 0; i < matrix.length; i++) {
                    out[i] = new double[matrix[i].length];
                }
                return out;
            }
            if (reference instanceof double[][] matrix) {
                double[][] out = new double[matrix.length][];
                for (int i = 0; i < matrix.length; i++) {
                    out[i] = new double[matrix[i].length];
                }
                return out;
            }
            return new double[0][0];
        }

        private static Object rowSums(Object value) {
            if (value instanceof double[][] matrix) {
                double[] sums = new double[matrix.length];
                for (int i = 0; i < matrix.length; i++) {
                    double total = 0.0;
                    for (double item : matrix[i]) {
                        total += item;
                    }
                    sums[i] = total;
                }
                return sums;
            }
            if (value instanceof int[][] matrix) {
                double[] sums = new double[matrix.length];
                for (int i = 0; i < matrix.length; i++) {
                    int total = 0;
                    for (int item : matrix[i]) {
                        total += item;
                    }
                    sums[i] = total;
                }
                return sums;
            }
            if (value instanceof double[] array) {
                return array.clone();
            }
            return new double[0];
        }

        private static double[] toDoubleArray(Object value) {
            if (value instanceof double[] array) {
                return array;
            }
            return new double[0];
        }

        private static NonFiniteCount countNonFinite(Object value) {
            int nan = 0;
            int inf = 0;
            if (value instanceof double[] array) {
                for (double item : array) {
                    if (Double.isNaN(item)) {
                        nan++;
                    } else if (Double.isInfinite(item)) {
                        inf++;
                    }
                }
            }
            if (value instanceof double[][] matrix) {
                for (double[] row : matrix) {
                    for (double item : row) {
                        if (Double.isNaN(item)) {
                            nan++;
                        } else if (Double.isInfinite(item)) {
                            inf++;
                        }
                    }
                }
            }
            return new NonFiniteCount(nan, inf);
        }

        private static double std(double[] rewards, List<Integer> indices) {
            double mean = 0.0;
            for (int index : indices) {
                mean += rewards[index];
            }
            mean /= indices.size();
            double variance = 0.0;
            for (int index : indices) {
                double diff = rewards[index] - mean;
                variance += diff * diff;
            }
            return Math.sqrt(variance / indices.size());
        }

        private static boolean hasPositiveWithTool(double[] rewards, List<?> turnCounts, List<Integer> indices) {
            for (int index : indices) {
                int turns = 0;
                if (index < turnCounts.size() && turnCounts.get(index) instanceof Number number) {
                    turns = number.intValue();
                }
                if (rewards[index] >= 1.0 - 1e-6 && turns >= 2) {
                    return true;
                }
            }
            return false;
        }

        private record NonFiniteCount(int nanCount, int infCount) {
        }
    }
}
