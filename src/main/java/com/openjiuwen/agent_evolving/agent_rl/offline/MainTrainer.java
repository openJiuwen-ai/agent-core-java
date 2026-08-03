/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline;

import com.openjiuwen.agent_evolving.agent_rl.BackendProxy;
import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.RLBatchBuilder;
import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TrainingCoordinator;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RLMetricsTracker;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RolloutPersistence;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.PpoStep;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Training loop coordinator.
 *
 * <p>Mirrors Python's {@code MainTrainer} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/main_trainer.py}.</p>
 */
public class MainTrainer {

    private static final Logger LOGGER = Logger.getLogger(MainTrainer.class.getName());
    private static final int DEFAULT_LLM_TIMEOUT_SECONDS = 30_000;
    private static final int DEFAULT_TRAIN_BATCH_SIZE = 32;

    private final Object rlTrainer;
    private final Object config;
    private final RLMetricsTracker metricsTracker;
    private final RolloutPersistence persistence;
    private final Object agentFactory;
    private final BackendProxy proxy;

    private final Object trainDataset;
    private final Object valDataset;
    private final TrainingCoordinator trainingCoordinator;
    private final StatefulDataLoader trainDataloader;
    private final StatefulDataLoader valDataloader;

    private boolean proxyStarted;

    public MainTrainer(Object rlTrainer, Object config) {
        this(rlTrainer, config, null, null, null, null, null, null, null);
    }

    public MainTrainer(Object rlTrainer,
                       Object config,
                       BatchCollator collateFn) {
        this(rlTrainer, config, collateFn, null, null, null, null, null, null);
    }

    public MainTrainer(Object rlTrainer,
                       Object config,
                       BatchCollator collateFn,
                       RlSampler trainSampler,
                       Object agentFactory,
                       Function<Map<String, Object>, Map<String, Object>> taskDataFn,
                       Function<RolloutMessage, Map<String, Object>> rewardFn,
                       RLMetricsTracker metricsTracker,
                       RolloutPersistence persistence) {
        this.rlTrainer = Objects.requireNonNull(rlTrainer, "rlTrainer");
        this.config = Objects.requireNonNull(config, "config");
        this.metricsTracker = metricsTracker;
        this.persistence = persistence;
        this.agentFactory = agentFactory;
        this.trainDataset = readProperty(rlTrainer, "train_dataset", "trainDataset");
        this.valDataset = readProperty(rlTrainer, "val_dataset", "valDataset");

        this.trainingCoordinator = new TrainingCoordinator(
                config,
                readProperty(rlTrainer, "tokenizer"),
                persistence
        );
        this.trainingCoordinator.configureParallelExecutor(
                asRlTaskFunction(agentFactory),
                taskDataFn,
                rewardFn
        );

        this.proxy = new BackendProxy(
                readDouble(DEFAULT_LLM_TIMEOUT_SECONDS, config, "JiuwenRL", "llm_timeout_seconds"),
                "agentrl"
        );

        Object dataConfig = readProperty(config, "data");
        int numWorkers = readInt(0, dataConfig, "dataloader_num_workers");
        int trainBatchSize = readInt(DEFAULT_TRAIN_BATCH_SIZE, dataConfig, "train_batch_size");
        RlSampler effectiveSampler = trainSampler != null ? trainSampler : createRlSampler(dataConfig, trainDataset);
        this.trainDataloader = new StatefulDataLoader(
                trainDataset,
                trainBatchSize,
                numWorkers,
                true,
                collateFn,
                effectiveSampler,
                false
        );

        if (valDataset == null) {
            this.valDataloader = null;
        } else {
            int valBatchSize = datasetLength(valDataset) > 0 ? datasetLength(valDataset) : 1;
            this.valDataloader = new StatefulDataLoader(
                    valDataset,
                    valBatchSize,
                    numWorkers,
                    false,
                    collateFn,
                    null,
                    readBoolean(false, dataConfig, "validation_shuffle")
            );
        }
    }

    /**
     * Create a sampler for the RL dataset based on configuration.
     *
     * @param dataConfig RL data config
     * @param dataset target dataset
     * @return sequential or random sampler marker
     */
    public static RlSampler createRlSampler(Object dataConfig, Object dataset) {
        String samplerType = String.valueOf(readNested(dataConfig, "random", "sampler"));
        if ("sequential".equals(samplerType)) {
            return new RlSampler(SamplerType.SEQUENTIAL, dataset);
        }
        return new RlSampler(SamplerType.RANDOM, dataset);
    }

    public void stop() {
        if (proxyStarted) {
            proxy.stopSync();
            proxyStarted = false;
        }
        if (metricsTracker != null) {
            metricsTracker.finish();
        }
    }

    public String proxyUrl() {
        return proxy.getUrl();
    }

    public String getProxyUrl() {
        return proxyUrl();
    }

    public void updateBackends(Object servers) {
        ensureProxyStarted();
        proxy.updateBackendServers(servers);
        LOGGER.info(() -> "Update backends success: " + servers);
    }

    public Map<String, Object> validate() {
        if (valDataloader == null) {
            LOGGER.info("No validation dataset configured, skipping validation");
            return null;
        }

        List<String> serverAddresses = wakeUpRollout();
        updateBackends(serverAddresses);

        if (valDataloader.size() != 1) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_VALIDATION_DATASET_INVALID,
                    "error_msg",
                    "validation dataloader must yield exactly one batch, check val_batch_size config"
            );
        }

        Map<String, Object> validationRlData = valDataloader.iterator().next();
        Map<String, Object> validationMetrics = trainingCoordinator.validateSync(asColumnBatch(validationRlData));
        LOGGER.info(() -> String.format(
                Locale.ROOT,
                "Global step %d validation result: %s",
                getGlobalSteps(),
                validationMetrics
        ));

        if (metricsTracker != null) {
            metricsTracker.logValidation(getGlobalSteps(), scalarMetrics(validationMetrics));
        }

        if (persistence != null) {
            try {
                persistence.saveStepSummary(getGlobalSteps(), validationMetrics);
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.WARNING,
                        String.format(
                                Locale.ROOT,
                                "Failed to persist validation summary (step %d): %s",
                                getGlobalSteps(),
                                exception.getMessage()
                        ),
                        exception
                );
            }
        }

        sleepRollout();
        return validationMetrics;
    }

    public void fit() {
        invokeNoArgIfPresent(rlTrainer, "setup_logger", "setupLogger");
        setGlobalSteps(0);
        invokeNoArgIfPresent(rlTrainer, "load_checkpoint", "loadCheckpoint");

        int totalTrainingSteps = trainDataloader.size() * readInt(1, readProperty(config, "trainer"), "total_epochs");
        if (readBoolean(false, readProperty(config, "trainer"), "val_before_train")) {
            LOGGER.info("Validate before training.");
            validate();
        }

        int consecutiveZeroRewardSteps = 0;
        int totalEpochs = readInt(1, readProperty(config, "trainer"), "total_epochs");
        for (int epoch = 0; epoch < totalEpochs; epoch++) {
            for (Map<String, Object> batchDict : trainDataloader) {
                try {
                    LOGGER.info(() -> String.format(
                            Locale.ROOT,
                            "Training Started at step %d.",
                            getGlobalSteps()
                    ));

                    Object originBatch = invokeRequired(rlTrainer, "get_rl_format_data", "getRlFormatData", batchDict);
                    setGlobalSteps(getGlobalSteps() + 1);
                    boolean isLastStep = getGlobalSteps() >= totalTrainingSteps;

                    List<String> serverAddresses = wakeUpRollout();
                    updateBackends(serverAddresses);

                    Object device = readProperty(batchDict.get("fake_ids"), "device");
                    RLBatchBuilder.GeneratedRlBatch generatedBatch = trainingCoordinator.runDemonLoopSync(
                            asColumnBatch(batchDict),
                            device,
                            getGlobalSteps()
                    );
                    PpoStep.Batch batch = new PpoStep.Batch(
                            generatedBatch.batch().asMap(),
                            generatedBatch.nonTensorMetadata().asMap(),
                            generatedBatch.batch().batchSize()
                    );

                    sleepRollout();
                    Map<String, Object> metrics = invokeMetrics(originBatch, batch);

                    if (shouldTrigger(readInt(0, readProperty(config, "trainer"), "test_freq"), isLastStep,
                            getGlobalSteps())) {
                        validate();
                    }
                    if (shouldTrigger(readInt(0, readProperty(config, "trainer"), "save_freq"), isLastStep,
                            getGlobalSteps())) {
                        invokeNoArgIfPresent(rlTrainer, "save_checkpoint", "saveCheckpoint");
                    }

                    StepRolloutStats stats = collectRolloutStats();
                    emitRolloutLogs(stats);
                    Map<String, Object> rolloutStats = stats.toMetrics();

                    if (metricsTracker != null) {
                        metricsTracker.logRolloutStats(
                                getGlobalSteps(),
                                trainingCoordinator.getRewardsByUid(),
                                trainingCoordinator.getTotalPositive(),
                                trainingCoordinator.getTotalNegative(),
                                stats.totalTrainingSamples()
                        );
                        metricsTracker.logRewardDistribution(getGlobalSteps(), stats.allGlobalRewards());
                    }

                    consecutiveZeroRewardSteps = stats.averageReward() <= 0.0d
                            ? consecutiveZeroRewardSteps + 1
                            : 0;
                    if (consecutiveZeroRewardSteps >= 3) {
                        int warningSteps = consecutiveZeroRewardSteps;
                        LOGGER.warning(() -> String.format(
                                Locale.ROOT,
                                "*** REWARD COLLAPSE WARNING *** avg_reward <= 0 for %d consecutive steps "
                                        + "(step %d). The model may be degenerating. avg_turns=%.2f, reward_mean=%.4f",
                                warningSteps,
                                getGlobalSteps(),
                                trainingCoordinator.getLastAvgTurnCount(),
                                stats.averageReward()
                        ));
                    }

                    metrics.put("training/global_step", getGlobalSteps());
                    metrics.put("training/epoch", epoch);
                    metrics.put("training/avg_conversation_turns", trainingCoordinator.getLastAvgTurnCount());
                    metrics.put("training/rollout_reward_mean", stats.averageReward());
                    metrics.put("training/consecutive_zero_reward_steps", consecutiveZeroRewardSteps);
                    metrics.putAll(rolloutStats);

                    if (metricsTracker != null) {
                        metricsTracker.logTrainingStep(new RLMetricsTracker.TrainingStepMetrics(
                                getGlobalSteps(),
                                epoch,
                                metrics,
                                trainingCoordinator.getLastAvgTurnCount(),
                                stats.averageReward(),
                                consecutiveZeroRewardSteps
                        ));
                    } else {
                        invokeIfPresent(rlTrainer, List.of("log_metrics", "logMetrics"), metrics, getGlobalSteps());
                    }

                    if (persistence != null) {
                        try {
                            persistence.saveStepSummary(getGlobalSteps(), metrics);
                        } catch (RuntimeException exception) {
                            LOGGER.log(
                                    Level.WARNING,
                                    String.format(
                                            Locale.ROOT,
                                            "Failed to persist step summary (step %d): %s",
                                            getGlobalSteps(),
                                            exception.getMessage()
                                    ),
                                    exception
                            );
                        }
                    }

                    if (isLastStep) {
                        if (metricsTracker != null) {
                            metricsTracker.finish();
                        }
                        LOGGER.info(() -> "Training finished at step " + getGlobalSteps() + ".");
                        return;
                    }
                } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                    LOGGER.log(
                            Level.WARNING,
                            String.format(
                                    Locale.ROOT,
                                    "Empty or invalid batch at step %d, skipping this step: %s",
                                    getGlobalSteps(),
                                    exception.getMessage()
                            ),
                            exception
                    );
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.SEVERE, "Unexpected exception, save checkpoint and exit: " + exception.getMessage(),
                            exception);
                    invokeNoArgIfPresent(rlTrainer, "save_checkpoint", "saveCheckpoint");
                    throw exception;
                }
            }
        }
    }

    public Object getRlTrainer() {
        return rlTrainer;
    }

    public Object getConfig() {
        return config;
    }

    public Object getTrainDataset() {
        return trainDataset;
    }

    public Object getValDataset() {
        return valDataset;
    }

    public TrainingCoordinator getTrainingCoordinator() {
        return trainingCoordinator;
    }

    public StatefulDataLoader getTrainDataloader() {
        return trainDataloader;
    }

    public StatefulDataLoader getValDataloader() {
        return valDataloader;
    }

    public BackendProxy getProxy() {
        return proxy;
    }

    public boolean isProxyStarted() {
        return proxyStarted;
    }

    private void ensureProxyStarted() {
        if (proxyStarted) {
            return;
        }
        proxy.startSync();
        proxyStarted = true;
        if (agentFactory != null) {
            writeProperty(agentFactory, "proxy_url", "proxyUrl", proxy.getUrl());
        }
        LOGGER.info(() -> "BackendProxy started at " + proxy.getUrl());
    }

    private List<String> wakeUpRollout() {
        Object result = invokeIfPresent(rlTrainer, List.of("wake_up_rollout", "wakeUpRollout"));
        return stringList(result);
    }

    private void sleepRollout() {
        invokeNoArgIfPresent(rlTrainer, "sleep_rollout", "sleepRollout");
    }

    private int getGlobalSteps() {
        Object value = readProperty(rlTrainer, "global_steps", "globalSteps");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void setGlobalSteps(int value) {
        writeProperty(rlTrainer, "global_steps", "globalSteps", value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeMetrics(Object originBatch, PpoStep.Batch batch) {
        Object result = invokeRequired(rlTrainer, "train_step", "trainStep", originBatch, batch);
        if (result instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Function<RLTask, ?> asRlTaskFunction(Object agentFactory) {
        if (agentFactory instanceof Function<?, ?> function) {
            Function<RLTask, ?> typedFunction = (Function<RLTask, ?>) function;
            return typedFunction;
        }
        return null;
    }

    private static Map<String, Object> scalarMetrics(Map<String, Object> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metrics == null) {
            return out;
        }
        metrics.forEach((key, value) -> {
            if (value instanceof Number) {
                out.put(key, value);
            }
        });
        return out;
    }

    private static boolean shouldTrigger(int frequency, boolean isLastStep, int globalStep) {
        return frequency > 0 && (isLastStep || globalStep % frequency == 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ? extends List<?>> asColumnBatch(Map<String, Object> batch) {
        return (Map<String, ? extends List<?>>) (Map<?, ?>) batch;
    }

    private StepRolloutStats collectRolloutStats() {
        Map<String, List<Map<String, Object>>> rewardsByUid = trainingCoordinator.getRewardsByUid();
        List<Double> allGlobals = new ArrayList<>();
        for (List<Map<String, Object>> entries : rewardsByUid.values()) {
            for (Map<String, Object> entry : entries) {
                Double value = doubleValue(entry.get("global"));
                if (value != null) {
                    allGlobals.add(value);
                }
            }
        }

        double avgReward = average(allGlobals);
        int sampleCount = trainingCoordinator.getLastTrainingSampleCount() > 0
                ? trainingCoordinator.getLastTrainingSampleCount()
                : allGlobals.size();
        return new StepRolloutStats(rewardsByUid, allGlobals, avgReward, sampleCount);
    }

    private void emitRolloutLogs(StepRolloutStats stats) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : stats.rewardsByUid().entrySet()) {
            List<String> rolloutStrings = new ArrayList<>();
            for (Map<String, Object> rewardEntry : entry.getValue()) {
                rolloutStrings.add("global=" + rewardEntry.get("global") + ", per_turn=" + rewardEntry.get("per_turn"));
            }
            LOGGER.info(() -> String.format(
                    Locale.ROOT,
                    "Step %d uid=%s  rollouts:%n  %s",
                    getGlobalSteps(),
                    entry.getKey(),
                    String.join("\n  ", rolloutStrings)
            ));
        }
        LOGGER.info(() -> String.format(
                Locale.ROOT,
                "Step %d reward_mean=%.4f  n_uids=%d  n_rollouts=%d  n_training_samples=%d",
                getGlobalSteps(),
                stats.averageReward(),
                stats.rewardsByUid().size(),
                stats.allGlobalRewards().size(),
                stats.totalTrainingSamples()
        ));
    }

    private static double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> out = new ArrayList<>();
            for (Object item : iterable) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<String> out = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                out.add(String.valueOf(Array.get(value, index)));
            }
            return out;
        }
        return List.of(String.valueOf(value));
    }

    private static Object invokeRequired(Object target, String snakeName, String camelName, Object... args) {
        Object result = invokeIfPresent(target, List.of(snakeName, camelName), args);
        if (result == MissingValue.INSTANCE) {
            throw new IllegalArgumentException("target does not expose method " + snakeName + " / " + camelName);
        }
        return result;
    }

    private static void invokeNoArgIfPresent(Object target, String snakeName, String camelName) {
        Object ignored = invokeIfPresent(target, List.of(snakeName, camelName));
    }

    private static Object invokeIfPresent(Object target, List<String> methodNames, Object... args) {
        if (target == null) {
            return MissingValue.INSTANCE;
        }
        for (String methodName : methodNames) {
            Method method = findCompatibleMethod(target.getClass(), methodName, args);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("failed to invoke " + methodName, exception);
            }
        }
        return MissingValue.INSTANCE;
    }

    private static Method findCompatibleMethod(Class<?> type, String methodName, Object[] args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (isCompatible(method.getParameterTypes(), args)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            if (args[index] == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (wrap(parameterTypes[index]).isAssignableFrom(args[index].getClass())) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object readProperty(Object target, String... names) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
                String camel = toCamel(name);
                if (map.containsKey(camel)) {
                    return map.get(camel);
                }
            }
            return null;
        }
        for (String name : names) {
            String camel = toCamel(name);
            for (String methodName : List.of("get" + capitalize(camel), "is" + capitalize(camel), camel, name)) {
                Object value = invokeIfPresent(target, List.of(methodName));
                if (value != MissingValue.INSTANCE) {
                    return value;
                }
            }
            Field field = findField(target.getClass(), name, camel);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void writeProperty(Object target, String snakeName, String camelName, Object value) {
        if (target == null) {
            return;
        }
        if (target instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).put(snakeName, value);
            ((Map<Object, Object>) map).put(camelName, value);
            return;
        }
        String setterName = "set" + capitalize(camelName);
        Method setter = findCompatibleMethod(target.getClass(), setterName, new Object[] {value});
        if (setter != null) {
            try {
                setter.setAccessible(true);
                setter.invoke(target, value);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Field field = findField(target.getClass(), snakeName, camelName);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(target, value);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static Field findField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object readNested(Object root, Object defaultValue, String... path) {
        Object current = root;
        for (String part : path) {
            current = readProperty(current, part);
            if (current == null) {
                return defaultValue;
            }
        }
        return current;
    }

    private static int readInt(int defaultValue, Object root, String... path) {
        Object value = readNested(root, defaultValue, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static double readDouble(double defaultValue, Object root, String... path) {
        Object value = readNested(root, defaultValue, path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static boolean readBoolean(boolean defaultValue, Object root, String... path) {
        Object value = readNested(root, defaultValue, path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int datasetLength(Object dataset) {
        if (dataset == null) {
            return 0;
        }
        if (dataset instanceof List<?> list) {
            return list.size();
        }
        if (dataset instanceof Map<?, ?> map) {
            return map.size();
        }
        if (dataset.getClass().isArray()) {
            return Array.getLength(dataset);
        }
        Object length = invokeIfPresent(dataset, List.of("size", "length", "__len__"));
        return length instanceof Number number ? number.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMapRow(Object row) {
        if (row instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("value", row);
        return wrapped;
    }

    private static Object rowAt(Object dataset, int index) {
        if (dataset instanceof List<?> list) {
            return list.get(index);
        }
        if (dataset != null && dataset.getClass().isArray()) {
            return Array.get(dataset, index);
        }
        if (dataset instanceof Map<?, ?> map) {
            Object value = map.containsKey(index) ? map.get(index) : map.get(String.valueOf(index));
            return value;
        }
        return invokeRequired(dataset, "get_item", "getItem", index);
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

    @FunctionalInterface
    public interface BatchCollator {
        Map<String, Object> collate(List<Map<String, Object>> rows);
    }

    public enum SamplerType {
        RANDOM,
        SEQUENTIAL
    }

    /**
     * Sampler marker matching Python's random/sequential sampler selection.
     *
     * <p>Mirrors Python's {@code _create_rl_sampler} output in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/main_trainer.py}.</p>
     */
    public record RlSampler(SamplerType samplerType, Object dataset) {
    }

    /**
     * Small Java equivalent for the stateful dataloader surface used by MainTrainer.
     *
     * <p>Mirrors Python's {@code StatefulDataLoader} usage in
     * {@code openjiuwen/agent_evolving/agent_rl/offline/main_trainer.py}.</p>
     */
    public static final class StatefulDataLoader implements Iterable<Map<String, Object>> {
        private final Object dataset;
        private final int batchSize;
        private final int numWorkers;
        private final boolean dropLast;
        private final BatchCollator collateFn;
        private final RlSampler sampler;
        private final boolean shuffle;

        public StatefulDataLoader(Object dataset,
                                  int batchSize,
                                  int numWorkers,
                                  boolean dropLast,
                                  BatchCollator collateFn,
                                  RlSampler sampler,
                                  boolean shuffle) {
            this.dataset = dataset;
            this.batchSize = Math.max(1, batchSize);
            this.numWorkers = Math.max(0, numWorkers);
            this.dropLast = dropLast;
            this.collateFn = collateFn;
            this.sampler = sampler;
            this.shuffle = shuffle;
        }

        public int size() {
            int length = datasetLength(dataset);
            if (length == 0) {
                return 0;
            }
            int full = length / batchSize;
            return dropLast || length % batchSize == 0 ? full : full + 1;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public int getNumWorkers() {
            return numWorkers;
        }

        public boolean isDropLast() {
            return dropLast;
        }

        public RlSampler getSampler() {
            return sampler;
        }

        public boolean isShuffle() {
            return shuffle;
        }

        @Override
        public Iterator<Map<String, Object>> iterator() {
            int length = datasetLength(dataset);
            List<Integer> indices = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                indices.add(index);
            }
            if (shuffle || sampler != null && sampler.samplerType() == SamplerType.RANDOM) {
                Collections.shuffle(indices);
            }
            List<Map<String, Object>> batches = new ArrayList<>();
            for (int start = 0; start < indices.size(); start += batchSize) {
                int end = Math.min(start + batchSize, indices.size());
                if (dropLast && end - start < batchSize) {
                    break;
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int cursor = start; cursor < end; cursor++) {
                    rows.add(asMapRow(rowAt(dataset, indices.get(cursor))));
                }
                batches.add(collateFn != null ? collateFn.collate(rows) : columnBatch(rows));
            }
            return batches.iterator();
        }

        private static Map<String, Object> columnBatch(List<Map<String, Object>> rows) {
            Map<String, Object> batch = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    @SuppressWarnings("unchecked")
                    List<Object> values = (List<Object>) batch.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
                    values.add(entry.getValue());
                }
            }
            return batch;
        }
    }

    private enum MissingValue {
        INSTANCE
    }

    private record StepRolloutStats(
            Map<String, List<Map<String, Object>>> rewardsByUid,
            List<Double> allGlobalRewards,
            double averageReward,
            int totalTrainingSamples
    ) {
        private StepRolloutStats {
            rewardsByUid = Map.copyOf(rewardsByUid);
            allGlobalRewards = List.copyOf(allGlobalRewards);
        }

        private Map<String, Object> toMetrics() {
            Map<String, Object> metrics = new LinkedHashMap<>();
            if (allGlobalRewards.isEmpty()) {
                return metrics;
            }
            double max = allGlobalRewards.stream().mapToDouble(Double::doubleValue).max().orElse(0.0d);
            double min = allGlobalRewards.stream().mapToDouble(Double::doubleValue).min().orElse(0.0d);
            double variance = allGlobalRewards.stream()
                    .mapToDouble(value -> Math.pow(value - averageReward, 2))
                    .average()
                    .orElse(0.0d);
            metrics.put("rollout/reward_mean", averageReward);
            metrics.put("rollout/reward_std", Math.sqrt(variance));
            metrics.put("rollout/reward_max", max);
            metrics.put("rollout/reward_min", min);
            metrics.put("rollout/total_rollouts", totalTrainingSamples);
            metrics.put("rollout/unique_prompts", rewardsByUid.size());
            return metrics;
        }
    }
}
