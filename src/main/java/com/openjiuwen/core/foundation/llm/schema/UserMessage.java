// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * User message class.
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message.py
 */
public class UserMessage extends BaseMessage {
    public UserMessage() {
        super();
        setRole("user");
    }

    public UserMessage(String content) {
        super("user", content);
    }

    /**
     * 静态工厂方法，便于快速创建UserMessage。
     *
     * @param content 消息内容
     * @return 新的UserMessage实例
     */
    public static UserMessage of(String content) {
        return new UserMessage(content);
    }

    public UserMessage(String content, String name) {
        super("user", content, name);
    }

    /**
     * Builder类
     */
    public static class Builder {
        private Object content;
        private String name;

        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public UserMessage build() {
            UserMessage message = new UserMessage();
            message.setContent(content);
            if (name != null) {
                message.setName(name);
            }
            return message;
        }
    }
}

