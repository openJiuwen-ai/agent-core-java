/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Mirrors Python's {@code MemoryCallOperator} in
 * {@code openjiuwen/core/operator/memory_call/base.py}.
 */
public final class MemoryCallOperator extends Operator {

    private final String operatorId;
    private final BiConsumer<String, Object> onParameterUpdated;
    private boolean enabled = true;
    private int maxRetries;

    public MemoryCallOperator() {
        this("memory_call", null);
    }

    public MemoryCallOperator(String operatorId) {
        this(operatorId, null);
    }

    public MemoryCallOperator(String operatorId, BiConsumer<String, Object> onParameterUpdated) {
        this.operatorId = operatorId != null ? operatorId : "memory_call";
        this.onParameterUpdated = onParameterUpdated;
    }

    @Override
    public String getOperatorId() {
        return operatorId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        Map<String, TunableSpec> tunables = new LinkedHashMap<>();
        tunables.put("enabled", new TunableSpec(
                "enabled",
                "discrete",
                "enabled",
                Map.of("type", "bool")
        ));
        tunables.put("max_retries", new TunableSpec(
                "max_retries",
                "discrete",
                "max_retries",
                Map.of("type", "int", "min", 0, "max", 5)
        ));
        return tunables;
    }

    @Override
    public void setParameter(String target, Object value) {
        if ("enabled".equals(target)) {
            enabled = value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
            notifyUpdated("enabled", enabled);
        } else if ("max_retries".equals(target)) {
            maxRetries = clampRetries(value);
            notifyUpdated("max_retries", maxRetries);
        }
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("enabled", enabled);
        state.put("max_retries", maxRetries);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        if (state.containsKey("enabled")) {
            setParameter("enabled", state.get("enabled"));
        }
        if (state.containsKey("max_retries")) {
            setParameter("max_retries", state.get("max_retries"));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    private int clampRetries(Object value) {
        int retries = Integer.parseInt(String.valueOf(value));
        return Math.max(0, Math.min(5, retries));
    }

    private void notifyUpdated(String target, Object value) {
        if (onParameterUpdated != null) {
            onParameterUpdated.accept(target, value);
        }
    }
}
