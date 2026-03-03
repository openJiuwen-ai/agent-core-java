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

import java.util.ArrayList;
import java.util.List;

/**
 * Streaming assistant message chunk with tool call fragment merging.
 * <p>
 * Mirrors Python's {@code AssistantMessageChunk} model. Tool call fragments
 * from the same call are concatenated rather than appended as new elements.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantMessageChunk extends AssistantMessage {

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
}
