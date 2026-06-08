/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's message chunk merge helpers in
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 */
public final class MessageChunkMerge {

    private MessageChunkMerge() {
    }

    @SuppressWarnings("unchecked")
    public static Object mergeParserContent(Object left, Object right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }
        if (left instanceof String leftString && right instanceof String rightString) {
            return leftString + rightString;
        }
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            List<Object> merged = new ArrayList<>(leftList);
            merged.addAll(rightList);
            return merged;
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            return mergeDicts((Map<String, Object>) leftMap, (Map<String, Object>) rightMap);
        }
        if (left.getClass() == right.getClass() && !isBasicScalar(left)) {
            try {
                return mergePydanticModels(left, right);
            } catch (Exception ignored) {
                return right;
            }
        }
        return right;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeDicts(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> result = new LinkedHashMap<>(left);
        for (Map.Entry<String, Object> entry : right.entrySet()) {
            String key = entry.getKey();
            Object rightValue = entry.getValue();
            if (result.containsKey(key)) {
                Object leftValue = result.get(key);
                if (leftValue instanceof String leftString && rightValue instanceof String rightString) {
                    result.put(key, leftString + rightString);
                } else if (leftValue instanceof List<?> leftList && rightValue instanceof List<?> rightList) {
                    List<Object> merged = new ArrayList<>(leftList);
                    merged.addAll(rightList);
                    result.put(key, merged);
                } else if (leftValue instanceof Map<?, ?> leftMap && rightValue instanceof Map<?, ?> rightMap) {
                    result.put(key, mergeDicts((Map<String, Object>) leftMap, (Map<String, Object>) rightMap));
                } else {
                    result.put(key, rightValue);
                }
            } else {
                result.put(key, rightValue);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T mergePydanticModels(T left, Object right) {
        if (right == null || left == null || left.getClass() != right.getClass()) {
            return (T) right;
        }
        try {
            Class<?> type = left.getClass();
            T merged = (T) type.getDeclaredConstructor().newInstance();
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
                Method getter = descriptor.getReadMethod();
                Method setter = descriptor.getWriteMethod();
                if (getter == null || setter == null) {
                    continue;
                }
                Object leftValue = getter.invoke(left);
                Object rightValue = getter.invoke(right);
                setter.invoke(merged, mergeParserContent(leftValue, rightValue));
            }
            return merged;
        } catch (Exception ignored) {
            return (T) right;
        }
    }

    public static List<Integer> concatTokenIds(List<Integer> left, List<Integer> right) {
        if (left == null || left.isEmpty()) {
            return right;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        List<Integer> merged = new ArrayList<>(left);
        merged.addAll(right);
        return merged;
    }

    @SuppressWarnings("unchecked")
    public static Object mergeLogprobs(Object left, Object right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            Map<String, Object> merged = new LinkedHashMap<>();
            leftMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            for (Map.Entry<?, ?> entry : rightMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object leftValue = merged.get(key);
                Object rightValue = entry.getValue();
                if (leftValue instanceof List<?> leftList && rightValue instanceof List<?> rightList) {
                    List<Object> combined = new ArrayList<>(leftList);
                    combined.addAll(rightList);
                    merged.put(key, combined);
                } else if (leftValue == null) {
                    merged.put(key, rightValue);
                } else {
                    merged.put(key, rightValue);
                }
            }
            return merged;
        }
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            List<Object> merged = new ArrayList<>(leftList);
            merged.addAll(rightList);
            return merged;
        }
        return right;
    }

    private static boolean isBasicScalar(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof String;
    }
}
