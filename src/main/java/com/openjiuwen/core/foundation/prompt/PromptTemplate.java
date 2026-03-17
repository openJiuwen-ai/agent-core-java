/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.prompt;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interpolatable text prompt template with configurable placeholders.
 * <p>
 * Supports both String and {@code List<BaseMessage>} as content,
 * and provides {@link #toMessages()} and {@link #format(Map)} methods.
 * <p>
 * Mirrors Python's {@code PromptTemplate}.
 */
@Data
@Builder
public class PromptTemplate {

    /** Template name. */
    @Builder.Default
    private String name = "";

    /**
     * Template content — either a plain {@code String} or a {@code List<BaseMessage>}.
     */
    @Builder.Default
    private Object content = "";

    /** Left delimiter for placeholders. */
    @Builder.Default
    private String placeholderPrefix = "{{";

    /** Right delimiter for placeholders. */
    @Builder.Default
    private String placeholderSuffix = "}}";

    /**
     * Convert template content to a list of {@link BaseMessage}s.
     * <p>
     * If content is a String, wraps it as a single {@link UserMessage}.
     * If it is already a {@code List<BaseMessage>}, returns a deep copy.
     */
    @SuppressWarnings("unchecked")
    public List<BaseMessage> toMessages() {
        if (content == null || (content instanceof String s && s.isEmpty())) {
            return List.of();
        }
        if (content instanceof String s) {
            return List.of(UserMessage.builder().content(s).build());
        }
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof BaseMessage)) {
                    throw ErrorHelper.buildError(StatusCode.PROMPT_TEMPLATE_INVALID,
                            "error_msg", "prompt template type must be in str or list[BaseMessage]");
                }
            }
            // Deep copy
            List<BaseMessage> result = new ArrayList<>();
            for (Object msg : list) {
                BaseMessage bm = (BaseMessage) msg;
                result.add(BaseMessage.builder()
                        .role(bm.getRole())
                        .content(bm.getContent())
                        .name(bm.getName())
                        .build());
            }
            return result;
        }
        throw ErrorHelper.buildError(StatusCode.PROMPT_TEMPLATE_INVALID,
                "error_msg", "prompt template type must be in str or list[BaseMessage]");
    }

    /**
     * Replace placeholders with the given keywords and return a new {@link PromptTemplate}.
     *
     * @param keywords key-value pairs for substitution; {@code null} or empty returns a copy
     * @return new PromptTemplate with substituted content
     */
    @SuppressWarnings("unchecked")
    public PromptTemplate format(Map<String, Object> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return PromptTemplate.builder()
                    .name(name)
                    .content(content)
                    .placeholderPrefix(placeholderPrefix)
                    .placeholderSuffix(placeholderSuffix)
                    .build();
        }

        Object contentCopy;
        if (content instanceof String) {
            contentCopy = content;
        } else if (content instanceof List<?> list) {
            // Shallow copy of the list with new message instances
            List<BaseMessage> copy = new ArrayList<>();
            for (Object item : list) {
                BaseMessage bm = (BaseMessage) item;
                copy.add(BaseMessage.builder()
                        .role(bm.getRole())
                        .content(bm.getContent())
                        .name(bm.getName())
                        .build());
            }
            contentCopy = copy;
        } else {
            contentCopy = content;
        }

        PromptAssembler assembler = new PromptAssembler(
                contentCopy, placeholderPrefix, placeholderSuffix);
        List<String> inputKeys = assembler.getInputKeys();

        // Only pass keys that the template actually needs
        Map<String, Object> validKeywords = new java.util.LinkedHashMap<>();
        for (String key : inputKeys) {
            if (keywords.containsKey(key)) {
                validKeywords.put(key, keywords.get(key));
            }
        }

        Object assembled = assembler.promptAssemble(validKeywords);

        return PromptTemplate.builder()
                .name(name)
                .content(assembled)
                .placeholderPrefix(placeholderPrefix)
                .placeholderSuffix(placeholderSuffix)
                .build();
    }
}
