// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * AI助手消息块类，用于流式响应。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message_chunk.py - AssistantMessageChunk
 */
public class AssistantMessageChunk extends AssistantMessage {

    public AssistantMessageChunk() {
        super();
    }

    public AssistantMessageChunk(String content) {
        super(content);
    }

    /**
     * 合并两个消息块。
     * 
     * 合并策略：
     * 1. content: 字符串拼接或List合并
     * 2. tool_calls: 按id合并相同调用的name和arguments
     * 3. finish_reason: 使用非"null"的值
     * 4. 其他字段: 使用非空值
     */
    public AssistantMessageChunk merge(AssistantMessageChunk other) {
        if (other == null) {
            return this;
        }

        AssistantMessageChunk merged = new AssistantMessageChunk();
        merged.setRole(getRole());

        // 合并content
        Object thisContent = getContent();
        Object otherContent = other.getContent();
        if (thisContent instanceof String && otherContent instanceof String) {
            merged.setContent(((String) thisContent) + ((String) otherContent));
        } else if (thisContent instanceof List && otherContent instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> combinedContent = new ArrayList<>((List<Object>) thisContent);
            @SuppressWarnings("unchecked")
            List<Object> otherList = (List<Object>) otherContent;
            combinedContent.addAll(otherList);
            merged.setContent(combinedContent);
        } else {
            merged.setContent(otherContent);
        }

        // 合并name
        merged.setName(getName() != null ? getName() : other.getName());

        // 合并tool_calls - 按id合并同一调用的片段
        List<ToolCall> mergedToolCalls = new ArrayList<>();
        if (getToolCalls() != null) {
            mergedToolCalls.addAll(getToolCalls());
        }

        if (other.getToolCalls() != null) {
            for (ToolCall incoming : other.getToolCalls()) {
                if (!mergedToolCalls.isEmpty()) {
                    ToolCall last = mergedToolCalls.get(mergedToolCalls.size() - 1);
                    boolean sameId = (last.getId() != null && incoming.getId() != null && last.getId().equals(incoming.getId()))
                            || (last.getId() == null || incoming.getId() == null);

                    if (sameId && "function".equals(last.getType()) && "function".equals(incoming.getType())) {
                        // 合并到最后一个tool_call
                        last.setId(last.getId() != null ? last.getId() : incoming.getId());
                        last.setType(last.getType() != null ? last.getType() : incoming.getType());
                        last.setName((last.getName() != null ? last.getName() : "") + 
                                    (incoming.getName() != null ? incoming.getName() : ""));
                        last.setArguments((last.getArguments() != null ? last.getArguments() : "") + 
                                         (incoming.getArguments() != null ? incoming.getArguments() : ""));
                        continue;
                    }
                }
                // 否则作为新的tool_call添加
                mergedToolCalls.add(incoming);
            }
        }
        merged.setToolCalls(mergedToolCalls.isEmpty() ? null : mergedToolCalls);

        // 合并finish_reason - 使用非"null"的值
        String mergedFinishReason = "null".equals(other.getFinishReason()) 
                ? getFinishReason() 
                : other.getFinishReason();
        merged.setFinishReason(mergedFinishReason);

        // 合并其他字段 - 使用非空值
        merged.setUsageMetadata(other.getUsageMetadata() != null ? other.getUsageMetadata() : getUsageMetadata());
        merged.setParserContent(other.getParserContent() != null ? other.getParserContent() : getParserContent());
        merged.setReasoningContent(other.getReasoningContent() != null ? other.getReasoningContent() : getReasoningContent());

        return merged;
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

        public AssistantMessageChunk build() {
            AssistantMessageChunk chunk = new AssistantMessageChunk();
            chunk.setRole(role);
            chunk.setContent(content);
            chunk.setName(name);
            chunk.setToolCalls(toolCalls);
            chunk.setUsageMetadata(usageMetadata);
            chunk.setFinishReason(finishReason);
            chunk.setParserContent(parserContent);
            chunk.setReasoningContent(reasoningContent);
            return chunk;
        }
    }
}

