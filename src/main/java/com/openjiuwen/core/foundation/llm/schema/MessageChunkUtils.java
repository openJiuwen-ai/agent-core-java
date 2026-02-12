// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息块合并工具类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message_chunk.py
 */
public final class MessageChunkUtils {

    private MessageChunkUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 智能合并parser_content字段。
     * 
     * 合并策略：
     * - 如果right为空，返回left
     * - 如果left为空，返回right
     * - 如果都是字符串，拼接
     * - 如果都是List，合并
     * - 如果都是Map，递归合并
     * - 否则返回right（保留最新值）
     */
    @SuppressWarnings("unchecked")
    public static <T> T mergeParserContent(T left, T right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }

        // 字符串拼接
        if (left instanceof String && right instanceof String) {
            return (T) (((String) left) + ((String) right));
        }

        // List合并
        if (left instanceof List && right instanceof List) {
            List<Object> result = new ArrayList<>((List<?>) left);
            result.addAll((List<?>) right);
            return (T) result;
        }

        // Map递归合并
        if (left instanceof Map && right instanceof Map) {
            return (T) mergeDicts((Map<String, Object>) left, (Map<String, Object>) right);
        }

        // 否则保留最新值
        return right;
    }

    /**
     * 递归合并两个Map。
     * 
     * 对于相同的key：
     * - 如果都是字符串，拼接
     * - 如果都是List，合并
     * - 如果都是Map，递归合并
     * - 否则使用right侧的值
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeDicts(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> result = new HashMap<>(left);

        for (Map.Entry<String, Object> entry : right.entrySet()) {
            String key = entry.getKey();
            Object rightValue = entry.getValue();

            if (result.containsKey(key)) {
                Object leftValue = result.get(key);

                // 递归处理相同类型
                if (leftValue instanceof String && rightValue instanceof String) {
                    result.put(key, ((String) leftValue) + ((String) rightValue));
                } else if (leftValue instanceof List && rightValue instanceof List) {
                    List<Object> merged = new ArrayList<>((List<?>) leftValue);
                    merged.addAll((List<?>) rightValue);
                    result.put(key, merged);
                } else if (leftValue instanceof Map && rightValue instanceof Map) {
                    result.put(key, mergeDicts(
                            (Map<String, Object>) leftValue,
                            (Map<String, Object>) rightValue
                    ));
                } else {
                    // 不同类型或其他类型，使用right侧的新值
                    result.put(key, rightValue);
                }
            } else {
                result.put(key, rightValue);
            }
        }

        return result;
    }
}

