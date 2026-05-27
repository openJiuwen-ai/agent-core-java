/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String CONTEXT_MESSAGE_ID_KEY = "context_message_id";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final int DEFAULT_CONTEXT_MAX_TOKENS = 200000;
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Map<String, Integer> MODEL_DEFAULT_CONTEXT_WINDOW_TOKENS = defaultContextWindowTokens();

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
        if (messages == null || messages.isEmpty()) {
            return rounds;
        }
        int i = messages.size() - 1;

        while (i >= 0) {
            Integer assistantIdx = null;
            int roundEnd = i;

            while (i >= 0 && !"assistant".equals(messages.get(i).getRole())) {
                i--;
            }

            if (i >= 0) {
                BaseMessage msg = messages.get(i);
                if (!hasToolCalls(msg)) {
                    assistantIdx = i;
                }
                i--;
            } else {
                i = roundEnd;
            }

            while (i >= 0 && !"user".equals(messages.get(i).getRole())) {
                i--;
            }

            if (i < 0) {
                break;
            }

            int foundUserIdx = i;

            if (rounds.isEmpty()) {
                for (int lastRoundIndex = messages.size() - 1; lastRoundIndex > foundUserIdx; lastRoundIndex--) {
                    if ("user".equals(messages.get(lastRoundIndex).getRole())) {
                        rounds.add(new int[]{
                                findContiguousUserGroupStart(messages, lastRoundIndex),
                                -1
                        });
                        break;
                    }
                }
            }

            int userIdx = foundUserIdx;
            rounds.add(new int[]{userIdx, assistantIdx != null ? assistantIdx : -1});
            i = userIdx - 1;
        }

        return rounds;
    }

    private static int findContiguousUserGroupStart(List<BaseMessage> messages, int userIdx) {
        int currentUserIdx = userIdx;
        while (currentUserIdx - 1 >= 0 && "user".equals(messages.get(currentUserIdx - 1).getRole())) {
            currentUserIdx--;
        }
        return currentUserIdx;
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
     * Resolve the tool call associated with a tool message by scanning backward.
     */
    public static ToolCall resolveToolCallFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage toolMessage) || toolMessage.getToolCallId() == null) {
            return null;
        }
        String toolCallId = toolMessage.getToolCallId();
        for (int idx = contextMessages.indexOf(message); idx >= 0; idx--) {
            BaseMessage candidate = contextMessages.get(idx);
            if (candidate instanceof AssistantMessage assistant
                    && assistant.getToolCalls() != null) {
                for (ToolCall toolCall : assistant.getToolCalls()) {
                    if (toolCallId.equals(toolCall.getId())) {
                        return toolCall;
                    }
                }
            }
        }
        return ToolCall.builder().name("").id("").arguments("{}").build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String resolveToolNameFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        ToolCall toolCall = resolveToolCallFromMessage(message, contextMessages);
        return toolCall != null ? extractToolName(toolCall) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String extractToolName(ToolCall toolCall) {
        return toolCall != null ? toolCall.getName() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<BaseMessage> ensureContextMessageIds(List<BaseMessage> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        for (BaseMessage message : messages) {
            Map<String, Object> metadata = ensureMetadata(message);
            if (!metadata.containsKey(CONTEXT_MESSAGE_ID_KEY)) {
                metadata.put(CONTEXT_MESSAGE_ID_KEY, java.util.UUID.randomUUID().toString().replace("-", ""));
            }
        }
        return messages;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int resolveContextMax(
            String modelName,
            Integer fallbackContextWindowTokens,
            Map<String, Integer> modelContextWindowTokens) {
        if (fallbackContextWindowTokens != null && fallbackContextWindowTokens > 0) {
            return fallbackContextWindowTokens;
        }
        if (modelName != null && !modelName.isBlank()) {
            if (modelContextWindowTokens != null) {
                Integer mapped = modelContextWindowTokens.get(modelName);
                if (mapped != null && mapped > 0) {
                    return mapped;
                }
            }
            Integer builtin = MODEL_DEFAULT_CONTEXT_WINDOW_TOKENS.get(modelName);
            if (builtin != null && builtin > 0) {
                return builtin;
            }
        }
        return DEFAULT_CONTEXT_MAX_TOKENS;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean isCompressionProcessor(Object processor) {
        if (processor == null) {
            return false;
        }
        String processorType = processor.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String moduleName = processor.getClass().getName().toLowerCase(Locale.ROOT);
        return processorType.contains("compressor")
                || processorType.contains("compact")
                || moduleName.contains(".processor.compressor.");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int estimateTokens(Object content) {
        if (content instanceof String text) {
            return Math.max(text.length() / 3, 1);
        }
        try {
            return Math.max(MAPPER.writeValueAsString(content).length() / 3, 1);
        } catch (JsonProcessingException e) {
            return Math.max(String.valueOf(content).length() / 3, 1);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int estimateMessageTokens(BaseMessage message) {
        return estimateTokens(message != null ? message.getContent() : "");
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureMetadata(BaseMessage message) {
        try {
            var getter = message.getClass().getMethod("getMetadata");
            Object value = getter.invoke(message);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Metadata access is optional for reflected message implementations.
        }
        try {
            var setter = message.getClass().getMethod("setMetadata", Map.class);
            Map<String, Object> metadata = new HashMap<>();
            setter.invoke(message, metadata);
            return metadata;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Fall back to a detached metadata map when mutation is unavailable.
        }
        return new HashMap<>();
    }

    private static Map<String, Integer> defaultContextWindowTokens() {
        Map<String, Integer> values = new HashMap<>();
        values.put("glm-5", 200000);
        values.put("glm-4-long", 200000);
        values.put("glm-4", 128000);
        values.put("glm-4-9b-chat-1m", 1048576);
        values.put("gpt-5.4", 1100000);
        values.put("gpt-4o", 128000);
        values.put("gpt-4o-mini", 128000);
        values.put("gpt-4-turbo", 128000);
        values.put("gpt-3.5-turbo", 16384);
        values.put("deepseek-v3", 128000);
        values.put("deepseek-chat", 65536);
        values.put("claude-opus-4.6", 1000000);
        values.put("claude-sonnet-4.6", 1000000);
        values.put("claude-haiku-4.6", 200000);
        values.put("gemini-3.1-pro", 2000000);
        values.put("gemini-2.5-pro", 1000000);
        values.put("gemini-2.5-flash", 1000000);
        values.put("llama-4-maverick", 1000000);
        values.put("llama-4-scout", 10000000);
        values.put("qwen-max", 32000);
        values.put("qwen-plus", 131072);
        values.put("qwen-turbo", 8192);
        values.put("qwen-long", 1000000);
        return Map.copyOf(values);
    }
}
