/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop condition over array items already stored in session (not from schema).
 * <p>
 * Mirrors Python's {@code ArrayConditionInSession} in
 * {@code openjiuwen/core/workflow/components/condition/array.py}.
 */
public class ArrayConditionInSession extends Condition {

    private static final int DEFAULT_MAX_LOOP_NUMBER = 1000;
    private final Map<String, Object> arrays;
    private final int minLength;

    @SuppressWarnings("unchecked")
    public ArrayConditionInSession(Map<String, Object> arrays) {
        super();
        this.arrays = arrays;
        this.minLength = checkArrays(arrays);
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        Object currentIdxObj = stateValue(session, Constant.INDEX);
        int currentIdx = (currentIdxObj instanceof Number) ? ((Number) currentIdxObj).intValue() : 0;

        if (currentIdx >= minLength) {
            return false;
        }

        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Object> entry : arrays.entrySet()) {
            String key = entry.getKey();
            Object arrayInfo = entry.getValue();
            if (!isListOrArray(arrayInfo)) {
                throw new IllegalArgumentException(
                        "Expected list/tuple for '" + key + "' in loop_array, got " + typeName(arrayInfo));
            }
            updates.put(key, sequenceValue(arrayInfo, currentIdx));
        }
        updateState(session, updates);
        Map<String, Object> ioUpdates = new HashMap<>(updates);
        return new Object[]{true, ioUpdates};
    }

    private static int checkArrays(Map<String, Object> arrays) {
        if (arrays == null || arrays.isEmpty()) {
            return 0;
        }
        int min = DEFAULT_MAX_LOOP_NUMBER;
        for (Map.Entry<String, Object> entry : arrays.entrySet()) {
            Object arrayInfo = entry.getValue();
            if (arrayInfo == null) {
                throw new IllegalArgumentException("Value for key '" + entry.getKey() + "' in loop_array cannot be None");
            }
            if (!isListOrArray(arrayInfo)) {
                throw new IllegalArgumentException(
                        "Expected list/tuple for '" + entry.getKey() + "' in loop_array, got " + typeName(arrayInfo));
            }
            min = Math.min(sequenceLength(arrayInfo), min);
        }
        return min;
    }

    private static boolean isListOrArray(Object value) {
        return value instanceof List<?> || value != null && value.getClass().isArray();
    }

    private static int sequenceLength(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return Array.getLength(value);
    }

    private static Object sequenceValue(Object value, int index) {
        if (value instanceof List<?> list) {
            return list.get(index);
        }
        return Array.get(value, index);
    }

    private static String typeName(Object value) {
        return value == null ? "NoneType" : value.getClass().getSimpleName();
    }
}
