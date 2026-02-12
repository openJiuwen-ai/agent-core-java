// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * Base message class for LLM interactions.
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message.py
 */
public class BaseMessage {
    private String role;
    private Object content; // Union[str, List[Union[str, dict]]]
    private String name;

    public BaseMessage() {
    }

    public BaseMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }

    public BaseMessage(String role, Object content, String name) {
        this.role = role;
        this.content = content;
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseMessage that = (BaseMessage) o;
        return java.util.Objects.equals(role, that.role) &&
                java.util.Objects.equals(content, that.content) &&
                java.util.Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(role, content, name);
    }
}

