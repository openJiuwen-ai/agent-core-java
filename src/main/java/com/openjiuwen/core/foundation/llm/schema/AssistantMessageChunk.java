/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.openjiuwen.core.common.logging.Loggers;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code AssistantMessageChunk} in
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 *
 * <p>Tool-call fragment merging uses stable keys ({@code index > id > name > anon})
 * plus pure-arguments fallback, matching 730 / tolerant provider streaming.</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssistantMessageChunk extends AssistantMessage {

    public AssistantMessageChunk merge(AssistantMessageChunk other) {
        return merge((Object) other);
    }

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
                .usageMetadata(otherChunk.getUsageMetadata() != null
                        ? otherChunk.getUsageMetadata() : getUsageMetadata())
                .finishReason(mergedFinishReason)
                .parserContent(MessageChunkMerge.mergeParserContent(getParserContent(), otherChunk.getParserContent()))
                .reasoningContent(orEmpty(getReasoningContent()) + orEmpty(otherChunk.getReasoningContent()))
                .promptTokenIds(preferLeft(getPromptTokenIds(), otherChunk.getPromptTokenIds()))
                .completionTokenIds(MessageChunkMerge.concatTokenIds(
                        getCompletionTokenIds(), otherChunk.getCompletionTokenIds()))
                .logprobs(MessageChunkMerge.mergeLogprobs(getLogprobs(), otherChunk.getLogprobs()))
                .build();
    }

    private static List<ToolCall> mergeToolCalls(List<ToolCall> left, List<ToolCall> right) {
        LinkedHashMap<Object, ToolCall> bucket = new LinkedHashMap<>();
        if (left != null) {
            for (ToolCall toolCall : left) {
                bucket.put(keyOf(toolCall), copyToolCall(toolCall));
            }
        }
        if (right != null) {
            for (ToolCall incoming : right) {
                Object key = keyOf(incoming);
                ToolCall exist = bucket.get(key);
                if (exist != null) {
                    appendFragment(exist, incoming);
                    continue;
                }
                // Same call id may key differently when providers send index only on the first fragment.
                ToolCall sameId = findById(bucket, incoming);
                if (sameId != null) {
                    appendFragment(sameId, incoming);
                    continue;
                }
                // Pure-args fragment continues the most recent call (Python falsy id/name).
                if (isPureArgumentsFragment(incoming) && !bucket.isEmpty()) {
                    appendFragment(lastValue(bucket), incoming);
                    continue;
                }
                // Python: merge with last when either side lacks id (streaming deltas).
                if (!bucket.isEmpty() && isFunctionTool(incoming) && missingId(incoming)) {
                    ToolCall last = lastValue(bucket);
                    if (isFunctionTool(last)) {
                        appendFragment(last, incoming);
                        continue;
                    }
                }
                // Late-arriving name on a single anonymous bucket entry.
                if (hasOwnName(incoming) && bucket.size() == 1) {
                    ToolCall only = lastValue(bucket);
                    if (!hasOwnName(only)) {
                        appendFragment(only, incoming);
                        continue;
                    }
                }
                bucket.put(key, copyToolCall(incoming));
            }
        }
        return new ArrayList<>(bucket.values());
    }

    private static Object keyOf(ToolCall toolCall) {
        if (toolCall == null) {
            return "anon";
        }
        if (toolCall.getIndex() != null) {
            return "idx:" + toolCall.getIndex();
        }
        if (toolCall.getId() != null && !toolCall.getId().isEmpty()) {
            return "id:" + toolCall.getId();
        }
        if (toolCall.getName() != null && !toolCall.getName().isEmpty()) {
            return "name:" + toolCall.getName();
        }
        return "anon";
    }

    private static void appendFragment(ToolCall base, ToolCall incoming) {
        if (base.getId() == null || base.getId().isEmpty()) {
            base.setId(incoming.getId());
        }
        if (base.getType() == null || base.getType().isEmpty()) {
            base.setType(incoming.getType() != null ? incoming.getType() : "function");
        }
        if (base.getName() == null || base.getName().isEmpty()) {
            base.setName(incoming.getName());
        } else if (incoming.getName() != null && !incoming.getName().isEmpty()
                && !base.getName().equals(incoming.getName())) {
            Loggers.LLM.debug("[merge] name conflict keeping existing={}, incoming={}",
                    base.getName(), incoming.getName());
        }
        if (base.getIndex() == null && incoming.getIndex() != null) {
            base.setIndex(incoming.getIndex());
        }
        base.setArguments(orEmpty(base.getArguments()) + orEmpty(incoming.getArguments()));
    }

    private static boolean isPureArgumentsFragment(ToolCall toolCall) {
        if (toolCall == null) {
            return false;
        }
        boolean noId = toolCall.getId() == null || toolCall.getId().isEmpty();
        boolean noName = toolCall.getName() == null || toolCall.getName().isEmpty();
        boolean noIndex = toolCall.getIndex() == null;
        boolean hasArgs = toolCall.getArguments() != null && !toolCall.getArguments().isEmpty();
        return noId && noName && noIndex && hasArgs;
    }

    private static boolean hasOwnName(ToolCall toolCall) {
        return toolCall != null && toolCall.getName() != null && !toolCall.getName().isEmpty();
    }

    private static boolean missingId(ToolCall toolCall) {
        return toolCall == null || toolCall.getId() == null || toolCall.getId().isEmpty();
    }

    private static boolean isFunctionTool(ToolCall toolCall) {
        return toolCall != null && "function".equals(toolCall.getType());
    }

    private static ToolCall findById(LinkedHashMap<Object, ToolCall> bucket, ToolCall incoming) {
        if (incoming == null || missingId(incoming)) {
            return null;
        }
        for (ToolCall existing : bucket.values()) {
            if (incoming.getId().equals(existing.getId())) {
                return existing;
            }
        }
        return null;
    }

    private static ToolCall lastValue(Map<Object, ToolCall> bucket) {
        ToolCall last = null;
        for (ToolCall value : bucket.values()) {
            last = value;
        }
        return last;
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
