// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * Tool message class.
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message.py
 */
public class ToolMessage extends BaseMessage {
    private String toolCallId;

    public ToolMessage() {
        super();
        setRole("tool");
    }

    public ToolMessage(String toolCallId, Object content) {
        super("tool", content);
        this.toolCallId = toolCallId;
    }

    /**
     * 静态工厂方法，便于快速创建ToolMessage。
     *
     * @param toolCallId 工具调用ID
     * @param content    消息内容
     * @return 新的ToolMessage实例
     */
    public static ToolMessage of(String toolCallId, String content) {
        return new ToolMessage(toolCallId, content);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ToolMessage that = (ToolMessage) o;
        return java.util.Objects.equals(toolCallId, that.toolCallId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), toolCallId);
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String toolCallId;
        private Object content;

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        public ToolMessage build() {
            ToolMessage message = new ToolMessage();
            message.setToolCallId(toolCallId);
            message.setContent(content);
            return message;
        }
    }
}

