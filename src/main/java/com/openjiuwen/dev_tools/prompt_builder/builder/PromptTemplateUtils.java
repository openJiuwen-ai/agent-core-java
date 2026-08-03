/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Legacy prompt template utility facade.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/utils.py}.</p>
 */
public final class PromptTemplateUtils {
    private static final Map<String, Object> TEMPLATE_MAP = Map.of(
            "zh-CN", PromptTemplatesZh.class,
            "en-US", PromptTemplatesEn.class
    );

    private PromptTemplateUtils() {
    }

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

    public static String getStringPrompt(Object prompt) {
        if (prompt instanceof String text) {
            return text;
        }
        if (prompt instanceof PromptTemplate template) {
            Object content = template.getContent();
            if (content instanceof String text) {
                return text;
            }
            if (content instanceof List<?> list && list.stream().allMatch(BaseMessage.class::isInstance)) {
                return list.stream()
                        .map(BaseMessage.class::cast)
                        .map(BaseMessage::getContentAsString)
                        .collect(Collectors.joining("\n"));
            }
            if (content instanceof List<?> list) {
                return list.stream()
                        .map(PromptTemplateUtils::joinValues)
                        .collect(Collectors.joining("\n"));
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                "error_msg",
                "Prompt type " + toPythonTypeString(prompt) + " is not supported"
        );
    }

    public static Map<String, Object> getTemplateMap() {
        return TEMPLATE_MAP;
    }

    private static String joinValues(Object item) {
        if (item instanceof Map<?, ?> map) {
            return map.values().stream()
                    .map(value -> value == null ? "" : value.toString())
                    .collect(Collectors.joining("\n"));
        }
        return String.valueOf(item);
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
