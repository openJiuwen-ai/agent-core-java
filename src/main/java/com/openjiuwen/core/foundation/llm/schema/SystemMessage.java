// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * 系统消息类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message.py - SystemMessage
 */
public class SystemMessage extends BaseMessage {
    
    public SystemMessage() {
        super();
        setRole("system");
    }

    public SystemMessage(String content) {
        super("system", content);
    }

    public SystemMessage(String content, String name) {
        super("system", content, name);
    }

    /**
     * 静态工厂方法，便于快速创建SystemMessage。
     *
     * @param content 消息内容
     * @return 新的SystemMessage实例
     */
    public static SystemMessage of(String content) {
        return new SystemMessage(content);
    }
}

