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
 * Loop condition over array items, resolving arrays from session state via input schema.
 * <p>
 * Mirrors Python's {@code ArrayCondition} in
 * {@code openjiuwen/core/workflow/components/condition/array.py}.
 */
public class ArrayCondition extends Condition {

    private static final int DEFAULT_MAX_LOOP_NUMBER = 1000;
    private final Map<String, Object> arrays;

    public ArrayCondition(Map<String, Object> arrays) {
        super(arrays);
        this.arrays = arrays;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object doInvoke(Object inputs, BaseSession session) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
        Object currentIdxObj = stateValue(session, Constant.INDEX);
        int currentIdx = (currentIdxObj instanceof Number) ? ((Number) currentIdxObj).intValue() : 0;

        int minLength = DEFAULT_MAX_LOOP_NUMBER;
        Map<String, Object> updates = new HashMap<>();

        for (Map.Entry<String, Object> entry : arrays.entrySet()) {
            String key = entry.getKey();
            Object arrObj = inputsMap.getOrDefault(key, List.of());
            int arrLength = sequenceLength(arrObj);
            minLength = Math.min(arrLength, minLength);
            if (currentIdx >= minLength) {
                return false;
            }
            updates.put(key, sequenceValue(arrObj, currentIdx));
        }
        updateState(session, updates);
        Map<String, Object> ioUpdates = new HashMap<>(updates);
        return new Object[]{true, ioUpdates};
    }

    private static int sequenceLength(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof CharSequence sequence) {
            return sequence.length();
        }
        if (value != null && value.getClass().isArray()) {
            return Array.getLength(value);
        }
        throw new IllegalArgumentException("object has no len(): " + typeName(value));
    }

    private static Object sequenceValue(Object value, int index) {
        if (value instanceof List<?> list) {
            return list.get(index);
        }
        if (value instanceof CharSequence sequence) {
            return String.valueOf(sequence.charAt(index));
        }
        if (value != null && value.getClass().isArray()) {
            return Array.get(value, index);
        }
        throw new IllegalArgumentException("object is not subscriptable: " + typeName(value));
    }

    private static String typeName(Object value) {
        return value == null ? "NoneType" : value.getClass().getSimpleName();
    }
}
