/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Default checkpoint manager implementation.
 *
 * <p>Save timing: improved or every N epoch.
 * Restore content: operators_state + progress best/epoch.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.manager.DefaultCheckpointManager}.
 */
public class DefaultCheckpointManager implements CheckpointManager {

    private final String runId;
    private final String checkpointVersion;
    private final int saveEveryNEpochs;
    private final boolean saveOnImprove;

    /**
     * Create with default settings.
     */
    public DefaultCheckpointManager() {
        this(null, "v1", 1, true);
    }

    /**
     * Create with custom settings.
     *
     * @param runId                Unique run identifier (auto-generated if null)
     * @param checkpointVersion    Checkpoint version string
     * @param saveEveryNEpochs     Save checkpoint every N epochs
     * @param saveOnImprove        Save when validation score improves
     */
    public DefaultCheckpointManager(
            String runId,
            String checkpointVersion,
            int saveEveryNEpochs,
            boolean saveOnImprove
    ) {
        this.runId = runId != null && !runId.isEmpty() ? runId : UUID.randomUUID().toString();
        this.checkpointVersion = checkpointVersion != null ? checkpointVersion : "v1";
        this.saveEveryNEpochs = Math.max(saveEveryNEpochs, 1);
        this.saveOnImprove = saveOnImprove;
    }

    /**
     * Get the run ID.
     *
     * @return Run identifier
     */
    public String getRunId() {
        return runId;
    }

    @Override
    public boolean shouldSave(int epoch, boolean improved) {
        if (saveOnImprove && improved) {
            return true;
        }
        return epoch % saveEveryNEpochs == 0;
    }

    @Override
    public EvolveCheckpoint buildCheckpoint(Object agent, Object progress, Map<String, Object> updaterState) {
        Map<String, Map<String, Object>> operatorsState = snapshotOperatorsState(agent);

        Map<String, Integer> step = new HashMap<>();
        step.put("epoch", getIntProperty(progress, "currentEpoch", 0));
        step.put("batch", getIntProperty(progress, "currentBatchIter", 0));

        Map<String, Object> best = new HashMap<>();
        best.put("best_score", getDoubleProperty(progress, "bestScore", 0.0));

        Integer seed = getProperty(progress, "seed", Integer.class);

        Map<String, Object> lastMetrics = new HashMap<>();
        lastMetrics.put("current_epoch_score", getDoubleProperty(progress, "currentEpochScore", 0.0));

        return EvolveCheckpoint.builder()
                .version(checkpointVersion)
                .runId(runId)
                .step(step)
                .best(best)
                .seed(seed)
                .operatorsState(operatorsState)
                .updaterState(updaterState != null ? updaterState : new HashMap<>())
                .searcherState(new HashMap<>())
                .lastMetrics(lastMetrics)
                .build();
    }

    @Override
    public Map<String, Object> restore(Object agent, EvolveCheckpoint checkpoint) {
        restoreOperatorsState(agent, checkpoint.getOperatorsState());

        Map<String, Object> result = new HashMap<>();
        Map<?, ?> step = checkpoint.getStep();
        int startEpoch = coerceToInt(step != null ? step.get("epoch") : null, 0);
        double bestScore = getBestScore(checkpoint.getBest());
        String restoredRunId = checkpoint.getRunId();

        result.put("start_epoch", startEpoch);
        result.put("startEpoch", startEpoch);
        result.put("best_score", bestScore);
        result.put("bestScore", bestScore);
        result.put("run_id", restoredRunId);
        result.put("runId", restoredRunId);
        return result;
    }

    private double getBestScore(Map<String, Object> best) {
        if (best == null || best.isEmpty()) {
            return 0.0;
        }
        Object value = best.containsKey("best_score") ? best.get("best_score") : best.get("bestScore");
        return coerceToDouble(value, 0.0);
    }

    /**
     * Snapshot state of all evolvable operators.
     *
     * @param agent Agent instance
     * @return Map of operator_id -> state
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> snapshotOperatorsState(Object agent) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        try {
            Map<String, Object> operators = invokeMethod(agent, "getOperators", Map.class);
            if (operators == null || operators.isEmpty()) {
                return result;
            }
            for (Map.Entry<String, Object> entry : operators.entrySet()) {
                Object op = entry.getValue();
                String opId = invokeMethod(op, "getOperatorId", String.class);
                Map<String, Object> state = invokeMethod(op, "getState", Map.class);
                if (opId != null && state != null) {
                    result.put(opId, state);
                }
            }
        } catch (Exception e) {
            // Ignore if method not available
        }
        return result;
    }

    /**
     * Restore state of all evolving operators.
     *
     * @param agent           Agent instance
     * @param operatorsState  Operators state map
     */
    @SuppressWarnings("unchecked")
    private void restoreOperatorsState(Object agent, Map<String, Map<String, Object>> operatorsState) {
        if (operatorsState == null || operatorsState.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> operators = invokeMethod(agent, "getOperators", Map.class);
            if (operators == null) {
                return;
            }
            for (Map.Entry<String, Map<String, Object>> entry : operatorsState.entrySet()) {
                String operatorId = entry.getKey();
                Map<String, Object> state = entry.getValue();
                Object op = operators.get(operatorId);
                if (op != null && state != null) {
                    invokeMethod(op, "loadState", void.class, Map.class, state);
                }
            }
        } catch (Exception e) {
            // Ignore if method not available
        }
    }

    private int getIntProperty(Object obj, String property, int defaultValue) {
        try {
            Object value = invokeMethod(obj, "get" + capitalize(property), Object.class);
            return coerceToInt(value, defaultValue);
        } catch (Exception e) {
            // Ignore
        }
        return defaultValue;
    }

    private double getDoubleProperty(Object obj, String property, double defaultValue) {
        try {
            Object value = invokeMethod(obj, "get" + capitalize(property), Object.class);
            return coerceToDouble(value, defaultValue);
        } catch (Exception e) {
            // Ignore
        }
        return defaultValue;
    }

    private int coerceToInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double coerceToDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private <T> T getProperty(Object obj, String property, Class<T> type) {
        try {
            return invokeMethod(obj, "get" + capitalize(property), type);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeMethod(Object obj, String methodName, Class<T> returnType, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length / 2];
            Object[] params = new Object[args.length / 2];
            for (int i = 0; i < args.length; i += 2) {
                paramTypes[i / 2] = (Class<?>) args[i];
                params[i / 2] = args[i + 1];
            }
            java.lang.reflect.Method method = findMethod(obj.getClass(), methodName, paramTypes);
            if (!method.canAccess(obj)) {
                method.setAccessible(true);
            }
            return (T) method.invoke(obj, params);
        } catch (Exception e) {
            return null;
        }
    }

    private java.lang.reflect.Method findMethod(Class<?> type, String methodName, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
