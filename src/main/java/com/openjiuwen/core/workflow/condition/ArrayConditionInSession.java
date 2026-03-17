/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop condition over array items already stored in session (not from schema).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.array.ArrayConditionInSession}.
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
        Object currentIdxObj = session.state().get(Constant.INDEX);
        int currentIdx = (currentIdxObj instanceof Number) ? ((Number) currentIdxObj).intValue() : 0;

        if (currentIdx >= minLength) {
            return false;
        }

        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Object> entry : arrays.entrySet()) {
            String key = entry.getKey();
            Object arrayInfo = entry.getValue();
            if (!(arrayInfo instanceof List)) {
                throw new IllegalArgumentException(
                        "Expected list for '" + key + "' in loop_array, got " + arrayInfo.getClass().getSimpleName());
            }
            List<?> list = (List<?>) arrayInfo;
            updates.put(key, list.get(currentIdx));
        }
        session.state().update(updates);
        Map<String, Object> ioUpdates = new HashMap<>(updates);
        return new Object[]{true, ioUpdates};
    }

    private static int checkArrays(Map<String, Object> arrays) {
        int min = DEFAULT_MAX_LOOP_NUMBER;
        for (Map.Entry<String, Object> entry : arrays.entrySet()) {
            Object arrayInfo = entry.getValue();
            if (arrayInfo == null) {
                throw new IllegalArgumentException("Value for key '" + entry.getKey() + "' in loop_array cannot be None");
            }
            if (!(arrayInfo instanceof List)) {
                throw new IllegalArgumentException(
                        "Expected list for '" + entry.getKey() + "' in loop_array, got " + arrayInfo.getClass().getSimpleName());
            }
            min = Math.min(((List<?>) arrayInfo).size(), min);
        }
        return min;
    }
}
