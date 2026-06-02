/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.runtime.ParallelRuntimeExecutor;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Core loop coordinator handling task submission, rollout collection,
 * stop-condition checking, and construction of training-ready RL batches.
 * <p>
 * Mirrors Python's {@code TrainingCoordinator} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.training_coordinator}.
 */
public class TrainingCoordinator {

    private final Object config;
    private final Object tokenizer;
    private final Object persistence;
    private final TaskQueue datastore;
    private final ProcessorsRegistry processorsRegistry;
    private final RLBatchBuilder batchBuilder;
    private final RolloutEncoder encoder;
    private final boolean wholeTrajectory;
    private final Integer finalKeepPerPrompt;

    private int totalPositive;
    private int totalNegative;
    private int totalActivateNum;
    private final List<Map<String, Object>> roundState = new ArrayList<>();
    private final Map<String, Map<String, Object>> rolloutState = new LinkedHashMap<>();
    private final Map<String, List<RolloutWithReward>> positiveCache = new LinkedHashMap<>();
    private final Map<String, List<RolloutWithReward>> negativeCache = new LinkedHashMap<>();
    private final List<Integer> turnCounts = new ArrayList<>();
    private final List<List<Double>> rewardLists = new ArrayList<>();
    private final Map<String, List<Map<String, Object>>> rewardsByUid = new LinkedHashMap<>();

    private Object parallelExecutor;
    private int lastTrainingSampleCount;
    private double lastAvgTurnCount;

    public TrainingCoordinator(Object config, Object tokenizer, Object persistence) {
        this.config = config;
        this.tokenizer = tokenizer;
        this.persistence = persistence;
        this.datastore = new TaskQueue();
        this.processorsRegistry = new ProcessorsRegistry();
        this.encoder = new RolloutEncoder(tokenizer);
        this.wholeTrajectory = readBoolean(config, false, "JiuwenRL", "whole_trajectory");
        this.finalKeepPerPrompt = readOptionalInt(config, "JiuwenRL", "final_keep_per_prompt");

        int maxPromptLength = readInt(config, 3072, "data", "max_prompt_length");
        int maxResponseLength = readInt(config, 3072, "data", "max_response_length");
        int padTokenId = readTokenizerPadTokenId(tokenizer, readInt(config, 0, "data", "pad_token_id"));
        this.batchBuilder = new RLBatchBuilder(maxPromptLength, padTokenId, maxResponseLength);
    }

    /**
     * Submit a new task for rollout generation.
     *
     * @param task RL task to submit
     * @return task id
     */
    public String submitTask(RLTask task) {
        return datastore.queueTask(task);
    }

    /**
     * Submit a new task using prompt data.
     *
     * @param promptId prompt identifier
     * @param promptData prompt data
     * @return task id
     */
    public String submitPrompt(String promptId, Map<String, Object> promptData) {
        RLTask task = new RLTask(promptId, promptId, promptData, 0);
        return datastore.queueTask(task);
    }

    /**
     * Build the initial rollout task dictionary from RL data.
     *
     * @param rlData column-oriented RL data
     * @return tasks keyed by rollout task id
     */
    public Map<String, RLTask> buildInitialTasks(Map<String, ? extends List<?>> rlData) {
        Map<String, RLTask> tasks = new LinkedHashMap<>();
        if (rlData == null || rlData.isEmpty()) {
            return tasks;
        }
        int batchSize = rlData.values().iterator().next().size();
        int rolloutN = readInt(config, 1, "actor_rollout_ref", "rollout", "n");
        for (int i = 0; i < batchSize; i++) {
            String originTaskId = UUID.randomUUID().toString();
            for (int j = 0; j < rolloutN; j++) {
                String taskId = UUID.randomUUID().toString();
                Map<String, Object> sample = new LinkedHashMap<>();
                for (Map.Entry<String, ? extends List<?>> entry : rlData.entrySet()) {
                    List<?> values = entry.getValue();
                    sample.put(entry.getKey(), i < values.size() ? values.get(i) : null);
                }
                tasks.put(taskId, new RLTask(taskId, originTaskId, sample, 0));
            }
        }
        return tasks;
    }

    /**
     * Compatibility alias for callers that use Python's private method name.
     *
     * @param rlData column-oriented RL data
     * @return tasks keyed by rollout task id
     */
    public Map<String, RLTask> build_initial_tasks(Map<String, ? extends List<?>> rlData) {
        return buildInitialTasks(rlData);
    }

    /**
     * Collect completed rollouts from the datastore.
     *
     * @return collected rollout messages
     */
    public List<RolloutMessage> collectRollouts() {
        Map<String, RolloutMessage> rollouts = datastore.getRollouts();
        return new ArrayList<>(rollouts.values());
    }

    /**
     * Check whether all known tasks are finished and no datastore work remains.
     *
     * @return true when the coordinator has no unfinished task state
     */
    public boolean shouldStop() {
        boolean allFinished = datastore.isFinished();
        for (Map<String, Object> state : rolloutState.values()) {
            allFinished = allFinished && Boolean.TRUE.equals(state.get("finished"));
        }
        return allFinished;
    }

    /**
     * Build the training batch from positive/negative caches using the configured sampler.
     *
     * @return assembled RL batch
     */
    public Object buildBatch() {
        return buildRlBatchFromCaches(null).rlBatch();
    }

    /**
     * Java mirror of Python's `_build_rl_batch_from_caches`.
     *
     * @param device optional device marker; ignored by Java-native RLBatchBuilder
     * @return assembled batch plus merged rollout dictionary
     */
    public BatchBuildResult buildRlBatchFromCaches(Object device) {
        RolloutProcessors.SamplingResult sampled = sampleCaches();
        Map<String, List<RolloutWithReward>> merged = mergeCaches(
                sampled.positiveRollouts(),
                sampled.negativeRollouts()
        );
        RLBatchBuilder.RlBatchResult batch = batchBuilder.generateRlBatch(merged, device);
        this.lastTrainingSampleCount = batch.getBatchSize();
        return new BatchBuildResult(batch, merged);
    }

    /**
     * Merge positive and negative rollout caches into one dictionary keyed by UID.
     *
     * @param posCache positive rollout cache
     * @param negCache negative rollout cache
     * @return merged rollout cache
     */
    public static Map<String, List<RolloutWithReward>> mergeCaches(
            Map<String, List<RolloutWithReward>> posCache,
            Map<String, List<RolloutWithReward>> negCache) {
        Map<String, List<RolloutWithReward>> merged = new LinkedHashMap<>();
        Set<String> allKeys = new HashSet<>();
        if (posCache != null) {
            allKeys.addAll(posCache.keySet());
        }
        if (negCache != null) {
            allKeys.addAll(negCache.keySet());
        }
        for (String key : allKeys) {
            List<RolloutWithReward> combined = new ArrayList<>();
            if (posCache != null) {
                combined.addAll(posCache.getOrDefault(key, List.of()));
            }
            if (negCache != null) {
                combined.addAll(negCache.getOrDefault(key, List.of()));
            }
            merged.put(key, combined);
        }
        return merged;
    }

    /**
     * Reset rollout caches, counters, and datastore state for a fresh cycle.
     */
    public void clearUpData() {
        roundState.clear();
        rolloutState.clear();
        positiveCache.clear();
        negativeCache.clear();
        turnCounts.clear();
        rewardLists.clear();
        rewardsByUid.clear();
        totalPositive = 0;
        totalNegative = 0;
        totalActivateNum = 0;
        lastTrainingSampleCount = 0;
        lastAvgTurnCount = 0.0d;
        datastore.clear();
    }

    /**
     * Compatibility alias for Python-style callers.
     */
    public void clear_up_data() {
        clearUpData();
    }

    /**
     * Run one synchronous Java iteration over queued tasks and collected rollouts.
     */
    public void runIteration() {
        List<RLTask> pendingTasks = datastore.getPendingTasks();
        if (!pendingTasks.isEmpty()) {
            for (RLTask task : pendingTasks) {
                datastore.markInProcessing(task.getTaskId());
                rolloutState.computeIfAbsent(task.getOriginTaskId(), ignored -> initialRolloutState());
            }
        }

        List<RolloutMessage> rollouts = collectRollouts();
        if (rollouts.isEmpty()) {
            appendRoundState(0);
            return;
        }

        Map<String, List<RolloutWithReward>> collectedMdp = collectRoundMdp(rollouts);
        updateRolloutState(roundState.size(), collectedMdp);
    }

    /**
     * Basic parallel-executor injection hook retained for Java integrations.
     *
     * @param executor executor instance or agent factory
     */
    public void configureParallelExecutor(Object executor) {
        if (executor instanceof ParallelRuntimeExecutor runtimeExecutor) {
            this.parallelExecutor = runtimeExecutor;
            return;
        }
        if (executor instanceof Function<?, ?> function) {
            configureParallelExecutor(function, null, null);
            return;
        }
        this.parallelExecutor = executor;
    }

    /**
     * Inject runtime configuration into the lazily-created parallel executor.
     */
    public void configureParallelExecutor(Function<?, ?> agentFactory,
                                          Function<?, ?> taskDataFn,
                                          Function<?, ?> rewardFn) {
        ParallelRuntimeExecutor executor = ensureParallelExecutor();
        if (agentFactory != null) {
            executor.setAgentFactory(agentFactory);
        }
        if (taskDataFn != null) {
            executor.setTaskDataFn(taskDataFn);
        }
        if (rewardFn != null) {
            executor.setRewardFn(rewardFn);
        }
    }

    private ParallelRuntimeExecutor ensureParallelExecutor() {
        if (parallelExecutor instanceof ParallelRuntimeExecutor executor) {
            return executor;
        }
        int numWorkers = readInt(config, 1, "trainer", "runtime_parallel_num");
        ParallelRuntimeExecutor executor = new ParallelRuntimeExecutor(datastore, numWorkers);
        parallelExecutor = executor;
        return executor;
    }

    /**
     * Compatibility hook for tests that only need to record stats.
     *
     * @param turnCount turn count
     * @param rewards rewards collected for the rollout
     */
    public void addRoundStats(int turnCount, List<Double> rewards) {
        turnCounts.add(turnCount);
        rewardLists.add(new ArrayList<>(rewards == null ? List.of() : rewards));
    }

    /**
     * Get coordinator statistics.
     *
     * @return stats map
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_positive", totalPositive);
        stats.put("total_negative", totalNegative);
        stats.put("total_activate_num", totalActivateNum);
        stats.put("round_count", roundState.size());
        stats.put("queue_size", datastore.getQueueSize());
        stats.put("in_processing_count", datastore.getInProcessingCount());
        stats.put("rollout_count", datastore.getRolloutCount());
        stats.put("last_training_sample_count", lastTrainingSampleCount);
        stats.put("last_avg_turn_count", lastAvgTurnCount);
        return stats;
    }

    private Map<String, List<RolloutWithReward>> collectRoundMdp(List<RolloutMessage> rollouts) {
        Map<String, List<RolloutWithReward>> collectedMdp = new LinkedHashMap<>();
        for (RolloutMessage rollout : rollouts) {
            if (rollout.getRolloutInfo() == null || rollout.getRolloutInfo().isEmpty()) {
                continue;
            }
            String uid = rollout.getOriginTaskId() != null ? rollout.getOriginTaskId() : rollout.getTaskId();
            turnCounts.add(rollout.getRolloutInfo().size());
            if (rollout.getRewardList() != null && !rollout.getRewardList().isEmpty()) {
                rewardLists.add(new ArrayList<>(rollout.getRewardList()));
            }
            Double globalReward = rollout.getGlobalReward();
            if (globalReward == null && rollout.getRewardList() != null && !rollout.getRewardList().isEmpty()) {
                globalReward = rollout.getRewardList().get(rollout.getRewardList().size() - 1);
            }
            Map<String, Object> rewardEntry = new LinkedHashMap<>();
            rewardEntry.put("global", globalReward);
            rewardEntry.put("per_turn", rollout.getRewardList() == null ? List.of() : new ArrayList<>(rollout.getRewardList()));
            rewardsByUid.computeIfAbsent(uid, ignored -> new ArrayList<>()).add(rewardEntry);

            List<RolloutWithReward> encoded = wholeTrajectory
                    ? encoder.buildWholeTrajectory(rollout)
                    : encoder.build(rollout);
            collectedMdp.computeIfAbsent(uid, ignored -> new ArrayList<>()).addAll(encoded);
        }
        return collectedMdp;
    }

    private void updateRolloutState(int roundId, Map<String, List<RolloutWithReward>> collectedMdp) {
        String classifierName = readString(config, "default_classify_rollouts", "JiuwenRL", "custom_fn", "classifier");
        String validatorName = readString(config, "default_validate_stop", "JiuwenRL", "custom_fn", "validator");
        RolloutProcessors.ClassifierProcessor classifier = processorsRegistry.getClassifier(classifierName);
        int activeTask = 0;

        for (Map.Entry<String, List<RolloutWithReward>> entry : collectedMdp.entrySet()) {
            String taskId = entry.getKey();
            RolloutProcessors.RolloutPair pair = classifier.apply(entry.getValue());
            List<RolloutWithReward> positives = pair.positiveRollouts();
            List<RolloutWithReward> negatives = pair.negativeRollouts();
            activeTask++;
            totalActivateNum++;
            totalPositive += positives.size();
            totalNegative += negatives.size();

            if (!positives.isEmpty()) {
                positiveCache.computeIfAbsent(taskId, ignored -> new ArrayList<>()).addAll(positives);
            }
            if (!negatives.isEmpty()) {
                negativeCache.computeIfAbsent(taskId, ignored -> new ArrayList<>()).addAll(negatives);
            }

            List<RolloutWithReward> cachedPositives = positiveCache.getOrDefault(taskId, List.of());
            List<RolloutWithReward> cachedNegatives = negativeCache.getOrDefault(taskId, List.of());
            boolean finished = applyValidator(validatorName, cachedPositives, cachedNegatives);
            Map<String, Object> state = rolloutState.computeIfAbsent(taskId, ignored -> initialRolloutState());
            state.put("finished", finished);
            state.put("pos", numberValue(state.get("pos")) + positives.size());
            state.put("neg", numberValue(state.get("neg")) + negatives.size());
        }

        appendRoundState(roundId, activeTask);
        lastAvgTurnCount = turnCounts.isEmpty()
                ? 0.0d
                : turnCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0d);
    }

    private RolloutProcessors.SamplingResult sampleCaches() {
        String samplerName = readString(config, "default_sampling", "JiuwenRL", "custom_fn", "sampler");
        if ("sampling_ada".equals(samplerName) && finalKeepPerPrompt != null) {
            return RolloutProcessors.samplingAda(positiveCache, negativeCache, finalKeepPerPrompt);
        }
        return processorsRegistry.getSampler(samplerName).apply(positiveCache, negativeCache);
    }

    private boolean applyValidator(
            String validatorName,
            List<RolloutWithReward> positives,
            List<RolloutWithReward> negatives) {
        if ("validate_stop_balanced".equals(validatorName) && finalKeepPerPrompt != null) {
            return RolloutProcessors.validateStopBalanced(positives, negatives, finalKeepPerPrompt);
        }
        return processorsRegistry.getValidator(validatorName).apply(positives, negatives);
    }

    private static Map<String, Object> initialRolloutState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("neg", 0);
        state.put("pos", 0);
        state.put("finished", false);
        return state;
    }

    private void appendRoundState(int activeTask) {
        appendRoundState(roundState.size(), activeTask);
    }

    private void appendRoundState(int roundId, int activeTask) {
        Map<String, Object> roundInfo = new LinkedHashMap<>();
        roundInfo.put("round_id", roundId);
        roundInfo.put("active_num_this_round", activeTask);
        roundInfo.put("_total_activate_num", totalActivateNum);
        roundState.add(roundInfo);
    }

    private static int readTokenizerPadTokenId(Object tokenizer, int defaultValue) {
        Object value = readProperty(tokenizer, "pad_token_id");
        if (value == null) {
            value = readProperty(tokenizer, "padTokenId");
        }
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Object readNested(Object root, Object defaultValue, String... path) {
        Object current = root;
        for (String part : path) {
            if (current == null) {
                return defaultValue;
            }
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
            } else {
                current = readProperty(current, part);
            }
        }
        return current == null ? defaultValue : current;
    }

    private static int readInt(Object root, int defaultValue, String... path) {
        Object value = readNested(root, defaultValue, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static Integer readOptionalInt(Object root, String... path) {
        Object value = readNested(root, null, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean readBoolean(Object root, boolean defaultValue, String... path) {
        Object value = readNested(root, defaultValue, path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String readString(Object root, String defaultValue, String... path) {
        Object value = readNested(root, defaultValue, path);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static Object readProperty(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        String camel = toCamel(name);
        for (String methodName : List.of("get" + capitalize(camel), "is" + capitalize(camel), camel, name)) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Method method = type.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                } catch (ReflectiveOperationException ignored) {
                    break;
                }
            }
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String fieldName : List.of(camel, name)) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try next field or superclass.
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String toCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public Object getConfig() { return config; }
    public Object getTokenizer() { return tokenizer; }
    public Object getPersistence() { return persistence; }
    public TaskQueue getDatastore() { return datastore; }
    public ProcessorsRegistry getProcessorsRegistry() { return processorsRegistry; }
    public RLBatchBuilder getBatchBuilder() { return batchBuilder; }
    public RolloutEncoder getEncoder() { return encoder; }
    public Object getParallelExecutor() { return parallelExecutor; }
    public boolean isWholeTrajectory() { return wholeTrajectory; }
    public int getTotalPositive() { return totalPositive; }
    public int getTotalNegative() { return totalNegative; }
    public int getTotalActivateNum() { return totalActivateNum; }
    public List<Map<String, Object>> getRoundState() { return List.copyOf(roundState); }
    public Map<String, Map<String, Object>> getRolloutState() { return Map.copyOf(rolloutState); }
    public Map<String, List<RolloutWithReward>> getPositiveCache() { return positiveCache; }
    public Map<String, List<RolloutWithReward>> getNegativeCache() { return negativeCache; }
    public List<Integer> getTurnCounts() { return List.copyOf(turnCounts); }
    public List<List<Double>> getRewardLists() { return List.copyOf(rewardLists); }
    public Map<String, List<Map<String, Object>>> getRewardsByUid() { return Map.copyOf(rewardsByUid); }
    public int getLastTrainingSampleCount() { return lastTrainingSampleCount; }
    public double getLastAvgTurnCount() { return lastAvgTurnCount; }

    public record BatchBuildResult(
            RLBatchBuilder.RlBatchResult rlBatch,
            Map<String, List<RolloutWithReward>> mergedRollouts) {
        public BatchBuildResult {
            Objects.requireNonNull(rlBatch, "rlBatch");
            mergedRollouts = Map.copyOf(mergedRollouts);
        }
    }
}
