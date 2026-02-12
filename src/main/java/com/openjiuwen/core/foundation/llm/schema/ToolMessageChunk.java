// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * 工具消息块类，用于流式响应。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message_chunk.py - ToolMessageChunk
 */
public class ToolMessageChunk extends ToolMessage {

    public ToolMessageChunk() {
        super();
    }

    public ToolMessageChunk(String toolCallId, String content) {
        super(toolCallId, content);
    }

    /**
     * 合并两个工具消息块。
     */
    public ToolMessageChunk merge(ToolMessageChunk other) {
        if (other == null) {
            return this;
        }

        String thisContent = getContent() != null ? getContent().toString() : "";
        String otherContent = other.getContent() != null ? other.getContent().toString() : "";
        String combinedContent = thisContent + otherContent;

        // toolCallId使用other的值（如果有的话）
        String toolCallId = other.getToolCallId() != null ? other.getToolCallId() : getToolCallId();

        return new ToolMessageChunk(toolCallId, combinedContent);
    }
}

