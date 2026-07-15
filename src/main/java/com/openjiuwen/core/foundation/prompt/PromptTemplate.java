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
 * 
 * @since 0.1.7
 */
public class PromptTemplate {
    /**
     * PromptTemplate.
     * 
     * @param name name
     * @param content content
     * @param placeholderPrefix placeholderPrefix
     * @param placeholderSuffix placeholderSuffix
     * @since 0.1.7
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
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getContent() {
        return content;
    }

    /**
     * setContent.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void setContent(Object content) {
        this.content = content;
    }

    /**
     * getPlaceholderPrefix.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getPlaceholderPrefix() {
        return placeholderPrefix;
    }

    /**
     * setPlaceholderPrefix.
     * 
     * @param placeholderPrefix placeholderPrefix
     * @since 0.1.7
     */
    public void setPlaceholderPrefix(String placeholderPrefix) {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * getPlaceholderSuffix.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getPlaceholderSuffix() {
        return placeholderSuffix;
    }

    /**
     * setPlaceholderSuffix.
     * 
     * @param placeholderSuffix placeholderSuffix
     * @since 0.1.7
     */
    public void setPlaceholderSuffix(String placeholderSuffix) {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * toMessages.
     * 
     * @return the result
     * @since 0.1.7
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
                    throw ErrorHelper.buildError(StatusCode.PROMPT_TEMPLATE_INVALID, "error_msg",
                            "prompt template type must be in str or list[BaseMessage]");
                }
            }
            List<BaseMessage> result = new ArrayList<>();
            for (Object msg : list) {
                result.add(copyMessage((BaseMessage) msg));
            }
            return result;
        }
        throw ErrorHelper.buildError(StatusCode.PROMPT_TEMPLATE_INVALID, "error_msg",
                "prompt template type must be in str or list[BaseMessage]");
    }

    /**
     * format.
     * 
     * @param keywords keywords
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public PromptTemplate format(Map<String, Object> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return PromptTemplate.builder().name(name).content(content).placeholderPrefix(placeholderPrefix)
                    .placeholderSuffix(placeholderSuffix).build();
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

        PromptAssembler assembler = new PromptAssembler(contentCopy, placeholderPrefix, placeholderSuffix);
        List<String> inputKeys = assembler.getInputKeys();

        Map<String, Object> validKeywords = new java.util.LinkedHashMap<>();
        for (String key : inputKeys) {
            if (keywords.containsKey(key)) {
                validKeywords.put(key, keywords.get(key));
            }
        }

        Object assembled = assembler.promptAssemble(validKeywords);

        return PromptTemplate.builder().name(name).content(assembled).placeholderPrefix(placeholderPrefix)
                .placeholderSuffix(placeholderSuffix).build();
    }

    /**
     * Copy a BaseMessage preserving its original subtype.
     * 
     * @param bm bm
     * @return the result
     * @since 0.1.7
     */
    private static BaseMessage copyMessage(BaseMessage bm) {
        if (bm instanceof AssistantMessage am) {
            return AssistantMessage.builder().role(am.getRole()).content(am.getContent()).name(am.getName())
                    .toolCalls(am.getToolCalls()).usageMetadata(am.getUsageMetadata())
                    .finishReason(am.getFinishReason()).parserContent(am.getParserContent())
                    .reasoningContent(am.getReasoningContent()).build();
        } else if (bm instanceof UserMessage) {
            return UserMessage.builder().role(bm.getRole()).content(bm.getContent()).name(bm.getName()).build();
        } else if (bm instanceof SystemMessage) {
            return SystemMessage.builder().role(bm.getRole()).content(bm.getContent()).name(bm.getName()).build();
        } else if (bm instanceof ToolMessage tm) {
            return ToolMessage.builder().role(bm.getRole()).content(bm.getContent()).name(bm.getName())
                    .toolCallId(tm.getToolCallId()).build();
        } else {
            return BaseMessage.builder().role(bm.getRole()).content(bm.getContent()).name(bm.getName()).build();
        }
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder {
        private String name = "";
        private Object content = "";
        private String placeholderPrefix = "{{";
        private String placeholderSuffix = "}}";

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        /**
         * placeholderPrefix.
         * 
         * @param placeholderPrefix placeholderPrefix
         * @return the result
         * @since 0.1.7
         */
        public Builder placeholderPrefix(String placeholderPrefix) {
            this.placeholderPrefix = placeholderPrefix;
            return this;
        }

        /**
         * placeholderSuffix.
         * 
         * @param placeholderSuffix placeholderSuffix
         * @return the result
         * @since 0.1.7
         */
        public Builder placeholderSuffix(String placeholderSuffix) {
            this.placeholderSuffix = placeholderSuffix;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public PromptTemplate build() {
            return new PromptTemplate(name, content, placeholderPrefix, placeholderSuffix);
        }
    }
}
