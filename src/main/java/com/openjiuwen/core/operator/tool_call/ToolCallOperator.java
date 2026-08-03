/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Mirrors Python's {@code ToolCallOperator} in
 * {@code openjiuwen/core/operator/tool_call/base.py}.
 */
public final class ToolCallOperator extends Operator {

    private final String operatorId;
    private final BiConsumer<String, Object> onParameterUpdated;
    private final Map<String, String> descriptions = new LinkedHashMap<>();

    public ToolCallOperator(String operatorId) {
        this(operatorId, null, null);
    }

    public ToolCallOperator(String operatorId, Map<String, String> descriptions) {
        this(operatorId, descriptions, null);
    }

    public ToolCallOperator(String operatorId,
                            Map<String, String> descriptions,
                            BiConsumer<String, Object> onParameterUpdated) {
        this.operatorId = operatorId;
        this.onParameterUpdated = onParameterUpdated;
        if (descriptions != null) {
            this.descriptions.putAll(descriptions);
        }
    }

    @Override
    public String getOperatorId() {
        return operatorId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        if (descriptions.isEmpty()) {
            return Map.of();
        }
        return Map.of(
                "tool_description",
                new TunableSpec(
                        "tool_description",
                        "text",
                        "tool_description",
                        Map.of("type", "dict")
                )
        );
    }

    @Override
    public void setParameter(String target, Object value) {
        if (!"tool_description".equals(target) || !(value instanceof Map<?, ?> descriptionMap)) {
            return;
        }
        descriptions.clear();
        for (Map.Entry<?, ?> entry : descriptionMap.entrySet()) {
            if (entry.getKey() != null) {
                descriptions.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        if (onParameterUpdated != null) {
            onParameterUpdated.accept("tool_description", new LinkedHashMap<>(descriptions));
        }
    }

    @Override
    public Map<String, Object> getState() {
        return Map.of("tool_description", new LinkedHashMap<>(descriptions));
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        Object value = state.get("tool_description");
        if (value instanceof Map<?, ?> descriptionMap) {
            setParameter("tool_description", descriptionMap);
        }
    }

    public Map<String, String> getDescriptions() {
        return new LinkedHashMap<>(descriptions);
    }
}
