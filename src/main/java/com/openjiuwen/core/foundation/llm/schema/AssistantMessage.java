// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI助手消息类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message.py - AssistantMessage
 */
public class AssistantMessage extends BaseMessage {
    private List<ToolCall> toolCalls;
    private UsageMetadata usageMetadata;
    private String finishReason = "null";
    private Object parserContent;
    private String reasoningContent;

    public AssistantMessage() {
        super();
        setRole("assistant");
    }

    public AssistantMessage(String content) {
        super("assistant", content);
    }

    public AssistantMessage(String content, List<ToolCall> toolCalls) {
        super("assistant", content);
        this.toolCalls = toolCalls;
    }

    /**
     * 静态工厂方法，便于快速创建AssistantMessage。
     *
     * @param content 消息内容
     * @return 新的AssistantMessage实例
     */
    public static AssistantMessage of(String content) {
        return new AssistantMessage(content);
    }

    /**
     * 从Map创建AssistantMessage（处理OpenAI格式的tool_calls转换）
     * 对应 Python: convert_openai_tool_calls_format
     */
    @SuppressWarnings("unchecked")
    public static AssistantMessage fromMap(Map<String, Object> data) {
        AssistantMessage message = new AssistantMessage();
        
        if (data.containsKey("role")) {
            message.setRole((String) data.get("role"));
        }
        if (data.containsKey("content")) {
            message.setContent(data.get("content"));
        }
        if (data.containsKey("name")) {
            message.setName((String) data.get("name"));
        }
        if (data.containsKey("finish_reason")) {
            message.setFinishReason((String) data.get("finish_reason"));
        }
        if (data.containsKey("reasoning_content")) {
            message.setReasoningContent((String) data.get("reasoning_content"));
        }
        if (data.containsKey("parser_content")) {
            message.setParserContent(data.get("parser_content"));
        }
        
        // 转换 OpenAI 格式的 tool_calls
        if (data.containsKey("tool_calls") && data.get("tool_calls") != null) {
            List<Map<String, Object>> toolCallsList = (List<Map<String, Object>>) data.get("tool_calls");
            List<ToolCall> convertedToolCalls = new ArrayList<>();
            
            for (Map<String, Object> tc : toolCallsList) {
                ToolCall toolCall = new ToolCall();
                toolCall.setId((String) tc.get("id"));
                toolCall.setType((String) tc.getOrDefault("type", "function"));
                
                // 处理嵌套的 function 对象（OpenAI格式）
                if (tc.containsKey("function") && tc.get("function") instanceof Map) {
                    Map<String, Object> function = (Map<String, Object>) tc.get("function");
                    toolCall.setName((String) function.getOrDefault("name", ""));
                    toolCall.setArguments((String) function.getOrDefault("arguments", ""));
                } else {
                    // 扁平格式
                    toolCall.setName((String) tc.getOrDefault("name", ""));
                    toolCall.setArguments((String) tc.getOrDefault("arguments", ""));
                }
                
                if (tc.containsKey("index")) {
                    toolCall.setIndex((Integer) tc.get("index"));
                }
                
                convertedToolCalls.add(toolCall);
            }
            message.setToolCalls(convertedToolCalls);
        }
        
        return message;
    }

    /**
     * 转换为OpenAI格式的Map
     * 对应 Python: model_dump
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("role", getRole());
        result.put("content", getContent());
        
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> toolCallsList = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                Map<String, Object> tcMap = new HashMap<>();
                tcMap.put("id", call.getId());
                tcMap.put("type", call.getType());
                
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", call.getName());
                functionMap.put("arguments", call.getArguments());
                tcMap.put("function", functionMap);
                
                toolCallsList.add(tcMap);
            }
            result.put("tool_calls", toolCallsList);
        }
        
        if (usageMetadata != null) {
            result.put("usage_metadata", usageMetadata.toMap());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AssistantMessage that = (AssistantMessage) o;
        return Objects.equals(toolCalls, that.toolCalls) &&
                Objects.equals(finishReason, that.finishReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), toolCalls, finishReason);
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String role = "assistant";
        private Object content;
        private String name;
        private List<ToolCall> toolCalls;
        private UsageMetadata usageMetadata;
        private String finishReason = "null";
        private Object parserContent;
        private String reasoningContent;

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder usageMetadata(UsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder parserContent(Object parserContent) {
            this.parserContent = parserContent;
            return this;
        }

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

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
}

