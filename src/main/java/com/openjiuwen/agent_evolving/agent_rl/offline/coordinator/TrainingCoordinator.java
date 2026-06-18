/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.runtime.ParallelRuntimeExecutor;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core loop coordinator handling task submission, rollout collection,
 * stop-condition checking, and construction of training-ready RL batches.
 *
 * <p>Mirrors Python's {@code TrainingCoordinator} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/training_coordinator.py}.</p>
 */
public class TrainingCoordinator {

    private static final Logger LOGGER = Logger.getLogger(TrainingCoordinator.class.getName());
    private static final long DEFAULT_POLL_MILLIS = 1_000L;

    private final Object config;
    private final Object tokenizer;
    private final Object persistence;
    private final TaskQueue datastore;
    private final ProcessorsRegistry processorsRegistry;
    private final RLBatchBuilder batchBuilder;
    private final RolloutEncoder rolloutEncoder;
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

    private ParallelRuntimeExecutor parallelExecutor;
    private boolean parallelSetup;
    private boolean wholeTrajectory;
    private int currentStep;
    private int lastTrainingSampleCount;
    private double lastAvgTurnCount;

    public TrainingCoordinator(Object config, Object tokenizer) {
        this(config, tokenizer, null);
    }

    public TrainingCoordinator(Object config, Object tokenizer, Object persistence) {
        this.config = config;
        this.tokenizer = tokenizer;
        this.persistence = persistence;
        this.datastore = new TaskQueue();
        this.processorsRegistry = new ProcessorsRegistry();
        this.rolloutEncoder = new RolloutEncoder(tokenizer);
        this.finalKeepPerPrompt = readOptionalInt(config, "JiuwenRL", "final_keep_per_prompt");
        this.wholeTrajectory = readBoolean(config, false, "JiuwenRL", "whole_trajectory");
        this.batchBuilder = new RLBatchBuilder(
                readInt(config, 0, "data", "max_prompt_length"),
                readTokenizerPadTokenId(tokenizer, readInt(config, 0, "data", "pad_token_id")),
                readInt(config, 0, "data", "max_response_length")
        );

        if (wholeTrajectory) {
            LOGGER.info("Whole-trajectory training mode ENABLED");
        } else {
            LOGGER.info("Per-turn training mode ENABLED");
        }
    }

    /**
     * Run the full rollout-feedback-update demon loop.
     *
     * @param rlData column-oriented RL data
     * @param device target device marker used by batch assembly
     * @param step training step
     * @return asynchronous batch result
     */
    public CompletionStage<RLBatchBuilder.GeneratedRlBatch> runDemonLoop(
            Map<String, ? extends List<?>> rlData,
            Object device,
            int step) {
        return CompletableFuture.supplyAsync(() -> runDemonLoopSync(rlData, device, step));
    }

    /**
     * Compatibility overload matching the Python default step.
     */
    public CompletionStage<RLBatchBuilder.GeneratedRlBatch> runDemonLoop(
            Map<String, ? extends List<?>> rlData,
            Object device) {
        return runDemonLoop(rlData, device, 0);
    }

    /**
     * Synchronous wrapper for {@link #runDemonLoop(Map, Object, int)}.
     */
    public RLBatchBuilder.GeneratedRlBatch runDemonLoopSync(
            Map<String, ? extends List<?>> rlData,
            Object device,
            int step) {
        currentStep = step;
        LOGGER.info("Setting up RL trainer data to datastore.");
        setupParallel();
        Map<String, RLTask> tasks = buildInitialTasks(rlData);
        clearUpData();
        LOGGER.info("Starting RL trainer demon loop with parallel execution.");

        try {
            runRounds(tasks);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Error in demon loop: " + exception.getMessage(), exception);
            throw exception;
        } finally {
            stopParallelExecutorIfNeeded();
        }

        BatchBuildResult result = buildRlBatchFromCaches(device);
        lastTrainingSampleCount = result.rlBatch().batch().batchSize();
        lastAvgTurnCount = average(turnCounts);
        double rewardMean = averageDoubles(flattenRewards(rewardLists));
        LOGGER.info(() -> String.format(
                "Rollout done: %d rollouts, %d training samples, avg_turns=%.2f, reward_mean=%.4f",
                result.mergedRollouts().size(),
                lastTrainingSampleCount,
                lastAvgTurnCount,
                rewardMean
        ));
        return result.rlBatch();
    }

    public RLBatchBuilder.GeneratedRlBatch runDemonLoopSync(
            Map<String, ? extends List<?>> rlData,
            Object device) {
        return runDemonLoopSync(rlData, device, 0);
    }

    public CompletionStage<RLBatchBuilder.GeneratedRlBatch> run_demon_loop(
            Map<String, ? extends List<?>> rlData,
            Object device,
            int step) {
        return runDemonLoop(rlData, device, step);
    }

    public RLBatchBuilder.GeneratedRlBatch run_demon_loop_sync(
            Map<String, ? extends List<?>> rlData,
            Object device,
            int step) {
        return runDemonLoopSync(rlData, device, step);
    }

    /**
     * Perform a full validation pass using one batch of tasks.
     *
     * @param rlData validation data
     * @return validation metrics
     */
    public CompletionStage<Map<String, Object>> validate(Map<String, ? extends List<?>> rlData) {
        return CompletableFuture.supplyAsync(() -> validateSync(rlData));
    }

    public Map<String, Object> validateSync(Map<String, ? extends List<?>> rlData) {
        initializeParallelProcessing();
        clearUpData();
        Map<String, RLTask> tasks = new LinkedHashMap<>();
        int batchSize = batchSize(rlData);

        for (int index = 0; index < batchSize; index++) {
            String taskId = UUID.randomUUID().toString();
            tasks.put(taskId, newTask(taskId, taskId, sampleAt(rlData, index), 0));
        }

        Map<String, RolloutMessage> collectedData;
        try {
            submitTasksForRound(tasks);
            collectedData = waitForTasksCompletion(0, DEFAULT_POLL_MILLIS);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Error in validation: " + exception.getMessage(), exception);
            throw exception;
        } finally {
            stopParallelExecutorIfNeeded();
        }

        for (RolloutMessage rollout : collectedData.values()) {
            saveRollout("val", rollout);
        }

        List<Double> globalRewards = new ArrayList<>();
        List<Integer> turnNum = new ArrayList<>();
        List<List<Double>> rewards = new ArrayList<>();
        for (RolloutMessage rollout : collectedData.values()) {
            List<Double> rewardList = rollout.getRewardList() == null ? List.of() : rollout.getRewardList();
            rewards.add(new ArrayList<>(rewardList));
            turnNum.add(rollout.getRolloutInfo() == null ? 0 : rollout.getRolloutInfo().size());
            globalRewards.add(rollout.getGlobalReward());
        }

        long correctCount = globalRewards.stream()
                .filter(Objects::nonNull)
                .filter(reward -> reward >= 0.9d)
                .count();
        int totalCount = globalRewards.size();
        double accuracy = totalCount > 0 ? (double) correctCount / totalCount : 0.0d;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("val/global_reward_mean", averageNonNull(globalRewards));
        metrics.put("val/accuracy", accuracy);
        metrics.put("val/correct_count", correctCount);
        metrics.put("val/sample_count", totalCount);
        metrics.put("val/average_turn_num", averageInts(turnNum));
        metrics.put("val/turn_num", turnNum);
        metrics.put("val/reward_list", rewards);
        return metrics;
    }

    public Map<String, Object> validate_sync(Map<String, ? extends List<?>> rlData) {
        return validateSync(rlData);
    }

    /**
     * Merge positive and negative rollout caches into a unified map keyed by UID.
     */
    public static Map<String, List<RolloutWithReward>> mergeCaches(
            Map<String, List<RolloutWithReward>> posCache,
            Map<String, List<RolloutWithReward>> negCache) {
        Map<String, List<RolloutWithReward>> rolloutDict = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        if (posCache != null) {
            keys.addAll(posCache.keySet());
        }
        if (negCache != null) {
            keys.addAll(negCache.keySet());
        }
        for (String uid : keys) {
            List<RolloutWithReward> rollouts = new ArrayList<>();
            if (posCache != null) {
                rollouts.addAll(posCache.getOrDefault(uid, List.of()));
            }
            if (negCache != null) {
                rollouts.addAll(negCache.getOrDefault(uid, List.of()));
            }
            rolloutDict.put(uid, rollouts);
        }
        return rolloutDict;
    }

    /**
     * Reset rollout caches and counters for a fresh cycle.
     */
    public void clearUpData() {
        roundState.clear();
        rolloutState.clear();
        totalPositive = 0;
        totalNegative = 0;
        totalActivateNum = 0;
        positiveCache.clear();
        negativeCache.clear();
        turnCounts.clear();
        rewardLists.clear();
        rewardsByUid.clear();
        datastore.clear();
    }

    public void clear_up_data() {
        clearUpData();
    }

    public void configureParallelExecutor(
            Function<RLTask, ?> agentFactory,
            Function<Map<String, Object>, Map<String, Object>> taskDataFn,
            Function<RolloutMessage, Map<String, Object>> rewardFn) {
        setupParallelExecutor();
        if (agentFactory != null) {
            parallelExecutor.setAgentFactory(agentFactory);
        }
        if (taskDataFn != null) {
            parallelExecutor.setTaskDataFn(taskDataFn);
        }
        if (rewardFn != null) {
            parallelExecutor.setRewardFn(rewardFn);
        }
    }

    public void configure_parallel_executor(
            Function<RLTask, ?> agentFactory,
            Function<Map<String, Object>, Map<String, Object>> taskDataFn,
            Function<RolloutMessage, Map<String, Object>> rewardFn) {
        configureParallelExecutor(agentFactory, taskDataFn, rewardFn);
    }

    public Map<String, RLTask> buildInitialTasks(Map<String, ? extends List<?>> rlData) {
        Map<String, RLTask> tasks = new LinkedHashMap<>();
        int batchSize = batchSize(rlData);
        int rolloutN = readInt(config, 1, "actor_rollout_ref", "rollout", "n");

        for (int index = 0; index < batchSize; index++) {
            String originTaskId = UUID.randomUUID().toString();
            for (int rolloutIndex = 0; rolloutIndex < rolloutN; rolloutIndex++) {
                String rolloutTaskId = UUID.randomUUID().toString();
                tasks.put(
                        rolloutTaskId,
                        newTask(rolloutTaskId, originTaskId, sampleAt(rlData, index), 0)
                );
            }
        }
        return tasks;
    }

    public Map<String, RLTask> build_initial_tasks(Map<String, ? extends List<?>> rlData) {
        return buildInitialTasks(rlData);
    }

    private void setupParallelExecutor() {
        if (parallelSetup && parallelExecutor != null) {
            return;
        }
        int numWorkers = readInt(config, 1, "trainer", "runtime_parallel_num");
        parallelExecutor = new ParallelRuntimeExecutor(datastore, numWorkers);
        parallelSetup = true;
        LOGGER.fine(() -> "Parallel executor setup with " + numWorkers + " workers");
    }

    private void initializeParallelProcessing() {
        setupParallelExecutor();
        if (!parallelExecutor.isRunning()) {
            parallelExecutor.start();
            LOGGER.fine("Parallel executor started");
        }
    }

    private void setupParallel() {
        initializeParallelProcessing();
    }

    private void submitTasksForRound(Map<String, RLTask> tasks) {
        initializeTasks(new ArrayList<>(tasks.values()));
        LOGGER.fine(() -> "Submitted " + tasks.size() + " tasks for current round");
    }

    private void initializeTasks(List<RLTask> tasks) {
        for (RLTask task : tasks) {
            datastore.queueTask(task);
            rolloutState.computeIfAbsent(task.getOriginTaskId(), ignored -> initialRolloutState());
        }
    }

    private Map<String, RolloutMessage> waitForTasksCompletion(int roundId, long pollMillis) {
        LOGGER.fine(() -> "Waiting for tasks completion in round " + roundId);
        Map<String, RolloutMessage> collectedData = new LinkedHashMap<>();

        while (!datastore.isFinished()) {
            Map<String, RolloutMessage> currentData = datastore.getRollouts();
            if (!currentData.isEmpty()) {
                collectedData.putAll(currentData);
            }
            sleep(pollMillis);
        }

        Map<String, RolloutMessage> finalData = datastore.getRollouts();
        if (!finalData.isEmpty()) {
            collectedData.putAll(finalData);
        }
        LOGGER.info(() -> "Round " + roundId + ": collected " + collectedData.size() + " rollouts");
        return collectedData;
    }

    private void runRounds(Map<String, RLTask> tasks) {
        int maxRound = readInt(config, 1, "trainer", "rollout_max_round");
        Map<String, RLTask> remainingTasks = new LinkedHashMap<>(tasks);

        for (int roundId = 0; roundId < maxRound; roundId++) {
            if (remainingTasks.isEmpty()) {
                LOGGER.fine("All tasks finished, ending demon loop");
                break;
            }

            int taskCount = remainingTasks.size();
            int currentRound = roundId;
            LOGGER.info(() -> "Round " + currentRound + ": " + taskCount + " tasks");
            submitTasksForRound(remainingTasks);
            Map<String, List<RolloutWithReward>> collectedMdp = collectRoundMdp(roundId);
            updateRolloutState(roundId, collectedMdp);
            FilterResult filterResult = filterUnfinishedTasks(remainingTasks);
            remainingTasks = filterResult.unfinishedTasks();
            LOGGER.fine(() -> "Round " + currentRound + ": " + filterResult.finishedCount()
                    + " finished, " + filterResult.unfinishedTasks().size() + " remaining");
        }
    }

    private Map<String, List<RolloutWithReward>> collectRoundMdp(int roundId) {
        Map<String, RolloutMessage> collectedData = waitForTasksCompletion(roundId, DEFAULT_POLL_MILLIS);
        Map<String, List<RolloutWithReward>> collectedMdp = new LinkedHashMap<>();

        for (RolloutMessage rollout : collectedData.values()) {
            if (rollout.getRolloutInfo() == null || rollout.getRolloutInfo().isEmpty()) {
                continue;
            }
            turnCounts.add(rollout.getRolloutInfo().size());
            if (rollout.getRewardList() != null && !rollout.getRewardList().isEmpty()) {
                rewardLists.add(new ArrayList<>(rollout.getRewardList()));
            }
            String uid = rollout.getOriginTaskId();
            Double globalReward = rollout.getGlobalReward();
            if (globalReward == null && rollout.getRewardList() != null && !rollout.getRewardList().isEmpty()) {
                globalReward = rollout.getRewardList().get(rollout.getRewardList().size() - 1);
            }
            Map<String, Object> rewardEntry = new LinkedHashMap<>();
            rewardEntry.put("global", globalReward);
            rewardEntry.put("per_turn", rollout.getRewardList() == null ? List.of() : new ArrayList<>(rollout.getRewardList()));
            rewardsByUid.computeIfAbsent(uid, ignored -> new ArrayList<>()).add(rewardEntry);

            List<RolloutWithReward> encoded = wholeTrajectory
                    ? rolloutEncoder.buildWholeTrajectory(rollout)
                    : rolloutEncoder.build(rollout);
            collectedMdp.computeIfAbsent(uid, ignored -> new ArrayList<>()).addAll(encoded);
        }

        for (RolloutMessage rollout : collectedData.values()) {
            saveRollout("train", rollout);
        }
        return collectedMdp;
    }

    private void updateRolloutState(int roundId, Map<String, List<RolloutWithReward>> collectedMdp) {
        String classifierName = readString(config, "default_classify_rollouts", "JiuwenRL", "custom_fn", "classifier");
        String validatorName = readString(config, "validate_stop_balanced", "JiuwenRL", "custom_fn", "validator");
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

            if (!negatives.isEmpty()) {
                negativeCache.computeIfAbsent(taskId, ignored -> new ArrayList<>()).addAll(negatives);
            }
            if (!positives.isEmpty()) {
                positiveCache.computeIfAbsent(taskId, ignored -> new ArrayList<>()).addAll(positives);
            }

            boolean isFinished = applyValidator(
                    validatorName,
                    positiveCache.getOrDefault(taskId, List.of()),
                    negativeCache.getOrDefault(taskId, List.of())
            );
            Map<String, Object> state = rolloutState.computeIfAbsent(taskId, ignored -> initialRolloutState());
            state.put("finished", isFinished);
            state.put("pos", intValue(state.get("pos")) + positives.size());
            state.put("neg", intValue(state.get("neg")) + negatives.size());
        }

        Map<String, Object> round = new LinkedHashMap<>();
        round.put("round_id", roundId);
        round.put("active_num_this_round", activeTask);
        round.put("_total_activate_num", totalActivateNum);
        roundState.add(round);
    }

    private FilterResult filterUnfinishedTasks(Map<String, RLTask> tasks) {
        Map<String, RLTask> unfinishedTasks = new LinkedHashMap<>();
        for (Map.Entry<String, RLTask> entry : tasks.entrySet()) {
            RLTask task = entry.getValue();
            Map<String, Object> state = rolloutState.get(task.getOriginTaskId());
            if (state != null && !Boolean.TRUE.equals(state.get("finished"))) {
                task.setRoundNum(task.getRoundNum() + 1);
                unfinishedTasks.put(entry.getKey(), task);
            }
        }
        return new FilterResult(unfinishedTasks, tasks.size() - unfinishedTasks.size());
    }

    private void stopParallelExecutorIfNeeded() {
        if (parallelExecutor != null && parallelExecutor.isRunning()) {
            parallelExecutor.stop();
            LOGGER.fine("Parallel executor stopped");
        }
    }

    public BatchBuildResult buildRlBatchFromCaches(Object device) {
        RolloutProcessors.SamplingResult sampled = sampleCaches();
        Map<String, List<RolloutWithReward>> mergedDict = mergeCaches(
                sampled.positiveRollouts(),
                sampled.negativeRollouts()
        );
        RLBatchBuilder.GeneratedRlBatch rlBatch = batchBuilder.generateRlBatch(mergedDict, device);
        return new BatchBuildResult(rlBatch, mergedDict);
    }

    public BatchBuildResult build_rl_batch_from_caches(Object device) {
        return buildRlBatchFromCaches(device);
    }

    private RolloutProcessors.SamplingResult sampleCaches() {
        String samplerName = readString(config, "sampling_ada", "JiuwenRL", "custom_fn", "sampler");
        RolloutProcessors.SamplerProcessor sampler = processorsRegistry.getSampler(samplerName);
        if (finalKeepPerPrompt != null) {
            return sampler.apply(positiveCache, negativeCache, finalKeepPerPrompt);
        }
        return sampler.apply(positiveCache, negativeCache);
    }

    private boolean applyValidator(
            String validatorName,
            List<RolloutWithReward> positives,
            List<RolloutWithReward> negatives) {
        RolloutProcessors.ValidatorProcessor validator = processorsRegistry.getValidator(validatorName);
        if (finalKeepPerPrompt != null) {
            return validator.apply(positives, negatives, finalKeepPerPrompt);
        }
        return validator.apply(positives, negatives);
    }

    private void saveRollout(String phase, RolloutMessage rollout) {
        if (persistence == null || rollout == null) {
            return;
        }
        for (String methodName : List.of("save_rollout", "saveRollout")) {
            for (Method method : persistence.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                try {
                    if (method.getParameterCount() == 4) {
                        method.invoke(persistence, currentStep, rollout.getTaskId(), rollout, phase);
                        return;
                    }
                    if (method.getParameterCount() == 1) {
                        method.invoke(persistence, rollout);
                        return;
                    }
                } catch (ReflectiveOperationException exception) {
                    LOGGER.fine(() -> "Persistence save_rollout failed: " + exception.getMessage());
                }
            }
        }
    }

    private static RLTask newTask(String taskId, String originTaskId, Map<String, Object> sample, int roundNum) {
        RLTask task = new RLTask();
        task.setTaskId(taskId);
        task.setOriginTaskId(originTaskId);
        task.setTaskSample(sample);
        task.setRoundNum(roundNum);
        return task;
    }

    private static int batchSize(Map<String, ? extends List<?>> data) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        return data.values().iterator().next().size();
    }

    private static Map<String, Object> sampleAt(Map<String, ? extends List<?>> data, int index) {
        Map<String, Object> sample = new LinkedHashMap<>();
        if (data == null) {
            return sample;
        }
        for (Map.Entry<String, ? extends List<?>> entry : data.entrySet()) {
            List<?> values = entry.getValue();
            sample.put(entry.getKey(), values != null && index < values.size() ? values.get(index) : null);
        }
        return sample;
    }

    private static Map<String, Object> initialRolloutState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("neg", 0);
        state.put("pos", 0);
        state.put("finished", false);
        return state;
    }

    private static int readTokenizerPadTokenId(Object tokenizer, int defaultValue) {
        Object value = readProperty(tokenizer, "pad_token_id");
        if (value == null) {
            value = readProperty(tokenizer, "padTokenId");
        }
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static Object readNested(Object root, Object defaultValue, String... path) {
        Object current = root;
        for (String part : path) {
            if (current == null) {
                return defaultValue;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
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
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static Integer readOptionalInt(Object root, String... path) {
        Object value = readNested(root, null, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
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
                } catch (NoSuchMethodException exception) {
                    type = type.getSuperclass();
                } catch (ReflectiveOperationException exception) {
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
                } catch (NoSuchFieldException exception) {
                    // Try next spelling or superclass.
                } catch (ReflectiveOperationException exception) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double average(List<Integer> values) {
        return values.isEmpty() ? 0.0d : values.stream().mapToInt(Integer::intValue).average().orElse(0.0d);
    }

    private static double averageInts(List<Integer> values) {
        return average(values);
    }

    private static double averageNonNull(List<Double> values) {
        List<Double> nonNullValues = values.stream().filter(Objects::nonNull).toList();
        return nonNullValues.isEmpty()
                ? 0.0d
                : nonNullValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private static double averageDoubles(List<Double> values) {
        return values.isEmpty() ? 0.0d : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private static List<Double> flattenRewards(List<List<Double>> rewards) {
        List<Double> flattened = new ArrayList<>();
        for (List<Double> rewardList : rewards) {
            for (Double reward : rewardList) {
                flattened.add(reward == null ? 0.0d : reward);
            }
        }
        return flattened;
    }

    private static String toCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(character));
                upperNext = false;
            } else {
                builder.append(character);
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public Object getConfig() {
        return config;
    }

    public Object getTokenizer() {
        return tokenizer;
    }

    public Object getPersistence() {
        return persistence;
    }

    public TaskQueue getDatastore() {
        return datastore;
    }

    public ProcessorsRegistry getProcessorsRegistry() {
        return processorsRegistry;
    }

    public RLBatchBuilder getBatchBuilder() {
        return batchBuilder;
    }

    public RolloutEncoder getRolloutEncoder() {
        return rolloutEncoder;
    }

    public ParallelRuntimeExecutor getParallelExecutor() {
        return parallelExecutor;
    }

    public boolean isWholeTrajectory() {
        return wholeTrajectory;
    }

    public int getTotalPositive() {
        return totalPositive;
    }

    public int getTotalNegative() {
        return totalNegative;
    }

    public int getTotalActivateNum() {
        return totalActivateNum;
    }

    public List<Map<String, Object>> getRoundState() {
        return List.copyOf(roundState);
    }

    public Map<String, Map<String, Object>> getRolloutState() {
        return Map.copyOf(rolloutState);
    }

    public Map<String, List<RolloutWithReward>> getPositiveCache() {
        return positiveCache;
    }

    public Map<String, List<RolloutWithReward>> getNegativeCache() {
        return negativeCache;
    }

    public List<Integer> getTurnCounts() {
        return List.copyOf(turnCounts);
    }

    public List<List<Double>> getRewardLists() {
        return List.copyOf(rewardLists);
    }

    public Map<String, List<Map<String, Object>>> getRewardsByUid() {
        return Map.copyOf(rewardsByUid);
    }

    public int getLastTrainingSampleCount() {
        return lastTrainingSampleCount;
    }

    public double getLastAvgTurnCount() {
        return lastAvgTurnCount;
    }

    public record BatchBuildResult(
            RLBatchBuilder.GeneratedRlBatch rlBatch,
            Map<String, List<RolloutWithReward>> mergedRollouts) {
        public BatchBuildResult {
            Objects.requireNonNull(rlBatch, "rlBatch");
            mergedRollouts = Map.copyOf(mergedRollouts);
        }
    }

    private record FilterResult(Map<String, RLTask> unfinishedTasks, int finishedCount) {
        private FilterResult {
            unfinishedTasks = Map.copyOf(unfinishedTasks);
        }
    }
}
