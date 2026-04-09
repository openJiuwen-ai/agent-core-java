  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.context.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.List;
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
        if (msg instanceof com.openjiuwen.core.foundation.llm.schema.AssistantMessage am) {
            return am.getToolCalls() != null && !am.getToolCalls().isEmpty();
        }
        return false;
    }
}
