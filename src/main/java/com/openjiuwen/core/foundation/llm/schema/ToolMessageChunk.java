/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Streaming tool message chunk.
 * <p>
 * Mirrors Python's {@code ToolMessageChunk} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessageChunk extends ToolMessage {

    /**
     * Merge another tool message chunk into this one.
     */
    public ToolMessageChunk merge(ToolMessageChunk other) {
        if (other == null) {
            return this;
        }
        return ToolMessageChunk.builder()
                .role("tool")
                .content(orEmpty(this.getContentAsString()) + orEmpty(other.getContentAsString()))
                .toolCallId(other.getToolCallId() != null ? other.getToolCallId() : this.getToolCallId())
                .build();
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }
}
