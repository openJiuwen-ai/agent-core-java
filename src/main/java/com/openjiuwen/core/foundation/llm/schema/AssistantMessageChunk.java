/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code AssistantMessageChunk} in
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssistantMessageChunk extends AssistantMessage {

    public AssistantMessageChunk merge(Object other) {
        if (!(other instanceof AssistantMessageChunk otherChunk)) {
            throw new IllegalArgumentException("Cannot merge AssistantMessageChunk with " + other);
        }

        Object combinedContent = MessageChunkMerge.mergeParserContent(getContent(), otherChunk.getContent());
        List<ToolCall> mergedToolCalls = mergeToolCalls(getToolCalls(), otherChunk.getToolCalls());
        String mergedFinishReason = !"null".equals(otherChunk.getFinishReason())
                ? otherChunk.getFinishReason()
                : getFinishReason();

        return AssistantMessageChunk.builder()
                .role(getRole())
                .content(combinedContent)
                .toolCalls(mergedToolCalls.isEmpty() ? null : mergedToolCalls)
                .usageMetadata(otherChunk.getUsageMetadata() != null ? otherChunk.getUsageMetadata() : getUsageMetadata())
                .finishReason(mergedFinishReason)
                .parserContent(MessageChunkMerge.mergeParserContent(getParserContent(), otherChunk.getParserContent()))
                .reasoningContent(orEmpty(getReasoningContent()) + orEmpty(otherChunk.getReasoningContent()))
                .promptTokenIds(preferLeft(getPromptTokenIds(), otherChunk.getPromptTokenIds()))
                .completionTokenIds(MessageChunkMerge.concatTokenIds(getCompletionTokenIds(), otherChunk.getCompletionTokenIds()))
                .logprobs(MessageChunkMerge.mergeLogprobs(getLogprobs(), otherChunk.getLogprobs()))
                .build();
    }

    private static List<ToolCall> mergeToolCalls(List<ToolCall> left, List<ToolCall> right) {
        List<ToolCall> merged = new ArrayList<>();
        if (left != null) {
            for (ToolCall toolCall : left) {
                merged.add(copyToolCall(toolCall));
            }
        }
        if (right != null) {
            for (ToolCall incoming : right) {
                if (!merged.isEmpty()) {
                    ToolCall last = merged.get(merged.size() - 1);
                    boolean sameId = (last.getId() != null && incoming.getId() != null && last.getId().equals(incoming.getId()))
                            || (last.getId() == null || incoming.getId() == null);
                    if (sameId && "function".equals(last.getType()) && "function".equals(incoming.getType())) {
                        merged.set(merged.size() - 1, ToolCall.builder()
                                .id(last.getId() != null ? last.getId() : incoming.getId())
                                .type(last.getType() != null ? last.getType() : incoming.getType())
                                .name(!orEmpty(last.getName()).isEmpty() ? last.getName() : incoming.getName())
                                .arguments(orEmpty(last.getArguments()) + orEmpty(incoming.getArguments()))
                                .index(last.getIndex())
                                .build());
                        continue;
                    }
                }
                merged.add(copyToolCall(incoming));
            }
        }
        return merged;
    }

    private static ToolCall copyToolCall(ToolCall toolCall) {
        return ToolCall.builder()
                .id(toolCall.getId())
                .type(toolCall.getType())
                .name(toolCall.getName())
                .arguments(toolCall.getArguments())
                .index(toolCall.getIndex())
                .build();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<Integer> preferLeft(List<Integer> left, List<Integer> right) {
        return (left != null && !left.isEmpty()) ? left : right;
    }
}
