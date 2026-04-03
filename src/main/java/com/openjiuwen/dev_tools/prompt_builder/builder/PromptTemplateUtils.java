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
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.utils}.
 */
public final class PromptTemplateUtils {

    private PromptTemplateUtils() {
    }

    private static final Map<String, Object> TEMPLATE_MAP = Map.of(
            "zh-CN", PromptTemplatesZh.class,
            "en-US", PromptTemplatesEn.class
    );

    public static Object selectTemplate(String language) {
        if (language == null) {
            return PromptTemplatesZh.class;
        }
        return TEMPLATE_MAP.getOrDefault(language, PromptTemplatesZh.class);
    }

    public static PromptTemplate getTemplate(Object templateHolder, String fieldName) {
        try {
            Class<?> templateClass = templateHolder instanceof Class<?>
                    ? (Class<?>) templateHolder
                    : templateHolder.getClass();
            return (PromptTemplate) templateClass.getField(fieldName).get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read template field: " + fieldName, exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static String getStringPrompt(Object prompt) {
        if (prompt instanceof String) {
            return (String) prompt;
        } else if (prompt instanceof PromptTemplate template) {
            Object content = template.getContent();
            if (content instanceof String s) {
                return s;
            } else if (content instanceof List<?> list) {
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
                "error_msg", "Prompt type " + toPythonTypeString(prompt) + " is not supported");
    }

    public static Map<String, Object> getTemplateMap() {
        return TEMPLATE_MAP;
    }

    private static String toPythonTypeString(Object value) {
        if (value == null) {
            return "<class 'NoneType'>";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return "<class 'int'>";
        }
        if (value instanceof Float || value instanceof Double) {
            return "<class 'float'>";
        }
        if (value instanceof Boolean) {
            return "<class 'bool'>";
        }
        if (value instanceof List<?>) {
            return "<class 'list'>";
        }
        if (value instanceof Map<?, ?>) {
            return "<class 'dict'>";
        }
        if (value instanceof String) {
            return "<class 'str'>";
        }
        return "<class '" + value.getClass().getSimpleName() + "'>";
    }
}