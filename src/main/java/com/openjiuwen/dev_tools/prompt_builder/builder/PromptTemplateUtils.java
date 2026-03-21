// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板工具类
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.utils}
 */
public final class PromptTemplateUtils {

    private PromptTemplateUtils() {
        // Utility class
    }

    /**
     * 模板映射
     */
    private static final Map<String, Object> TEMPLATE_MAP = Map.of(
            "zh-CN", PromptTemplatesZh.class,
            "en-US", PromptTemplatesEn.class
    );

    /**
     * 选择模板
     *
     * @param language 语言代码
     * @return 模板类
     */
    public static Object selectTemplate(String language) {
        return TEMPLATE_MAP.getOrDefault(language, PromptTemplatesZh.class);
    }

    /**
     * 获取字符串形式的提示词
     *
     * @param prompt 提示词对象（String 或 PromptTemplate）
     * @return 字符串形式的提示词
     */
    @SuppressWarnings("unchecked")
    public static String getStringPrompt(Object prompt) {
        if (prompt instanceof String) {
            return (String) prompt;
        } else if (prompt instanceof PromptTemplate template) {
            Object content = template.getContent();
            if (content instanceof String s) {
                return s;
            } else if (content instanceof List<?> list) {
                // Check if all items are BaseMessage
                if (!list.isEmpty() && list.get(0) instanceof BaseMessage) {
                    StringBuilder sb = new StringBuilder();
                    for (BaseMessage msg : (List<BaseMessage>) list) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(msg.getContentAsString());
                    }
                    return sb.toString();
                } else {
                    // Handle list of maps
                    StringBuilder sb = new StringBuilder();
                    for (Object item : list) {
                        if (item instanceof Map) {
                            Map<String, Object> mapItem = (Map<String, Object>) item;
                            for (Object value : mapItem.values()) {
                                if (sb.length() > 0) {
                                    sb.append("\n");
                                }
                                sb.append(value != null ? value.toString() : "");
                            }
                        }
                    }
                    return sb.toString();
                }
            }
        }
        throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                "error_msg", "Prompt type " + (prompt != null ? prompt.getClass().getName() : "null") + " is not supported");
    }

    /**
     * 获取模板映射
     *
     * @return 模板映射
     */
    public static Map<String, Object> getTemplateMap() {
        return TEMPLATE_MAP;
    }
}