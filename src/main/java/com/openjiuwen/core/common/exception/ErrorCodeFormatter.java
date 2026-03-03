// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误码格式化工具
 *
 * <p>用于安全地格式化错误消息模板中的占位符。</p>
 */
final class ErrorCodeFormatter {

    private ErrorCodeFormatter() {
        // 防止实例化
    }

    /**
     * 使用提供的参数安全格式化模板
     *
     * <p>缺失的键将显示为 '<missing:KEY>'。
     * 如果模板为 null 或为空，返回空字符串。</p>
     *
     * @param template 模板字符串
     * @param params 参数映射
     * @return 格式化后的字符串
     */
    static String formatTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        Map<String, String> safe = new HashMap<>();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "null";
                safe.put(entry.getKey(), value);
            }
        }

        try {
            return replacePlaceholders(template, safe);
        } catch (Exception e) {
            // 作为最后手段，返回原始模板加参数摘要
            return template + " (format error, params=" + (params != null ? params : "{}") + ")";
        }
    }

    /**
     * 替换模板中的占位符
     *
     * @param template 模板字符串
     * @param params 参数映射
     * @return 替换后的字符串
     */
    private static String replacePlaceholders(String template, Map<String, String> params) {
        StringBuilder result = new StringBuilder();
        int start = 0;
        int placeholderStart = template.indexOf('{');

        while (placeholderStart != -1) {
            result.append(template, start, placeholderStart);

            int placeholderEnd = template.indexOf('}', placeholderStart);
            if (placeholderEnd == -1) {
                // 没有闭合大括号，直接追加剩余内容
                result.append(template.substring(placeholderStart));
                break;
            }

            String key = template.substring(placeholderStart + 1, placeholderEnd);
            String value = params.getOrDefault(key, "<missing:" + key + ">");
            result.append(value);

            start = placeholderEnd + 1;
            placeholderStart = template.indexOf('{', start);
        }

        if (start < template.length()) {
            result.append(template.substring(start));
        }

        return result.toString();
    }
}