/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import java.util.List;
import java.util.Map;

/**
 * Utility class for merging streaming message chunks and parser content.
 * <p>
 * Mirrors Python's merge helper functions in {@code message_chunk.py}.
 */
public final class MergeUtils {

    private MergeUtils() {
        // Utility class
    }

    /**
     * Intelligently merge parser_content fields.
     * <p>
     * Merge strategy:
     * <ul>
     *   <li>If right is null, return left</li>
     *   <li>If left is null, return right</li>
     *   <li>If both are strings, concatenate</li>
     *   <li>If both are lists, concatenate</li>
     *   <li>If both are maps, recursively merge</li>
     *   <li>Otherwise, return right (keep latest)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static Object mergeParserContent(Object left, Object right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }

        // String concatenation
        if (left instanceof String ls && right instanceof String rs) {
            return ls + rs;
        }

        // List concatenation
        if (left instanceof List<?> ll && right instanceof List<?> rl) {
            var merged = new java.util.ArrayList<Object>(ll);
            merged.addAll(rl);
            return merged;
        }

        // Map recursive merge
        if (left instanceof Map<?, ?> lm && right instanceof Map<?, ?> rm) {
            return mergeMaps((Map<String, Object>) lm, (Map<String, Object>) rm);
        }

        // Otherwise keep the latest value
        return right;
    }

    /**
     * Recursively merge two maps.
     * <p>
     * For the same key: strings are concatenated, lists are concatenated,
     * maps are recursively merged, otherwise the right value wins.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeMaps(Map<String, Object> left, Map<String, Object> right) {
        var result = new java.util.LinkedHashMap<>(left);
        for (var entry : right.entrySet()) {
            String key = entry.getKey();
            Object rightValue = entry.getValue();

            if (result.containsKey(key)) {
                Object leftValue = result.get(key);

                if (leftValue instanceof String ls && rightValue instanceof String rs) {
                    result.put(key, ls + rs);
                } else if (leftValue instanceof List<?> ll && rightValue instanceof List<?> rl) {
                    var merged = new java.util.ArrayList<Object>(ll);
                    merged.addAll(rl);
                    result.put(key, merged);
                } else if (leftValue instanceof Map<?, ?> lm && rightValue instanceof Map<?, ?> rm) {
                    result.put(key, mergeMaps((Map<String, Object>) lm, (Map<String, Object>) rm));
                } else {
                    result.put(key, rightValue);
                }
            } else {
                result.put(key, rightValue);
            }
        }
        return result;
    }
}
