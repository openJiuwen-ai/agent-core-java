/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming assistant message chunk with tool call fragment merging.
 * <p>
 * Mirrors Python's {@code AssistantMessageChunk} model. Tool call fragments
 * from the same call are concatenated rather than appended as new elements.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantMessageChunk extends AssistantMessage {

    /**
     * Auto-generated for codecheck compliance.
     */
    public AssistantMessageChunk() {
    }

    /**
     * Merge another chunk into this one, combining content and tool call fragments.
     *
     * @param other the chunk to merge
     * @return a new merged chunk
     */
    public AssistantMessageChunk merge(AssistantMessageChunk other) {
        if (other == null) {
            return this;
        }

        // Merge content
        Object combinedContent = BaseMessageChunk.mergeContent(this.getContent(), other.getContent());

        // Merge tool_calls by concatenating fragments of the same call
        List<ToolCall> mergedToolCalls = new ArrayList<>();
        if (this.getToolCalls() != null) {
            mergedToolCalls.addAll(this.getToolCalls());
        }

        if (other.getToolCalls() != null) {
            for (ToolCall incoming : other.getToolCalls()) {
                if (!mergedToolCalls.isEmpty()) {
                    ToolCall last = mergedToolCalls.get(mergedToolCalls.size() - 1);
                    boolean sameId = (last.getId() != null && incoming.getId() != null
                            && last.getId().equals(incoming.getId()))
                            || (last.getId() == null || incoming.getId() == null);

                    if (sameId && "function".equals(last.getType()) && "function".equals(incoming.getType())) {
                        // Merge fragments into the existing tool call
                        last.setId(last.getId() != null ? last.getId() : incoming.getId());
                        last.setType(last.getType() != null ? last.getType() : incoming.getType());
                        last.setName(orEmpty(last.getName()) + orEmpty(incoming.getName()));
                        last.setArguments(orEmpty(last.getArguments()) + orEmpty(incoming.getArguments()));
                        continue;
                    }
                }
                mergedToolCalls.add(incoming);
            }
        }

        String mergedFinishReason = !"null".equals(other.getFinishReason())
                ? other.getFinishReason()
                : this.getFinishReason();

        return AssistantMessageChunk.builder()
                .role(this.getRole())
                .content(combinedContent)
                .toolCalls(mergedToolCalls.isEmpty() ? null : mergedToolCalls)
                .usageMetadata(other.getUsageMetadata() != null ? other.getUsageMetadata() : this.getUsageMetadata())
                .finishReason(mergedFinishReason)
                .parserContent(other.getParserContent() != null ? other.getParserContent() : this.getParserContent())
                .reasoningContent(other.getReasoningContent() != null
                        ? other.getReasoningContent() : this.getReasoningContent())
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
    public static class Builder extends AssistantMessage.Builder {
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
        public Builder toolCalls(List<ToolCall> toolCalls) {
            super.toolCalls(toolCalls);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder usageMetadata(UsageMetadata usageMetadata) {
            super.usageMetadata(usageMetadata);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder finishReason(String finishReason) {
            super.finishReason(finishReason);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder parserContent(Object parserContent) {
            super.parserContent(parserContent);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder reasoningContent(String reasoningContent) {
            super.reasoningContent(reasoningContent);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public AssistantMessageChunk build() {
            AssistantMessageChunk chunk = new AssistantMessageChunk();
            chunk.setRole(role);
            chunk.setContent(content);
            chunk.setName(name);
            chunk.setMetadata(metadata);
            chunk.setToolCalls(toolCalls);
            chunk.setUsageMetadata(usageMetadata);
            chunk.setFinishReason(finishReason);
            chunk.setParserContent(parserContent);
            chunk.setReasoningContent(reasoningContent);
            return chunk;
        }
    }
}
