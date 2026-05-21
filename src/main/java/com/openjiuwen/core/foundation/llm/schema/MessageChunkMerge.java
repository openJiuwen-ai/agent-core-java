/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for merging parser content fields.
 * <p>
 * Mirrors Python's {@code message_chunk} module from
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 */
public class MessageChunkMerge {

    /**
     * Intelligently merge parser_content fields.
     *
     * <p>Merge strategy:
     * <ul>
     *   <li>If right is empty, return left</li>
     *   <li>If left is empty, return right</li>
     *   <li>If both are strings, concatenate them</li>
     *   <li>If both are lists, merge (concatenate) them</li>
     *   <li>If both are dicts, recursively merge the dicts</li>
     *   <li>Otherwise, return right (keep the latest value)</li>
     * </ul>
     *
     * @param left  the left value
     * @param right the right value
     * @return merged value
     */
    public static Object mergeParserContent(Object left, Object right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }

        // String concatenation
        if (left instanceof String && right instanceof String) {
            return (String) left + (String) right;
        }

        // List concatenation
        if (left instanceof List && right instanceof List) {
            List<Object> result = new ArrayList<>((List<?>) left);
            result.addAll((List<?>) right);
            return result;
        }

        // Dictionary recursive merge
        if (left instanceof Map && right instanceof Map) {
            return mergeDicts((Map<String, Object>) left, (Map<String, Object>) right);
        }

        // Otherwise, keep the latest value
        return right;
    }

    /**
     * Recursively merge two dictionaries.
     *
     * <p>For the same key:
     * <ul>
     *   <li>If both values are strings, concatenate them</li>
     *   <li>If both values are lists, concatenate them</li>
     *   <li>If both values are dicts, recursively merge them</li>
     *   <li>Otherwise, use the value from the right side</li>
     * </ul>
     *
     * @param left  the left dictionary
     * @param right the right dictionary
     * @return merged dictionary
     */
    public static Map<String, Object> mergeDicts(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> result = new HashMap<>(left);

        for (Map.Entry<String, Object> entry : right.entrySet()) {
            String key = entry.getKey();
            Object rightValue = entry.getValue();

            if (result.containsKey(key)) {
                Object leftValue = result.get(key);

                // Recursively handle same types
                if (leftValue instanceof String && rightValue instanceof String) {
                    result.put(key, (String) leftValue + (String) rightValue);
                } else if (leftValue instanceof List && rightValue instanceof List) {
                    List<Object> merged = new ArrayList<>((List<?>) leftValue);
                    merged.addAll((List<?>) rightValue);
                    result.put(key, merged);
                } else if (leftValue instanceof Map && rightValue instanceof Map) {
                    result.put(key, mergeDicts((Map<String, Object>) leftValue, (Map<String, Object>) rightValue));
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