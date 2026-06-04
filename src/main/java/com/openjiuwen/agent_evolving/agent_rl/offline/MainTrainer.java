/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.TrainingCoordinator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main training loop coordinator for RL training.
 * <p>
 * Orchestrates:
 * - VerlTrainingExecutor (PPO training)
 * - TrainingCoordinator (rollout generation and data assembly)
 * - DataLoaders for training and validation data
 * - BackendProxy (stable LLM inference URL for agents)
 * - Checkpointing, validation, and metrics logging
 * <p>
 * Mirrors Python's {@code MainTrainer} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.main_trainer}.
 */
public class MainTrainer {

    private static final Object ABSENT = new Object();
    private static final Map<String, Object> NO_VALIDATION_METRICS = null;

    private final Object rlTrainer;
    private final Map<String, Object> config;
    private final Object metricsTracker;
    private final Object persistence;
    private final Object agentFactory;
    private final Object trainingCoordinator;
    private final List<Object> trainDataset;
    private final List<Object> valDataset;
    private final LocalBackendProxy proxy;
    private final Map<String, Object> lastMetrics = new LinkedHashMap<>();
    private final Map<String, Object> lastValidationMetrics = new LinkedHashMap<>();

    private boolean proxyStarted;
    private int consecutiveZeroRewardSteps;

    public MainTrainer(
            Object rlTrainer,
            Map<String, Object> config,
            Object metricsTracker,
            Object persistence,
            Object agentFactory) {

        this.rlTrainer = rlTrainer;
        this.config = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
        this.metricsTracker = metricsTracker;
        this.persistence = persistence;
        this.agentFactory = agentFactory;
        this.trainDataset = asList(readProperty(rlTrainer, "trainDataset"));
        this.valDataset = asList(readProperty(rlTrainer, "valDataset"));
        this.trainingCoordinator = createTrainingCoordinator(this.config, persistence, agentFactory);
        this.proxy = new LocalBackendProxy(readInt(this.config, 30_000, "JiuwenRL", "llm_timeout_seconds"));
    }

    /**
     * Run the training loop for the requested epoch count.
     *
     * @param numEpochs number of training epochs
     */
    public void train(int numEpochs) {
        if (invokeIfPresent(rlTrainer, "setupLogger")) {
            // Python calls setup_logger before fit when available.
        }
        if (readProperty(rlTrainer, "globalSteps") == ABSENT) {
            writeProperty(rlTrainer, "globalSteps", 0);
        }
        invokeIfPresent(rlTrainer, "loadCheckpoint");

        for (int epoch = 0; epoch < numEpochs; epoch++) {
            runEpoch(epoch);
        }
        if (metricsTracker != null) {
            invokeIfPresent(metricsTracker, "finish");
        }
    }

    /**
     * Python-compatible training entry point.
     */
    public void fit() {
        train(readInt(config, 1, "trainer", "total_epochs"));
    }

    /**
     * Run a single training epoch over the Java dataset/list adapter.
     *
     * @param epoch epoch number
     */
    public void runEpoch(int epoch) {
        if (invokeIfPresent(rlTrainer, "runEpoch", epoch)) {
            return;
        }
        if (trainDataset.isEmpty()) {
            throw new IllegalStateException("trainDataset is not configured");
        }

        int batchIndex = 0;
        for (Object batchDict : trainDataset) {
            int nextStep = readIntProperty(rlTrainer, "globalSteps", 0) + 1;
            Object originBatch = invokeOrFallback(rlTrainer, "getRlFormatData", batchDict, batchDict);
            writeProperty(rlTrainer, "globalSteps", nextStep);

            Object servers = invokeOrFallback(rlTrainer, "wakeUpRollout", List.of());
            updateBackends(asStringList(servers));

            Object assembledBatch = runCoordinatorStep(batchDict, nextStep);
            Object trainBatch = invokeOrFallback(rlTrainer, "getRlFormatData", assembledBatch, assembledBatch);
            invokeIfPresent(rlTrainer, "sleepRollout");

            Object metricsObj = invokeRequired(rlTrainer, "trainStep", originBatch, trainBatch);
            Map<String, Object> metrics = toStringObjectMap(metricsObj);
            enrichTrainingMetrics(metrics, nextStep, epoch);
            logMetrics(metrics, nextStep);

            boolean isLastBatch = batchIndex == trainDataset.size() - 1;
            maybeValidate(nextStep, isLastBatch);
            maybeSaveCheckpoint(nextStep, isLastBatch);
            batchIndex++;
        }
    }

    /**
     * Run validation and return scalar/non-scalar metrics from the training coordinator.
     *
     * @return validation metrics or null when no validation dataset is configured
     */
    public Map<String, Object> validate() {
        if (valDataset.isEmpty()) {
            return NO_VALIDATION_METRICS;
        }

        Object servers = invokeOrFallback(rlTrainer, "wakeUpRollout", List.of());
        updateBackends(asStringList(servers));

        Object validationData = valDataset.size() == 1 ? valDataset.get(0) : new ArrayList<>(valDataset);
        Object rawMetrics = invokeOrFallback(trainingCoordinator, "validateSync", ABSENT, validationData);
        if (rawMetrics == ABSENT) {
            rawMetrics = invokeOrFallback(trainingCoordinator, "validate", Map.of(), validationData);
        }
        Map<String, Object> validationMetrics = toStringObjectMap(rawMetrics);
        lastValidationMetrics.clear();
        lastValidationMetrics.putAll(validationMetrics);

        int step = readIntProperty(rlTrainer, "globalSteps", 0);
        if (metricsTracker != null) {
            invokeIfPresent(metricsTracker, "logValidation", step, scalarMetrics(validationMetrics));
        }
        if (persistence != null) {
            invokeIfPresent(persistence, "saveStepSummary", step, validationMetrics);
        }
        invokeIfPresent(rlTrainer, "sleepRollout");
        return validationMetrics;
    }

    /**
     * Gracefully stop proxy and metrics tracking.
     */
    public void stop() {
        if (proxyStarted) {
            proxy.stop();
            proxyStarted = false;
        }
        if (metricsTracker != null) {
            invokeIfPresent(metricsTracker, "finish");
        }
    }

    /**
     * Return the proxy URL.
     *
     * @return proxy URL
     */
    public String getProxyUrl() {
        return proxy.getUrl();
    }

    /**
     * Alias matching Python's `proxy_url` property naming.
     *
     * @return proxy URL
     */
    public String proxyUrl() {
        return getProxyUrl();
    }

    /**
     * Update backend rollout server list on the proxy.
     *
     * @param servers backend server URLs
     */
    public void updateBackends(List<String> servers) {
        ensureProxyStarted();
        proxy.updateBackendServers(servers);
    }

    /**
     * Test/support hook mirroring Python test setup that marks proxy started.
     */
    public void simulateProxyAlreadyStarted() {
        proxyStarted = true;
    }

    /**
     * Save a checkpoint through the trainer.
     *
     * @param path checkpoint path
     */
    public void saveCheckpoint(String path) {
        if (invokeIfPresent(rlTrainer, "saveCheckpoint", path)) {
            return;
        }
        if (invokeIfPresent(rlTrainer, "saveCheckpoint")) {
            return;
        }
        throw new UnsupportedOperationException("rlTrainer does not expose saveCheckpoint");
    }

    /**
     * Load a checkpoint through the trainer.
     *
     * @param path checkpoint path
     */
    public void loadCheckpoint(String path) {
        if (invokeIfPresent(rlTrainer, "loadCheckpoint", path)) {
            return;
        }
        if (invokeIfPresent(rlTrainer, "loadCheckpoint")) {
            return;
        }
        throw new UnsupportedOperationException("rlTrainer does not expose loadCheckpoint");
    }

    /**
     * Return the latest known training, validation, and trainer metrics.
     *
     * @return metrics snapshot
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        Object trainerMetrics = invokeOrFallback(rlTrainer, "getMetrics", Map.of());
        metrics.putAll(toStringObjectMap(trainerMetrics));
        metrics.putAll(lastMetrics);
        if (!lastValidationMetrics.isEmpty()) {
            metrics.put("validation", new LinkedHashMap<>(lastValidationMetrics));
        }
        metrics.put("training/global_step", readIntProperty(rlTrainer, "globalSteps", 0));
        metrics.put("proxy/url", proxy.getUrl());
        metrics.put("proxy/started", proxyStarted);
        return metrics;
    }

    public Object getRlTrainer() { return rlTrainer; }
    public Map<String, Object> getConfig() { return Map.copyOf(config); }
    public Object getTrainingCoordinator() { return trainingCoordinator; }
    public Object getMetricsTracker() { return metricsTracker; }
    public Object getPersistence() { return persistence; }
    public Object getAgentFactory() { return agentFactory; }
    public List<Object> getTrainDataset() { return List.copyOf(trainDataset); }
    public List<Object> getValDataset() { return List.copyOf(valDataset); }
    public List<String> getBackendServers() { return proxy.getBackendServers(); }
    public boolean isProxyStarted() { return proxyStarted; }

    private Object runCoordinatorStep(Object batchDict, int step) {
        Object result = invokeOrFallback(trainingCoordinator, "runDemonLoopSync", ABSENT, batchDict, null, step);
        if (result == ABSENT) {
            result = invokeOrFallback(trainingCoordinator, "runDemonLoopSync", ABSENT, batchDict, step);
        }
        if (result == ABSENT) {
            result = invokeOrFallback(trainingCoordinator, "runDemonLoopSync", ABSENT, batchDict);
        }
        return result == ABSENT ? batchDict : result;
    }

    private void maybeValidate(int step, boolean isLastStep) {
        int testFreq = readInt(config, 0, "trainer", "test_freq");
        if (testFreq > 0 && (isLastStep || step % testFreq == 0)) {
            validate();
        }
    }

    private void maybeSaveCheckpoint(int step, boolean isLastStep) {
        int saveFreq = readInt(config, 0, "trainer", "save_freq");
        if (saveFreq > 0 && (isLastStep || step % saveFreq == 0)) {
            saveCheckpoint(null);
        }
    }

    private void ensureProxyStarted() {
        if (!proxyStarted) {
            proxy.startSync();
            proxyStarted = true;
            writeProperty(agentFactory, "proxyUrl", proxy.getUrl());
            writeProperty(agentFactory, "proxy_url", proxy.getUrl());
        }
    }

    private void enrichTrainingMetrics(Map<String, Object> metrics, int step, int epoch) {
        metrics.put("training/global_step", step);
        metrics.put("training/epoch", epoch);

        double rewardMean = readDouble(metrics.get("rollout/reward_mean"), 0.0d);
        if (rewardMean <= 0.0d) {
            consecutiveZeroRewardSteps++;
        } else {
            consecutiveZeroRewardSteps = 0;
        }
        metrics.put("training/consecutive_zero_reward_steps", consecutiveZeroRewardSteps);

        Object avgTurns = readProperty(trainingCoordinator, "lastAvgTurnCount");
        if (avgTurns != ABSENT) {
            metrics.put("training/avg_conversation_turns", avgTurns);
        }
        lastMetrics.clear();
        lastMetrics.putAll(metrics);
    }

    private void logMetrics(Map<String, Object> metrics, int step) {
        if (metricsTracker != null && invokeIfPresent(metricsTracker, "logTrainingStep", metrics)) {
            return;
        }
        if (metricsTracker != null && invokeIfPresent(metricsTracker, "logMetrics", metrics, step)) {
            return;
        }
        if (!invokeIfPresent(rlTrainer, "logMetrics", metrics, step)) {
            invokeIfPresent(rlTrainer, "log_metrics", metrics, step);
        }
        if (persistence != null) {
            invokeIfPresent(persistence, "saveStepSummary", step, metrics);
        }
    }

    private Object createTrainingCoordinator(Map<String, Object> cfg, Object persistenceObj, Object factory) {
        Object coordinator = readProperty(rlTrainer, "trainingCoordinator");
        if (coordinator != ABSENT) {
            return coordinator;
        }
        Object existing = cfg.get("trainingCoordinator");
        if (existing != null) {
            return existing;
        }
        Object tokenizer = readProperty(rlTrainer, "tokenizer");
        TrainingCoordinator created = new TrainingCoordinator(cfg, tokenizer == ABSENT ? null : tokenizer, persistenceObj);
        if (factory != null) {
            created.configureParallelExecutor(factory);
        }
        return created;
    }

    private static Map<String, Object> scalarMetrics(Map<String, Object> metrics) {
        Map<String, Object> scalar = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                scalar.put(entry.getKey(), entry.getValue());
            }
        }
        return scalar;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value == ABSENT || value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>((List<Object>) list);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                result.add(item);
            }
            return result;
        }
        if (value instanceof Object[] array) {
            List<Object> result = new ArrayList<>();
            Collections.addAll(result, array);
            return result;
        }
        return new ArrayList<>(List.of(value));
    }

    private static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value == ABSENT || value == null) {
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value instanceof Object[] array) {
            for (Object item : array) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        result.add(String.valueOf(value));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object readNested(Object root, Object defaultValue, String... path) {
        Object current = root;
        for (String part : path) {
            if (current == ABSENT || current == null) {
                return defaultValue;
            }
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).getOrDefault(part, ABSENT);
            } else {
                current = readProperty(current, part);
            }
        }
        return current == ABSENT || current == null ? defaultValue : current;
    }

    private static int readInt(Object root, int defaultValue, String... path) {
        Object value = readNested(root, defaultValue, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && value != ABSENT) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int readIntProperty(Object target, String name, int defaultValue) {
        Object value = readProperty(target, name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != ABSENT && value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double readDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != ABSENT && value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static Object readProperty(Object target, String name) {
        if (target == null || name == null) {
            return ABSENT;
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
                    // Try the next field shape or superclass.
                } catch (ReflectiveOperationException ignored) {
                    return ABSENT;
                }
            }
            type = type.getSuperclass();
        }
        return ABSENT;
    }

    private static void writeProperty(Object target, String name, Object value) {
        if (target == null || name == null) {
            return;
        }
        String camel = toCamel(name);
        String setter = "set" + capitalize(camel);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setter) && method.getParameterCount() == 1
                    && parametersCompatible(method.getParameterTypes(), new Object[] {value})) {
                try {
                    method.invoke(target, value);
                    return;
                } catch (ReflectiveOperationException ignored) {
                    return;
                }
            }
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String fieldName : List.of(camel, name)) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    // Try next field shape or superclass.
                } catch (ReflectiveOperationException ignored) {
                    return;
                }
            }
            type = type.getSuperclass();
        }
    }

    private static Object invokeRequired(Object target, String name, Object... args) {
        Object result = invokeOrFallback(target, name, ABSENT, args);
        if (result == ABSENT) {
            String targetName = target != null ? target.getClass().getName() : "<null>";
            throw new UnsupportedOperationException(targetName + " does not expose " + name);
        }
        return result;
    }

    private static boolean invokeIfPresent(Object target, String name, Object... args) {
        return invokeOrFallback(target, name, ABSENT, args) != ABSENT;
    }

    private static Object invokeOrFallback(Object target, String name, Object fallback, Object... args) {
        if (target == null) {
            return fallback;
        }
        Optional<Method> method = findMethod(target.getClass(), name, args);
        if (method.isEmpty()) {
            return fallback;
        }
        try {
            method.get().setAccessible(true);
            return method.get().invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke " + name, exception);
        }
    }

    private static Optional<Method> findMethod(Class<?> type, String name, Object[] args) {
        List<String> candidateNames = List.of(name, toCamel(name), toSnake(name));
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!candidateNames.contains(method.getName())) {
                    continue;
                }
                if (method.getParameterCount() != args.length) {
                    continue;
                }
                if (parametersCompatible(method.getParameterTypes(), args)) {
                    return Optional.of(method);
                }
            }
            current = current.getSuperclass();
        }
        return Optional.empty();
    }

    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) {
        boolean compatible = true;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null || args[i] == ABSENT) {
                continue;
            }
            Class<?> parameterType = wrap(parameterTypes[i]);
            if (!parameterType.isAssignableFrom(args[i].getClass())) {
                compatible = false;
                break;
            }
        }
        return compatible;
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
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
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

    private static String toSnake(String value) {
        if (value == null || value.indexOf('_') >= 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class LocalBackendProxy {
        private final int timeoutSeconds;
        private final List<String> backendServers = new ArrayList<>();
        private final String url = "http://127.0.0.1:0";
        private boolean running;

        private LocalBackendProxy(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        private void startSync() {
            running = true;
        }

        private void stop() {
            running = false;
        }

        private void updateBackendServers(List<String> servers) {
            backendServers.clear();
            backendServers.addAll(servers == null ? List.of() : servers);
        }

        private String getUrl() {
            return url;
        }

        private List<String> getBackendServers() {
            return List.copyOf(backendServers);
        }

        @SuppressWarnings("unused")
        private int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        @SuppressWarnings("unused")
        private boolean isRunning() {
            return running;
        }
    }
}
