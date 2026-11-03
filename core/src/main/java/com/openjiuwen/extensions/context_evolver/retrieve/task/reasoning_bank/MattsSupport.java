/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-local helpers for MaTTS dynamic context and trajectory structures.
 *
 * <p>Mirrors Python's dynamic dict/context usage in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
final class MattsSupport {

    private MattsSupport() {
    }

    static Object requireContext(RuntimeContext context, String key) {
        Map<String, Object> data = context.toDict();
        if (!data.containsKey(key)) {
            throw new IllegalStateException("Context has no attribute '" + key + "'");
        }
        return data.get(key);
    }

    static MattsAsyncLlm requireLlm(Object llmObject) {
        if (llmObject instanceof MattsAsyncLlm llm) {
            return llm;
        }
        throw new IllegalStateException("LLM not configured in ServiceContext");
    }

    static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static int sizeOfSteps(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0D;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    static List<Map<String, Object>> trajectoryMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(mapValue(item));
        }
        return result;
    }

    static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected map value: " + value);
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    static String left(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
