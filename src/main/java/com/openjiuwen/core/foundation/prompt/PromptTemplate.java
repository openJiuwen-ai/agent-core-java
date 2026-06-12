/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interpolatable text prompt template with configurable placeholders.
 *
 * <p>Mirrors Python's {@code PromptTemplate} in
 * {@code openjiuwen/core/foundation/prompt/template.py}.</p>
 */
@Data
@Builder
public class PromptTemplate {

    @Builder.Default
    private String name = "";

    @Builder.Default
    private Object content = "";

    @Builder.Default
    private String placeholderPrefix = "{{";

    @Builder.Default
    private String placeholderSuffix = "}}";

    public PromptTemplate(String name, Object content, String placeholderPrefix, String placeholderSuffix) {
        this.name = name == null ? "" : name;
        this.content = content == null ? "" : content;
        this.placeholderPrefix = placeholderPrefix == null ? "{{" : placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix == null ? "}}" : placeholderSuffix;
    }

    /**
     * Convert template content to message list using Python's wrapping rules.
     *
     * @return copied message list
     */
    public List<BaseMessage> toMessages() {
        if (content == null || (content instanceof String text && text.isEmpty())) {
            return List.of();
        }
        if (content instanceof String text) {
            return List.of(UserMessage.builder().content(text).build());
        }
        if (!(content instanceof List<?> items)) {
            throw invalidPromptTemplateType();
        }
        List<BaseMessage> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof BaseMessage baseMessage)) {
                throw invalidPromptTemplateType();
            }
            result.add(copyMessage(baseMessage));
        }
        return result;
    }

    /**
     * Format placeholders and return a new PromptTemplate instance.
     *
     * @param keywords substitutions
     * @return formatted prompt template copy
     */
    public PromptTemplate format(Map<String, Object> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return PromptTemplate.builder()
                    .name(name)
                    .content(copyContent(content))
                    .placeholderPrefix(placeholderPrefix)
                    .placeholderSuffix(placeholderSuffix)
                    .build();
        }

        Object contentCopy = copyContent(content);
        PromptAssembler assembler = new PromptAssembler(contentCopy, placeholderPrefix, placeholderSuffix);
        List<String> inputKeys = assembler.getInputKeys();
        Map<String, Object> validKeywords = new java.util.LinkedHashMap<>();
        for (String key : inputKeys) {
            if (keywords.containsKey(key)) {
                validKeywords.put(key, keywords.get(key));
            }
        }
        Object formattedContent = assembler.promptAssemble(validKeywords);
        return PromptTemplate.builder()
                .name(name)
                .content(formattedContent)
                .placeholderPrefix(placeholderPrefix)
                .placeholderSuffix(placeholderSuffix)
                .build();
    }

    private static Object copyContent(Object source) {
        if (source instanceof List<?> items) {
            List<Object> copy = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof BaseMessage baseMessage) {
                    copy.add(copyMessage(baseMessage));
                } else {
                    copy.add(item);
                }
            }
            return copy;
        }
        return source;
    }

    private static BaseMessage copyMessage(BaseMessage message) {
        if (message instanceof AssistantMessage assistant) {
            return AssistantMessage.builder()
                    .role(assistant.getRole())
                    .content(assistant.getContent())
                    .name(assistant.getName())
                    .toolCalls(assistant.getToolCalls())
                    .usageMetadata(assistant.getUsageMetadata())
                    .finishReason(assistant.getFinishReason())
                    .parserContent(assistant.getParserContent())
                    .reasoningContent(assistant.getReasoningContent())
                    .promptTokenIds(assistant.getPromptTokenIds())
                    .completionTokenIds(assistant.getCompletionTokenIds())
                    .logprobs(assistant.getLogprobs())
                    .metadata(assistant.getMetadata())
                    .build();
        }
        if (message instanceof UserMessage) {
            return UserMessage.builder()
                    .role(message.getRole())
                    .content(message.getContent())
                    .name(message.getName())
                    .metadata(message.getMetadata())
                    .build();
        }
        if (message instanceof SystemMessage) {
            return SystemMessage.builder()
                    .role(message.getRole())
                    .content(message.getContent())
                    .name(message.getName())
                    .metadata(message.getMetadata())
                    .build();
        }
        if (message instanceof ToolMessage toolMessage) {
            return ToolMessage.builder()
                    .role(message.getRole())
                    .content(message.getContent())
                    .name(message.getName())
                    .metadata(message.getMetadata())
                    .toolCallId(toolMessage.getToolCallId())
                    .build();
        }
        return BaseMessage.builder()
                .role(message.getRole())
                .content(message.getContent())
                .name(message.getName())
                .metadata(message.getMetadata())
                .build();
    }

    private static RuntimeException invalidPromptTemplateType() {
        return ErrorHelper.buildError(
                StatusCode.PROMPT_TEMPLATE_INVALID,
                "error_msg",
                "prompt template type must be in str or list[BaseMessage]"
        );
    }
}
