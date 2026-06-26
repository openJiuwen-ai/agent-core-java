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
 * Prompt builder helper functions.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/utils.py}.</p>
 */
public final class PromptBuilderUtils {

    private static final Map<String, Map<String, PromptTemplate>> TEMPLATE_MAP = Map.of(
            "zh-CN", PromptZh.templates(),
            "en-US", PromptEn.templates()
    );

    private PromptBuilderUtils() {
    }

    public static Map<String, PromptTemplate> selectTemplate() {
        return selectTemplate("zh-CN");
    }

    public static Map<String, PromptTemplate> selectTemplate(String language) {
        return TEMPLATE_MAP.getOrDefault(language, PromptZh.templates());
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
            if (content instanceof List<?> contentList && contentList.stream().allMatch(BaseMessage.class::isInstance)) {
                return contentList.stream()
                        .map(BaseMessage.class::cast)
                        .map(BaseMessage::getContentAsString)
                        .collect(Collectors.joining("\n"));
            }
            if (content instanceof List<?> contentList) {
                return contentList.stream()
                        .map(PromptBuilderUtils::joinValues)
                        .collect(Collectors.joining("\n"));
            }
        }
        String typeName = prompt == null ? "null" : prompt.getClass().toString();
        throw ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                "error_msg",
                "Prompt type " + typeName + " is not supported"
        );
    }

    private static String joinValues(Object item) {
        if (item instanceof Map<?, ?> map) {
            return map.values().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n"));
        }
        return String.valueOf(item);
    }
}
