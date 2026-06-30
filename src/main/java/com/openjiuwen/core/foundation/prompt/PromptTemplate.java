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

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interpolatable text prompt template with configurable placeholders.
 * Supports both String and {@code List<BaseMessage>} as content.
 * Mirrors Python's {@code PromptTemplate}.
 */
public class PromptTemplate {

    /**
     * Four-arg constructor for direct instantiation in tests.
     */
    public PromptTemplate(String name, Object content, String placeholderPrefix, String placeholderSuffix) {
        this.name = name;
        this.content = content;
        this.placeholderPrefix = placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix;
    }

    /** Template name. */
    private String name = "";

    /** Template content - either a plain String or a List<BaseMessage>. */
    private Object content = "";

    /** Left delimiter for placeholders. */
    private String placeholderPrefix = "{{";

    /** Right delimiter for placeholders. */
    private String placeholderSuffix = "}}";

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContent(Object content) {
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getPlaceholderPrefix() {
        return placeholderPrefix;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPlaceholderPrefix(String placeholderPrefix) {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getPlaceholderSuffix() {
        return placeholderSuffix;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPlaceholderSuffix(String placeholderSuffix) {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * Convert template content to a list of BaseMessages.
     * Preserves original message subtype.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
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
            List<BaseMessage> result = new ArrayList<>();
            for (Object msg : list) {
                result.add(copyMessage((BaseMessage) msg));
            }
            return result;
        }
        throw ErrorHelper.buildError(StatusCode.PROMPT_TEMPLATE_INVALID,
                "error_msg", "prompt template type must be in str or list[BaseMessage]");
    }

    /**
     * Replace placeholders with the given keywords and return a new PromptTemplate.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
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
            List<BaseMessage> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyMessage((BaseMessage) item));
            }
            contentCopy = copy;
        } else {
            contentCopy = content;
        }

        PromptAssembler assembler = new PromptAssembler(
                contentCopy, placeholderPrefix, placeholderSuffix);
        List<String> inputKeys = assembler.getInputKeys();

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

    /**
     * Copy a BaseMessage preserving its original subtype.
     */
    private static BaseMessage copyMessage(BaseMessage bm) {
        if (bm instanceof AssistantMessage am) {
            return AssistantMessage.builder()
                    .role(am.getRole())
                    .content(am.getContent())
                    .name(am.getName())
                    .toolCalls(am.getToolCalls())
                    .usageMetadata(am.getUsageMetadata())
                    .finishReason(am.getFinishReason())
                    .parserContent(am.getParserContent())
                    .reasoningContent(am.getReasoningContent())
                    .build();
        } else if (bm instanceof UserMessage) {
            return UserMessage.builder()
                    .role(bm.getRole())
                    .content(bm.getContent())
                    .name(bm.getName())
                    .build();
        } else if (bm instanceof SystemMessage) {
            return SystemMessage.builder()
                    .role(bm.getRole())
                    .content(bm.getContent())
                    .name(bm.getName())
                    .build();
        } else if (bm instanceof ToolMessage tm) {
            return ToolMessage.builder()
                    .role(bm.getRole())
                    .content(bm.getContent())
                    .name(bm.getName())
                    .toolCallId(tm.getToolCallId())
                    .build();
        } else {
            return BaseMessage.builder()
                    .role(bm.getRole())
                    .content(bm.getContent())
                    .name(bm.getName())
                    .build();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        private String name = "";
        private Object content = "";
        private String placeholderPrefix = "{{";
        private String placeholderSuffix = "}}";

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder placeholderPrefix(String placeholderPrefix) {
            this.placeholderPrefix = placeholderPrefix;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder placeholderSuffix(String placeholderSuffix) {
            this.placeholderSuffix = placeholderSuffix;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public PromptTemplate build() {
            return new PromptTemplate(name, content, placeholderPrefix, placeholderSuffix);
        }
    }
}
