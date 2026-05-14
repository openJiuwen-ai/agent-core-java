/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    public static ToolMessageChunkBuilder builder() {
        return new ToolMessageChunkBuilder();
    }

    /**
     * Merge another tool message chunk into this one.
     *
     * @param other the chunk to merge
     * @return a new merged chunk
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

    public static final class ToolMessageChunkBuilder {
        private String role;
        private Object content;
        private String name;
        private String toolCallId;

        public ToolMessageChunkBuilder role(String role) { this.role = role; return this; }
        public ToolMessageChunkBuilder content(Object content) { this.content = content; return this; }
        public ToolMessageChunkBuilder name(String name) { this.name = name; return this; }
        public ToolMessageChunkBuilder toolCallId(String toolCallId) { this.toolCallId = toolCallId; return this; }

        public ToolMessageChunk build() {
            ToolMessageChunk chunk = new ToolMessageChunk();
            chunk.setRole(role);
            chunk.setContent(content);
            chunk.setName(name);
            chunk.setToolCallId(toolCallId);
            return chunk;
        }
    }
}
