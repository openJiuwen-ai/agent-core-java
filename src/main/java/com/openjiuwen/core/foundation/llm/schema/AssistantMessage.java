/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant message from LLM response, with optional tool calls and metadata.
 * <p>
 * Mirrors Python's {@code AssistantMessage} model. Handles conversion between
 * OpenAI nested format and flat {@link ToolCall} format during deserialization.
 * 
 * @since 0.1.7
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantMessage extends BaseMessage {
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    @JsonProperty("usage_metadata")
    private UsageMetadata usageMetadata;

    @JsonProperty("finish_reason")
    private String finishReason;

    @JsonProperty("parser_content")
    private Object parserContent;

    @JsonProperty("reasoning_content")
    private String reasoningContent;

    /**
     * AssistantMessage.
     * 
     * @since 0.1.7
     */
    public AssistantMessage() {
    }

    /**
     * AssistantMessage.
     * 
     * @param role role
     * @param content content
     * @param name name
     * @param metadata metadata
     * @param toolCalls toolCalls
     * @param usageMetadata usageMetadata
     * @param finishReason finishReason
     * @param parserContent parserContent
     * @param reasoningContent reasoningContent
     * @since 0.1.7
     */
    public AssistantMessage(String role, Object content, String name, Map<String, Object> metadata,
            List<ToolCall> toolCalls, UsageMetadata usageMetadata, String finishReason, Object parserContent,
            String reasoningContent) {
        super(role, content, name, metadata);
        this.toolCalls = toolCalls;
        this.usageMetadata = usageMetadata;
        this.finishReason = finishReason;
        this.parserContent = parserContent;
        this.reasoningContent = reasoningContent;
    }

    /**
     * Create an assistant message with string content.
     * 
     * @param content the message content
     * @since 0.1.7
     */
    public AssistantMessage(String content) {
        super("assistant", content);
        this.finishReason = "null";
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "assistant";
    }

    /**
     * getToolCalls.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * setToolCalls.
     * 
     * @param toolCalls toolCalls
     * @since 0.1.7
     */
    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    /**
     * getUsageMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public UsageMetadata getUsageMetadata() {
        return usageMetadata;
    }

    /**
     * setUsageMetadata.
     * 
     * @param usageMetadata usageMetadata
     * @since 0.1.7
     */
    public void setUsageMetadata(UsageMetadata usageMetadata) {
        this.usageMetadata = usageMetadata;
    }

    /**
     * getFinishReason.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getFinishReason() {
        return finishReason;
    }

    /**
     * setFinishReason.
     * 
     * @param finishReason finishReason
     * @since 0.1.7
     */
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    /**
     * getParserContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getParserContent() {
        return parserContent;
    }

    /**
     * setParserContent.
     * 
     * @param parserContent parserContent
     * @since 0.1.7
     */
    public void setParserContent(Object parserContent) {
        this.parserContent = parserContent;
    }

    /**
     * getReasoningContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReasoningContent() {
        return reasoningContent;
    }

    /**
     * setReasoningContent.
     * 
     * @param reasoningContent reasoningContent
     * @since 0.1.7
     */
    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    // ==================== OpenAI Format Conversion ====================

    /**
     * Convert OpenAI API nested tool_calls format to flat {@link ToolCall} format.
     * <p>
     * OpenAI format: {@code {"id":"xxx","type":"function","function":{"name":"...","arguments":"..."}}}
     * <br>
     * Flat format: {@code {"id":"xxx","type":"function","name":"...","arguments":"..."}}
     * 
     * @param rawToolCalls list of raw tool call maps from API
     * @return list of converted {@link ToolCall} instances
     * @since 0.1.7
     */
    public static List<ToolCall> convertOpenAiToolCalls(List<Map<String, Object>> rawToolCalls) {
        if (rawToolCalls == null || rawToolCalls.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<ToolCall> result = new ArrayList<>();
        for (Map<String, Object> tc : rawToolCalls) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) tc.get("function");
            if (function != null) {
                result.add(
                        ToolCall.builder().id((String) tc.get("id")).type((String) tc.getOrDefault("type", "function"))
                                .name((String) function.getOrDefault("name", ""))
                                .arguments((String) function.getOrDefault("arguments", ""))
                                .index(tc.get("index") instanceof Number n ? n.intValue() : 0).build());
            } else {
                result.add(ToolCall.builder().id((String) tc.get("id"))
                        .type((String) tc.getOrDefault("type", "function")).name((String) tc.getOrDefault("name", ""))
                        .arguments((String) tc.getOrDefault("arguments", ""))
                        .index(tc.get("index") instanceof Number n ? n.intValue() : 0).build());
            }
        }
        return result;
    }

    /**
     * Convert this message to OpenAI-compatible dict format for API requests.
     * 
     * @return a map containing the message in API format
     * @since 0.1.7
     */
    public Map<String, Object> toApiFormat() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", getRole());
        result.put("content", getContent());

        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> toolCallList = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                Map<String, Object> tcMap = new LinkedHashMap<>();
                tcMap.put("id", call.getId());
                tcMap.put("type", call.getType());
                Map<String, String> fnMap = new LinkedHashMap<>();
                fnMap.put("name", call.getName());
                fnMap.put("arguments", call.getArguments());
                tcMap.put("function", fnMap);
                toolCallList.add(tcMap);
            }
            result.put("tool_calls", toolCallList);
        }
        if (usageMetadata != null) {
            result.put("usage_metadata", usageMetadata);
        }
        if (finishReason != null) {
            result.put("finish_reason", finishReason);
        }
        if (parserContent != null) {
            result.put("parser_content", parserContent);
        }
        if (reasoningContent != null) {
            result.put("reasoning_content", reasoningContent);
        }
        return result;
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
    public static class Builder extends BaseMessage.Builder {
        /**
         * toolCalls.
         * 
         * @since 0.1.7
         */
        protected List<ToolCall> toolCalls;

        /**
         * usageMetadata.
         * 
         * @since 0.1.7
         */
        protected UsageMetadata usageMetadata;

        /**
         * finishReason.
         * 
         * @since 0.1.7
         */
        protected String finishReason;

        /**
         * parserContent.
         * 
         * @since 0.1.7
         */
        protected Object parserContent;

        /**
         * reasoningContent.
         * 
         * @since 0.1.7
         */
        protected String reasoningContent;

        /**
         * role.
         * 
         * @param role role
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder role(String role) {
            super.role(role);
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder content(Object content) {
            super.content(content);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * metadata.
         * 
         * @param metadata metadata
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder metadata(Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * toolCalls.
         * 
         * @param toolCalls toolCalls
         * @return the result
         * @since 0.1.7
         */
        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        /**
         * usageMetadata.
         * 
         * @param usageMetadata usageMetadata
         * @return the result
         * @since 0.1.7
         */
        public Builder usageMetadata(UsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
            return this;
        }

        /**
         * finishReason.
         * 
         * @param finishReason finishReason
         * @return the result
         * @since 0.1.7
         */
        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        /**
         * parserContent.
         * 
         * @param parserContent parserContent
         * @return the result
         * @since 0.1.7
         */
        public Builder parserContent(Object parserContent) {
            this.parserContent = parserContent;
            return this;
        }

        /**
         * reasoningContent.
         * 
         * @param reasoningContent reasoningContent
         * @return the result
         * @since 0.1.7
         */
        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public AssistantMessage build() {
            return new AssistantMessage(role, content, name, metadata, toolCalls, usageMetadata, finishReason,
                    parserContent, reasoningContent);
        }
    }
}
