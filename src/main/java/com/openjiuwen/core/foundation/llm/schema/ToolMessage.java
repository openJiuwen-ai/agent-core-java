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

import java.util.Map;

/**
 * Mirrors Python's {@code ToolMessage} in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessage extends BaseMessage {

    @JsonProperty("tool_call_id")
    private String toolCallId;

    public ToolMessage(String content, String toolCallId) {
        super("tool", content);
        this.toolCallId = toolCallId;
    }

    public ToolMessage(String content, String toolCallId, String name) {
        this(content, toolCallId);
        setName(name);
    }

    @Override
    public String getRole() {
        String value = super.getRole();
        return value != null ? value : "tool";
    }

    @Override
    public Map<String, Object> modelDump() {
        Map<String, Object> result = super.modelDump();
        result.put("role", getRole());
        if (toolCallId != null) {
            result.put("tool_call_id", toolCallId);
        }
        return result;
    }

    @Override
    public Map<String, Object> model_dump() {
        return modelDump();
    }
}
