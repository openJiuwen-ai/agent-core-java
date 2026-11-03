/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ToolCall 参数的 JSON object 处理工具。
 *
 * <p>本类提供进入历史前的有限修复和模型请求发出前的安全兜底。
 * 修复只补齐末尾缺失的容器闭合符；请求兜底只返回合法原文或 "{}"，绝不执行修复。</p>
 */
public final class ToolCallArgumentUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ToolCallArgumentUtils() {
    }

    /**
     * 尝试把参数字符串修复为完整 JSON object。
     *
     * <p>仅补齐末尾缺失的容器闭合符（"}" / "]"）；补齐后仍不是完整 JSON object 时返回原文。
     * null 或空白字符串按空对象处理，返回 "{}"。</p>
     *
     * @param arguments 原始工具参数字符串
     * @return 修复后的 JSON object 字符串，或无法修复时的原文
     */
    public static String repairJsonObject(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "{}";
        }
        if (isJsonObject(arguments)) {
            return arguments;
        }

        String repaired = appendMissingClosures(arguments);
        if (!repaired.equals(arguments) && isJsonObject(repaired)) {
            return repaired;
        }
        return arguments;
    }

    /**
     * 判断参数是否为一个完整 JSON object 字符串。
     *
     * <p>数组、字符串、数字、布尔值、null、非法 JSON、带 trailing token 的 JSON 都返回 false。</p>
     *
     * @param arguments 待判断的参数值
     * @return 参数是完整 JSON object 字符串时返回 true
     */
    public static boolean isJsonObject(Object arguments) {
        if (!(arguments instanceof String text)) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(text);
            return root != null && root.isObject();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 将参数转换为可安全回放给模型请求的 JSON object 字符串。
     *
     * <p>完整 JSON object 原样返回；其他任何值都兜底为 "{}"。该方法不做修复。</p>
     *
     * @param arguments 原始工具参数
     * @return 原始 JSON object 字符串或 "{}"
     */
    public static String fallbackJsonObject(Object arguments) {
        if (isJsonObject(arguments)) {
            return (String) arguments;
        }
        return "{}";
    }

    /**
     * 补齐末尾缺失的 JSON 容器闭合符。
     *
     * <p>该方法只做括号/方括号闭合补齐；遇到字符串未闭合、闭合符不匹配等情况会返回原文。</p>
     */
    private static String appendMissingClosures(String arguments) {
        StringBuilder expectedClosures = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < arguments.length(); index++) {
            char current = arguments.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                expectedClosures.append('}');
            } else if (current == '[') {
                expectedClosures.append(']');
            } else if (current == '}' || current == ']') {
                if (expectedClosures.isEmpty()
                        || expectedClosures.charAt(expectedClosures.length() - 1) != current) {
                    return arguments;
                }
                expectedClosures.deleteCharAt(expectedClosures.length() - 1);
            }
        }

        if (inString || expectedClosures.isEmpty()) {
            return arguments;
        }

        StringBuilder repaired = new StringBuilder(arguments);
        for (int index = expectedClosures.length() - 1; index >= 0; index--) {
            repaired.append(expectedClosures.charAt(index));
        }
        return repaired.toString();
    }
}
