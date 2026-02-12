// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

/**
 * Tool call representation.
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/tool_call.py
 */
public class ToolCall {
    private String id;
    private String type;
    private String name;
    private String arguments;
    private Integer index;

    public ToolCall() {
    }

    public ToolCall(String type, String name, String arguments, String id) {
        this.type = type;
        this.name = name;
        this.arguments = arguments;
        this.id = id;
    }

    public ToolCall(String id, String type, String name, String arguments, Integer index) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.arguments = arguments;
        this.index = index;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolCall toolCall = (ToolCall) o;
        return java.util.Objects.equals(id, toolCall.id) &&
                java.util.Objects.equals(type, toolCall.type) &&
                java.util.Objects.equals(name, toolCall.name) &&
                java.util.Objects.equals(arguments, toolCall.arguments) &&
                java.util.Objects.equals(index, toolCall.index);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, type, name, arguments, index);
    }
}

