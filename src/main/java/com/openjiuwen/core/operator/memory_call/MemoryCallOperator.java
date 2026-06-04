/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.memory_call;

import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Memory invocation operator with enabled and retry tunables.
 *
 * <p>Mirrors Python's {@code MemoryCallOperator} in
 * {@code openjiuwen.core.operator.memory_call.base}.</p>
 */
public class MemoryCallOperator extends Operator {

    private final MemoryOperation memory;
    private final String memoryCallId;
    private final MemoryInvoker memoryInvoker;
    private final BiConsumer<String, Object> onParameterUpdated;
    private boolean enabled = true;
    private int maxRetries;

    public MemoryCallOperator(MemoryOperation memory, String memoryCallId, MemoryInvoker memoryInvoker) {
        this(memory, memoryCallId, memoryInvoker, null);
    }

    public MemoryCallOperator(MemoryOperation memory, String memoryCallId, MemoryInvoker memoryInvoker,
                              BiConsumer<String, Object> onParameterUpdated) {
        this.memory = memory;
        this.memoryCallId = memoryCallId != null ? memoryCallId : "memory_call";
        this.memoryInvoker = memoryInvoker;
        this.onParameterUpdated = onParameterUpdated;
    }

    public MemoryCallOperator(MemoryOperation memory) {
        this(memory, "memory_call", null);
    }

    public MemoryCallOperator(MemoryInvoker memoryInvoker) {
        this(null, "memory_call", memoryInvoker);
    }

    public MemoryCallOperator() {
        this(null, "memory_call", null);
    }

    @Override
    public String getOperatorId() {
        return memoryCallId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        Map<String, TunableSpec> tunables = new LinkedHashMap<>();
        tunables.put("enabled", new TunableSpec(
                "enabled", "discrete", "enabled", Map.of("type", "bool")));
        tunables.put("max_retries", new TunableSpec(
                "max_retries", "discrete", "max_retries", Map.of("type", "int", "min", 0, "max", 5)));
        return tunables;
    }

    @Override
    public void setParameter(String target, Object value) {
        if ("enabled".equals(target)) {
            enabled = value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
            notifyParameterUpdated("enabled", enabled);
        }
        if ("max_retries".equals(target)) {
            maxRetries = clampRetries(value);
            notifyParameterUpdated("max_retries", maxRetries);
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

    public Object invoke(Map<String, Object> inputs,
                         Session session,
                         Map<String, Object> kwargs) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("MemoryCallOperator disabled: " + memoryCallId);
        }
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        setOperatorContext(session, memoryCallId);
        try {
            Exception lastError = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (memoryInvoker != null) {
                        return memoryInvoker.invoke(inputs);
                    }
                    if (memory == null) {
                        throw new IllegalStateException("MemoryCallOperator has no memory configured");
                    }
                    return memory.invoke(inputs, safeKwargs);
                } catch (Exception ex) {
                    lastError = ex;
                    if (attempt >= maxRetries) {
                        throw ex;
                    }
                }
            }
            throw lastError != null ? lastError : new IllegalStateException("memory invoke failed without exception");
        } finally {
            setOperatorContext(session, null);
        }
    }

    public Object invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, Collections.emptyMap());
    }

    public OperatorStream<Object> stream(Map<String, Object> inputs,
                                         Session session,
                                         Map<String, Object> kwargs) throws Exception {
        if (memory == null) {
            throw new UnsupportedOperationException("memory stream not implemented");
        }
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        setOperatorContext(session, memoryCallId);
        try {
            return OperatorStream.wrap(memory.stream(inputs, safeKwargs), () -> setOperatorContext(session, null));
        } catch (Exception ex) {
            setOperatorContext(session, null);
            throw ex;
        }
    }

    public OperatorStream<Object> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, Collections.emptyMap());
    }

    private static int clampRetries(Object value) {
        int retries = Integer.parseInt(String.valueOf(value));
        return Math.max(0, Math.min(5, retries));
    }

    private void notifyParameterUpdated(String target, Object value) {
        if (onParameterUpdated != null) {
            onParameterUpdated.accept(target, value);
        }
    }

}
