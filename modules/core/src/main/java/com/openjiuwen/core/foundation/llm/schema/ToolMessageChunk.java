/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Streaming tool message chunk.
 * <p>
 * Mirrors Python's {@code ToolMessageChunk} model.
 * 
 * @since 0.1.7
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessageChunk extends ToolMessage {
    /**
     * ToolMessageChunk.
     * 
     * @since 0.1.7
     */
    public ToolMessageChunk() {
    }

    /**
     * Merge another tool message chunk into this one.
     * 
     * @param other the chunk to merge
     * @return a new merged chunk
     * @since 0.1.7
     */
    public ToolMessageChunk merge(ToolMessageChunk other) {
        if (other == null) {
            return this;
        }
        return ToolMessageChunk.builder().role("tool")
                .content(orEmpty(this.getContentAsString()) + orEmpty(other.getContentAsString()))
                .toolCallId(other.getToolCallId() != null ? other.getToolCallId() : this.getToolCallId()).build();
    }

    /**
     * orEmpty.
     * 
     * @param s s
     * @return the result
     * @since 0.1.7
     */
    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends ToolMessage.Builder {
        /**
         * role.
         * 
         * @param role role
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder role(String role) {
            super.role(role);
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder content(Object content) {
            super.content(content);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * metadata.
         * 
         * @param metadata metadata
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder metadata(java.util.Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * toolCallId.
         * 
         * @param toolCallId toolCallId
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder toolCallId(String toolCallId) {
            super.toolCallId(toolCallId);
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public ToolMessageChunk build() {
            ToolMessageChunk chunk = new ToolMessageChunk();
            chunk.setRole(role);
            chunk.setContent(content);
            chunk.setName(name);
            chunk.setMetadata(metadata);
            chunk.setToolCallId(toolCallId);
            return chunk;
        }
    }
}
