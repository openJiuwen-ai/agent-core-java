/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import com.openjiuwen.agent_evolving.trajectory.UpdateKey;
import com.openjiuwen.core.operator.Operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared execution helpers for applying normalized evolution updates.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_evolving/update_execution.py}.</p>
 */
public final class UpdateExecution {

    private UpdateExecution() {
    }

    public static List<ApplyResult> executeUpdates(
            Map<String, ? extends Operator> operators,
            Map<UpdateKey, ?> updates
    ) {
        Map<String, ? extends Operator> safeOperators = Objects.requireNonNull(operators, "operators");
        Map<UpdateKey, ?> safeUpdates = Objects.requireNonNull(updates, "updates");
        Map<UpdateKey, Object> nonNullUpdates = new LinkedHashMap<>();
        List<UpdateKey> nullUpdateKeys = new ArrayList<>();

        for (Map.Entry<UpdateKey, ?> entry : safeUpdates.entrySet()) {
            if (entry.getValue() == null) {
                nullUpdateKeys.add(entry.getKey());
            } else {
                nonNullUpdates.put(entry.getKey(), entry.getValue());
            }
        }

        List<ApplyResult> results = new ArrayList<>();
        for (Map.Entry<UpdateKey, UpdateValue> entry : EvolutionTypes.normalizeUpdates(nonNullUpdates).entrySet()) {
            UpdateKey key = entry.getKey();
            String operatorId = key == null ? null : key.getOperatorId();
            String target = key == null ? null : key.getTarget();
            UpdateValue update = entry.getValue();
            Operator operator = safeOperators.get(operatorId);
            if (operator == null) {
                results.add(ApplyResult.builder()
                        .operatorId(operatorId)
                        .target(target)
                        .applied(false)
                        .mode(update.getMode())
                        .effect(update.getEffect())
                        .value(update.getPayload())
                        .changeType(update.getChangeType())
                        .errors(List.of("operator not found: " + operatorId))
                        .metadata(update.getMetadata())
                        .build());
                continue;
            }
            results.add(operator.applyUpdate(target, update));
        }

        for (UpdateKey key : nullUpdateKeys) {
            results.add(ApplyResult.builder()
                    .operatorId(key == null ? null : key.getOperatorId())
                    .target(key == null ? null : key.getTarget())
                    .applied(false)
                    .value(null)
                    .errors(List.of("update value is None"))
                    .build());
        }
        return List.copyOf(results);
    }

    public static List<ApplyResult> applyUpdates(
            Map<String, ? extends Operator> operators,
            Map<UpdateKey, ?> updates
    ) {
        return executeUpdates(operators, updates);
    }

    public static Map<String, Integer> summarizeApplyResults(Collection<ApplyResult> results) {
        List<ApplyResult> resultList = new ArrayList<>(Objects.requireNonNull(results, "results"));
        int applied = 0;
        for (ApplyResult result : resultList) {
            if (result != null && result.isApplied()) {
                applied++;
            }
        }
        return Map.of(
                "total", resultList.size(),
                "applied", applied,
                "failed", resultList.size() - applied
        );
    }
}
