/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Streaming tool message chunk.
 * <p>
 * Mirrors Python's {@code ToolMessageChunk} model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessageChunk extends ToolMessage {

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolMessageChunk() {
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends ToolMessage.Builder {
        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder role(String role) {
            super.role(role);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder content(Object content) {
            super.content(content);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder metadata(java.util.Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder toolCallId(String toolCallId) {
            super.toolCallId(toolCallId);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
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
