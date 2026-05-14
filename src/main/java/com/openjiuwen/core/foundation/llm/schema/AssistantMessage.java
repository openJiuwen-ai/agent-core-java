/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant message from LLM response, with optional tool calls and metadata.
 * <p>
 * Mirrors Python's {@code AssistantMessage} model. Handles conversion between
 * OpenAI nested format and flat {@link ToolCall} format during deserialization.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
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

    public static AssistantMessageBuilder builder() {
        return new AssistantMessageBuilder();
    }

    // ==================== Constructors ====================

    /**
     * Create an assistant message with string content.
     *
     * @param content the message content
     */
    public AssistantMessage(String content) {
        super("assistant", content);
        this.finishReason = "null";
    }

    public AssistantMessage() {
        super();
        this.finishReason = "null";
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public UsageMetadata getUsageMetadata() {
        return usageMetadata;
    }

    public void setUsageMetadata(UsageMetadata usageMetadata) {
        this.usageMetadata = usageMetadata;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Object getParserContent() {
        return parserContent;
    }

    public void setParserContent(Object parserContent) {
        this.parserContent = parserContent;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public static final class AssistantMessageBuilder {
        private String role;
        private Object content;
        private String name;
        private List<ToolCall> toolCalls;
        private UsageMetadata usageMetadata;
        private String finishReason;
        private Object parserContent;
        private String reasoningContent;

        public AssistantMessageBuilder role(String role) { this.role = role; return this; }
        public AssistantMessageBuilder content(Object content) { this.content = content; return this; }
        public AssistantMessageBuilder name(String name) { this.name = name; return this; }
        public AssistantMessageBuilder toolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; return this; }
        public AssistantMessageBuilder usageMetadata(UsageMetadata usageMetadata) { this.usageMetadata = usageMetadata; return this; }
        public AssistantMessageBuilder finishReason(String finishReason) { this.finishReason = finishReason; return this; }
        public AssistantMessageBuilder parserContent(Object parserContent) { this.parserContent = parserContent; return this; }
        public AssistantMessageBuilder reasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; return this; }

        public AssistantMessage build() {
            AssistantMessage message = new AssistantMessage();
            message.setRole(role);
            message.setContent(content);
            message.setName(name);
            message.setToolCalls(toolCalls);
            message.setUsageMetadata(usageMetadata);
            message.setFinishReason(finishReason);
            message.setParserContent(parserContent);
            message.setReasoningContent(reasoningContent);
            return message;
        }
    }

    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "assistant";
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
     */
    public static List<ToolCall> convertOpenAiToolCalls(List<Map<String, Object>> rawToolCalls) {
        if (rawToolCalls == null || rawToolCalls.isEmpty()) {
            return null;
        }
        List<ToolCall> result = new ArrayList<>();
        for (Map<String, Object> tc : rawToolCalls) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) tc.get("function");
            if (function != null) {
                result.add(ToolCall.builder()
                        .id((String) tc.get("id"))
                        .type((String) tc.getOrDefault("type", "function"))
                        .name((String) function.getOrDefault("name", ""))
                        .arguments((String) function.getOrDefault("arguments", ""))
                        .index(tc.get("index") != null ? ((Number) tc.get("index")).intValue() : null)
                        .build());
            } else {
                result.add(ToolCall.builder()
                        .id((String) tc.get("id"))
                        .type((String) tc.getOrDefault("type", "function"))
                        .name((String) tc.getOrDefault("name", ""))
                        .arguments((String) tc.getOrDefault("arguments", ""))
                        .index(tc.get("index") != null ? ((Number) tc.get("index")).intValue() : null)
                        .build());
            }
        }
        return result;
    }

    /**
     * Convert this message to OpenAI-compatible dict format for API requests.
     *
     * @return a map containing the message in API format
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
}
