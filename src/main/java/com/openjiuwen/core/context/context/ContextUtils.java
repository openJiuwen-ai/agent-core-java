/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility helper functions for manipulating and parsing conversation contexts.
 * All methods are static and stateless.
 * <p>
 * Mirrors Python's {@code ContextUtils} from {@code context_engine/context/context_utils.py}.
 */
public final class ContextUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContextUtils() {
    }

    /**
     * Find the index of the last assistant message without tool calls.
     *
     * @param messages the message list to search
     * @return the index, or empty if not found
     */
    public static Optional<Integer> findLastAiMessageWithoutToolCall(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        for (int idx = messages.size() - 1; idx >= 0; idx--) {
            BaseMessage msg = messages.get(idx);
            if ("assistant".equals(msg.getRole())) {
                if (!hasToolCalls(msg)) {
                    return Optional.of(idx);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Replace a range of messages with target messages.
     *
     * @param messages       the original message list
     * @param targetMessages the replacement messages
     * @param startIndex     the start index (inclusive)
     * @param endIndex       the end index (inclusive)
     * @return the new message list
     */
    public static List<BaseMessage> replaceMessages(
            List<BaseMessage> messages,
            List<BaseMessage> targetMessages,
            int startIndex,
            int endIndex) {

        if (startIndex < 0 || endIndex >= messages.size() || startIndex > endIndex) {
            throw new IndexOutOfBoundsException("Invalid start/end index");
        }
        List<BaseMessage> result = new ArrayList<>(messages.size() - (endIndex - startIndex + 1) + targetMessages.size());
        result.addAll(messages.subList(0, startIndex));
        result.addAll(targetMessages);
        result.addAll(messages.subList(endIndex + 1, messages.size()));
        return result;
    }

    /**
     * Format reloaded messages for display.
     *
     * @param offloadHandle the handle used for offloading
     * @param messages      the reloaded messages
     * @return formatted string
     */
    public static String formatReloadedMessages(String offloadHandle, List<BaseMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("reload messages with handle=").append(offloadHandle).append(":\n");
        for (int i = 0; i < messages.size(); i++) {
            sb.append("message ").append(i + 1).append(": ");
            try {
                sb.append(MAPPER.writeValueAsString(messages.get(i)));
            } catch (JsonProcessingException e) {
                sb.append(messages.get(i).toString());
            }
            if (i < messages.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Find all dialogue rounds in the message list.
     * A round starts from a user message and ends at the next assistant message
     * that contains no tool calls.
     *
     * @param messages the message list
     * @return list of rounds, each represented as [userIndex, assistantIndex] (assistantIndex may be null)
     */
    public static List<int[]> findAllDialogueRound(List<BaseMessage> messages) {
        List<int[]> rounds = new ArrayList<>();
        int i = messages.size() - 1;

        while (i >= 0) {
            Integer assistantIdx = null;

            // Find assistant message
            while (i >= 0 && !"assistant".equals(messages.get(i).getRole())) {
                i--;
            }

            if (i >= 0) {
                BaseMessage msg = messages.get(i);
                boolean hasToolCallsFlag = "assistant".equals(msg.getRole()) && hasToolCalls(msg);
                if (!hasToolCallsFlag) {
                    assistantIdx = i;
                }
                i--;
            }

            // Find user message
            while (i >= 0 && !"user".equals(messages.get(i).getRole())) {
                i--;
            }

            if (i < 0) {
                break;
            }

            int userIdx = i;
            if (rounds.isEmpty()) {
                for (int lastRoundIndex = messages.size() - 1; lastRoundIndex > userIdx; lastRoundIndex--) {
                    if ("user".equals(messages.get(lastRoundIndex).getRole())) {
                        rounds.add(new int[]{lastRoundIndex, -1}); // -1 represents None
                        break;
                    }
                }
            }

            rounds.add(new int[]{userIdx, assistantIdx != null ? assistantIdx : -1});
            i--;
        }

        return rounds;
    }

    /**
     * Find the start index for the last N dialogue rounds.
     *
     * @param messages the message list
     * @param n        number of rounds to retain
     * @return the start index, or -1 if no rounds found
     */
    public static int findLastNDialogueRound(List<BaseMessage> messages, int n) {
        List<int[]> rounds = findAllDialogueRound(messages);
        if (rounds.isEmpty()) {
            return -1;
        }
        int[] targetRound = rounds.get(Math.min(n, rounds.size()) - 1);
        return targetRound[0];
    }

    /**
     * Check whether a message has tool calls (AssistantMessage with non-empty toolCalls).
     */
    private static boolean hasToolCalls(BaseMessage msg) {
        if (msg instanceof AssistantMessage am) {
            return am.getToolCalls() != null && !am.getToolCalls().isEmpty();
        }
        return false;
    }

    // ==================== Tool resolution helpers ====================

    /**
     * Look up the tool_call object that corresponds to a tool message by
     * traversing context backwards.
     * <p>
     * Mirrors Python's {@code ContextUtils.resolve_tool_call_from_message}.
     *
     * @param message         ToolMessage to look up
     * @param contextMessages context message list
     * @return the matching tool_call object, or null if not found
     */
    public static Object resolveToolCallFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage toolMsg)) {
            return null;
        }
        String toolCallId = toolMsg.getToolCallId();
        if (toolCallId == null || toolCallId.isEmpty()) {
            return null;
        }
        for (int i = contextMessages.size() - 1; i >= 0; i--) {
            BaseMessage ctxMsg = contextMessages.get(i);
            if (!(ctxMsg instanceof AssistantMessage assistant)) {
                continue;
            }
            if (assistant.getToolCalls() != null) {
                for (Object toolCall : assistant.getToolCalls()) {
                    if (toolCallMatchesId(toolCall, toolCallId)) {
                        return toolCall;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check if a tool_call object matches a given tool_call_id.
     */
    public static boolean toolCallMatchesId(Object toolCall, String toolCallId) {
        if (toolCall instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return toolCallId.equals(id);
        }
        // attribute-based access
        try {
            Object id = toolCall.getClass().getField("id").get(toolCall);
            return toolCallId.equals(id);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract the tool name from a tool_call object.
     */
    public static String extractToolName(Object toolCall) {
        if (toolCall instanceof Map<?, ?> map) {
            Object function = map.get("function");
            if (function instanceof Map<?, ?> funcMap) {
                Object name = funcMap.get("name");
                if (name instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
            Object name = map.get("name");
            return (name instanceof String s && !s.isEmpty()) ? s : null;
        }
        // attribute-based
        try {
            Object function = toolCall.getClass().getField("function").get(toolCall);
            if (function != null) {
                Object name = function.getClass().getField("name").get(function);
                if (name instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Object name = toolCall.getClass().getField("name").get(toolCall);
            if (name instanceof String s && !s.isEmpty()) {
                return s;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Look up the tool name that corresponds to a tool message.
     * <p>
     * Mirrors Python's {@code ContextUtils.resolve_tool_name_from_message}.
     */
    public static String resolveToolNameFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        Object toolCall = resolveToolCallFromMessage(message, contextMessages);
        if (toolCall == null) {
            return null;
        }
        return extractToolName(toolCall);
    }

    // ==================== Token estimation ====================

    /**
     * Estimate token count from content using a rough chars/3 heuristic.
     * <p>
     * Mirrors Python's {@code ContextUtils.estimate_tokens}.
     */
    public static int estimateTokens(Object content) {
        if (content instanceof String s) {
            return Math.max(s.length() / 3, 1);
        }
        try {
            String json = MAPPER.writeValueAsString(content);
            return Math.max(json.length() / 3, 1);
        } catch (JsonProcessingException e) {
            return Math.max(String.valueOf(content).length() / 3, 1);
        }
    }
}
