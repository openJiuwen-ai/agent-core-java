/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop condition over array items, resolving arrays from session state via input schema.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.array.ArrayCondition}.
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
        Object currentIdxObj = session.state().get(Constant.INDEX);
        int currentIdx = (currentIdxObj instanceof Number) ? ((Number) currentIdxObj).intValue() : 0;

        int minLength = DEFAULT_MAX_LOOP_NUMBER;
        Map<String, Object> updates = new HashMap<>();

        for (Map.Entry<String, Object> entry : arrays.entrySet()) {
            String key = entry.getKey();
            Object arrObj = inputsMap.getOrDefault(key, List.of());
            if (!(arrObj instanceof List)) {
                return false;
            }
            List<?> arr = (List<?>) arrObj;
            minLength = Math.min(arr.size(), minLength);
            if (currentIdx >= minLength) {
                return false;
            }
            updates.put(key, arr.get(currentIdx));
        }
        session.state().update(updates);
        Map<String, Object> ioUpdates = new HashMap<>(updates);
        return new Object[]{true, ioUpdates};
    }
}
