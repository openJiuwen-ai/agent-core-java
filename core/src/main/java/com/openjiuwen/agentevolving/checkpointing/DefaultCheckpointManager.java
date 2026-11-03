/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.openjiuwen.agentevolving.experience.PendingChange;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default checkpoint manager implementation.
 *
 * <p>Mirrors Python's {@code DefaultCheckpointManager} in
 * {@code openjiuwen/agent_evolving/checkpointing/manager.py}.</p>
 */
public class DefaultCheckpointManager implements CheckpointManager {

    private final String runId;
    private final String checkpointVersion;
    private final int saveEveryNEpochs;
    private final boolean saveOnImprove;
    private final Map<String, List<PendingChange>> pending = new LinkedHashMap<>();

    public DefaultCheckpointManager() {
        this(null, "v1", 1, true);
    }

    public DefaultCheckpointManager(
            String runId,
            String checkpointVersion,
            int saveEveryNEpochs,
            boolean saveOnImprove
    ) {
        this.runId = runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId;
        this.checkpointVersion = checkpointVersion == null ? "v1" : checkpointVersion;
        this.saveEveryNEpochs = Math.max(saveEveryNEpochs, 1);
        this.saveOnImprove = saveOnImprove;
    }

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
        Map<String, Integer> step = new LinkedHashMap<>();
        step.put("epoch", readInt(progress, 0, "current_epoch", "currentEpoch"));
        step.put("batch", readInt(progress, 0, "current_batch_iter", "currentBatchIter"));

        Map<String, Object> best = new LinkedHashMap<>();
        best.put("best_score", readDouble(progress, 0.0d, "best_score", "bestScore"));

        Integer seed = readOptionalInt(progress, "seed");
        Map<String, Object> lastMetrics = new LinkedHashMap<>();
        lastMetrics.put("current_epoch_score", readDouble(progress, 0.0d, "current_epoch_score", "currentEpochScore"));

        return EvolveCheckpoint.builder()
                .version(checkpointVersion)
                .runId(runId)
                .step(step)
                .best(best)
                .seed(seed)
                .operatorsState(operatorsState)
                .updaterState(updaterState == null ? new LinkedHashMap<>() : new LinkedHashMap<>(updaterState))
                .searcherState(new LinkedHashMap<>())
                .lastMetrics(lastMetrics)
                .build();
    }

    @Override
    public Map<String, Object> restore(Object agent, EvolveCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        restoreOperatorsState(agent, checkpoint.getOperatorsState());
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> step = checkpoint.getStep();
        Map<String, Object> best = checkpoint.getBest();
        result.put("start_epoch", step == null ? 0 : coerceToInt(step.get("epoch"), 0));
        result.put("best_score", best == null ? 0.0d : coerceToDouble(best.get("best_score"), 0.0d));
        result.put("run_id", checkpoint.getRunId());
        return result;
    }

    public void addPending(String operatorId, PendingChange change) {
        pending.computeIfAbsent(operatorId, ignored -> new ArrayList<>()).add(change);
    }

    public List<PendingChange> getPending(String operatorId) {
        return new ArrayList<>(pending.getOrDefault(operatorId, List.of()));
    }

    public int commitPending(String operatorId, EvolutionStore store) {
        List<PendingChange> pendingList = pending.remove(operatorId);
        if (pendingList == null || pendingList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PendingChange change : pendingList) {
            count += change.getPayload().size();
        }
        return count;
    }

    public void discardPending(String operatorId, String changeId) {
        List<PendingChange> pendingList = pending.get(operatorId);
        if (pendingList == null) {
            return;
        }
        pendingList.removeIf(change -> Objects.equals(change.getChangeId(), changeId));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> snapshotOperatorsState(Object agent) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Object rawOperators = invokeAny(agent, "get_operators", "getOperators");
        if (!(rawOperators instanceof Map<?, ?> operators) || operators.isEmpty()) {
            return result;
        }
        for (Object value : operators.values()) {
            String operatorId = stringValue(readAny(value, "operator_id", "operatorId"));
            Object rawState = invokeAny(value, "get_state", "getState");
            if (operatorId == null || !(rawState instanceof Map<?, ?> state)) {
                continue;
            }
            Map<String, Object> copiedState = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : state.entrySet()) {
                copiedState.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            result.put(operatorId, copiedState);
        }
        return result;
    }

    private static void restoreOperatorsState(Object agent, Map<String, Map<String, Object>> operatorsState) {
        if (operatorsState == null || operatorsState.isEmpty()) {
            return;
        }
        Object rawOperators = invokeAny(agent, "get_operators", "getOperators");
        if (!(rawOperators instanceof Map<?, ?> operators) || operators.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> entry : operatorsState.entrySet()) {
            Object operator = operators.get(entry.getKey());
            if (operator != null) {
                invokeAny(operator, List.of(entry.getValue()), "load_state", "loadState");
            }
        }
    }

    private static int readInt(Object source, int fallback, String... names) {
        return coerceToInt(readAny(source, names), fallback);
    }

    private static Integer readOptionalInt(Object source, String... names) {
        Object value = readAny(source, names);
        return value == null ? null : coerceToInt(value, 0);
    }

    private static double readDouble(Object source, double fallback, String... names) {
        return coerceToDouble(readAny(source, names), fallback);
    }

    private static Object readAny(Object source, String... names) {
        if (source == null) {
            return null;
        }
        for (String name : names) {
            Object methodValue = invokeAny(source, "get" + upperFirst(toCamel(name)), name);
            if (methodValue != MissingValue.INSTANCE) {
                return methodValue;
            }
            Object fieldValue = readField(source, name);
            if (fieldValue != MissingValue.INSTANCE) {
                return fieldValue;
            }
        }
        return null;
    }

    private static Object invokeAny(Object target, String... methodNames) {
        return invokeAny(target, List.of(), methodNames);
    }

    private static Object invokeAny(Object target, List<Object> args, String... methodNames) {
        if (target == null) {
            return MissingValue.INSTANCE;
        }
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName, args.size());
            if (method == null) {
                continue;
            }
            try {
                if (!method.canAccess(target)) {
                    method.setAccessible(true);
                }
                return method.invoke(target, args.toArray());
            } catch (ReflectiveOperationException ignored) {
                return MissingValue.INSTANCE;
            }
        }
        return MissingValue.INSTANCE;
    }

    private static Method findMethod(Class<?> type, String methodName, int argCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == argCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                if (!field.canAccess(target)) {
                    field.setAccessible(true);
                }
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return MissingValue.INSTANCE;
    }

    private static String toCamel(String value) {
        if (!value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                builder.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String upperFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String stringValue(Object value) {
        return value == null || value == MissingValue.INSTANCE ? null : String.valueOf(value);
    }

    private static int coerceToInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double coerceToDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private enum MissingValue {
        INSTANCE
    }
}
