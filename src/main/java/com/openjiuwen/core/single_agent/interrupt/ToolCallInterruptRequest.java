/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Interrupt request with tool call context.
 *
 * <p>Inherits from InterruptRequest and adds tool call context fields.
 * Used for serializing interrupt info to user output.</p>
 *
 * <p>Mirrors Python's {@code ToolCallInterruptRequest} in
 * {@code openjiuwen.core.single_agent.interrupt.response}.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCallInterruptRequest extends InterruptRequest {

    // Extra fields storage (for subclasses like AskUserRequest)
    private Map<String, Object> extraFields = new HashMap<>();

    @Builder.Default
    private String toolName = "";

    @Builder.Default
    private String toolCallId = "";

    private Object toolArgs;

    private Integer index;

    /**
     * Allow extra fields to be set during JSON deserialization.
     */
    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    /**
     * Create ToolCallInterruptRequest from InterruptRequest and ToolCall.
     *
     * <p>Preserves all fields from the request, including any extra fields
     * defined in subclasses.</p>
     *
     * @param request  the base interrupt request
     * @param toolCall the tool call object (with name, id, arguments, index)
     * @return new ToolCallInterruptRequest instance
     */
    public static ToolCallInterruptRequest fromToolCall(InterruptRequest request, Object toolCall) {
        ToolCallInterruptRequestBuilder builder = ToolCallInterruptRequest.builder()
                .message(request.getMessage())
                .payloadSchema(request.getPayloadSchema())
                .autoConfirmKey(request.getAutoConfirmKey());

        // Extract tool call attributes
        if (toolCall != null) {
            try {
                // Handle ToolCall-like objects via reflection
                java.lang.reflect.Method getName = toolCall.getClass().getMethod("getName");
                java.lang.reflect.Method getId = toolCall.getClass().getMethod("getId");
                java.lang.reflect.Method getArguments = toolCall.getClass().getMethod("getArguments");
                java.lang.reflect.Method getIndex = toolCall.getClass().getMethod("getIndex");

                builder.toolName((String) getName.invoke(toolCall));
                builder.toolCallId((String) getId.invoke(toolCall));
                builder.toolArgs(getArguments.invoke(toolCall));
                builder.index((Integer) getIndex.invoke(toolCall));
            } catch (Exception e) {
                // Fallback: treat toolCall as string
                builder.toolName(String.valueOf(toolCall));
            }
        }

        return builder.build();
    }
}