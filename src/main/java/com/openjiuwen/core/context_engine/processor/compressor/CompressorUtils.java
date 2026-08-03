/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.context_engine.context.SessionMemorySupport;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions shared by context compressor processors.
 *
 * <p>Mirrors Python's helper functions in
 * {@code openjiuwen/core/context_engine/processor/compressor/util.py}.</p>
 */
public final class CompressorUtils {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Pattern JSON_STRING_FIELD =
            Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_INT_FIELD =
            Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");

    private CompressorUtils() {
    }

    public static String messageToText(BaseMessage message) {
        Object content = message == null ? "" : message.getContent();
        if (content instanceof String text) {
            return text;
        }
        try {
            return JSON_MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            return String.valueOf(content);
        }
    }

    public static String messageSignature(BaseMessage message) {
        List<String> toolCallIds = new ArrayList<>();
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null) {
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                toolCallIds.add(toolCall.getId() == null ? "" : toolCall.getId());
            }
        }
        return (message == null ? "" : message.getRole())
                + "|" + messageToText(message)
                + "|" + String.join("|", toolCallIds);
    }

    public static boolean roundContainsSkillRead(List<BaseMessage> messages) {
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            if (!(message instanceof AssistantMessage assistantMessage) || assistantMessage.getToolCalls() == null) {
                continue;
            }
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                if (!"read_file".equals(toolCall.getName())) {
                    continue;
                }
                String filePath = extractArgumentValue(parseToolArguments(toolCall.getArguments()),
                        toolCall.getArguments(), List.of("file_path"));
                if (isSkillFilePath(filePath)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isSkillFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String normalized = filePath.replace('\\', '/').toLowerCase();
        return normalized.endsWith("/skill.md") || normalized.endsWith("skill.md");
    }

    public static String describeToolCall(String toolName, String argumentsText) {
        Map<String, Object> parsedArguments = parseToolArguments(argumentsText);
        if ("read_file".equals(toolName)) {
            String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
            return "read_file path=" + (filePath.isBlank() ? "[unknown]" : filePath);
        }
        if ("write_file".equals(toolName)) {
            String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
            return "write_file path=" + (filePath.isBlank() ? "[unknown]" : filePath);
        }
        if ("edit_file".equals(toolName)) {
            String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
            return "edit_file path=" + (filePath.isBlank() ? "[unknown]" : filePath);
        }
        if ("glob".equals(toolName)) {
            String pattern = extractArgumentValue(parsedArguments, argumentsText, List.of("pattern"));
            String path = extractArgumentValue(parsedArguments, argumentsText, List.of("path"));
            return "glob pattern=" + (pattern.isBlank() ? "[unknown]" : pattern)
                    + " path=" + (path.isBlank() ? "." : path);
        }
        if ("grep".equals(toolName)) {
            String pattern = extractArgumentValue(parsedArguments, argumentsText, List.of("pattern"));
            String path = extractArgumentValue(parsedArguments, argumentsText, List.of("path", "file_path"));
            return "grep pattern=" + (pattern.isBlank() ? "[unknown]" : pattern)
                    + " path=" + (path.isBlank() ? "[unknown]" : path);
        }
        return (toolName == null ? "" : toolName) + " args=" + (argumentsText == null ? "" : argumentsText);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseToolArguments(String argumentsText) {
        if (argumentsText == null || argumentsText.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = JSON_MAPPER.readValue(argumentsText, Object.class);
            if (parsed instanceof Map<?, ?> rawMap) {
                Map<String, Object> result = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        } catch (JsonProcessingException ignored) {
            // Python returns an empty dict when JSON parsing fails.
        }
        return Map.of();
    }

    public static String extractArgumentValue(Map<String, Object> parsedArguments, String argumentsText,
                                              List<String> keys) {
        Map<String, Object> safeArguments = parsedArguments == null ? Map.of() : parsedArguments;
        for (String key : keys == null ? List.<String>of() : keys) {
            Object value = safeArguments.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        String safeText = argumentsText == null ? "" : argumentsText;
        for (String key : keys == null ? List.<String>of() : keys) {
            Matcher matcher = Pattern.compile(String.format(JSON_STRING_FIELD.pattern(), Pattern.quote(key)))
                    .matcher(safeText);
            if (matcher.find()) {
                return matcher.group(1).strip();
            }
        }
        return "";
    }

    public static String findToolResultText(List<BaseMessage> messages, String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return "";
        }
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = safeMessages.size() - 1; index >= 0; index--) {
            BaseMessage message = safeMessages.get(index);
            if (message instanceof ToolMessage toolMessage && toolCallId.equals(toolMessage.getToolCallId())) {
                return messageToText(message);
            }
        }
        return "";
    }

    public static String extractToolResultHint(String toolName, String resultText, List<String> allowedToolNames) {
        if (resultText == null || resultText.isBlank()) {
            return "";
        }
        if (allowedToolNames == null || !allowedToolNames.contains(toolName)) {
            return "";
        }
        if ("read_file".equals(toolName)) {
            List<String> parts = new ArrayList<>();
            String filePath = findJsonString(resultText, "file_path");
            String lineCount = findJsonInt(resultText, "line_count");
            if (!filePath.isBlank()) {
                parts.add("result_path=" + filePath);
            }
            if (!lineCount.isBlank()) {
                parts.add("lines=" + lineCount);
            }
            return String.join(" ", parts);
        }
        if ("glob".equals(toolName)) {
            String count = findJsonInt(resultText, "count");
            return count.isBlank() ? "" : "matches=" + count;
        }
        if ("grep".equals(toolName)) {
            String count = findJsonInt(resultText, "count");
            return count.isBlank() ? "" : "hits=" + count;
        }
        if ("edit_file".equals(toolName)) {
            String replacements = findJsonInt(resultText, "replacements");
            return replacements.isBlank() ? "" : "replacements=" + replacements;
        }
        if ("write_file".equals(toolName)) {
            String bytes = findJsonInt(resultText, "bytes_written");
            return bytes.isBlank() ? "" : "bytes_written=" + bytes;
        }
        return "";
    }

    public static boolean isSummaryMessage(BaseMessage message, String summaryMarker) {
        return message instanceof UserMessage
                && message.getContent() instanceof String text
                && text.startsWith(summaryMarker);
    }

    public static List<Integer> collectSummaryIndices(List<BaseMessage> messages, String summaryMarker) {
        List<Integer> indices = new ArrayList<>();
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = 0; index < safeMessages.size(); index++) {
            if (isSummaryMessage(safeMessages.get(index), summaryMarker)) {
                indices.add(index);
            }
        }
        return indices;
    }

    public static int estimateContentTokens(Object content) {
        if (content instanceof String text) {
            return text.length() / 3;
        }
        try {
            return JSON_MAPPER.writeValueAsString(content).length() / 3;
        } catch (JsonProcessingException ex) {
            return String.valueOf(content).length() / 3;
        }
    }

    public static List<List<BaseMessage>> groupCompletedApiRoundMessages(List<BaseMessage> messages) {
        List<List<BaseMessage>> groups = new ArrayList<>();
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (SessionMemorySupport.ApiRound round : SessionMemorySupport.groupCompletedApiRounds(safeMessages)) {
            groups.add(new ArrayList<>(safeMessages.subList(round.start(), round.end())));
        }
        return groups;
    }

    public static int countMessagesTokens(List<BaseMessage> messages, ModelContext.TokenCounterPort tokenCounter,
                                          String processorType) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        if (safeMessages.isEmpty()) {
            return 0;
        }
        if (tokenCounter != null) {
            try {
                return tokenCounter.countTokens(safeMessages);
            } catch (RuntimeException ignored) {
                // Python logs and falls back to a character estimate when the token counter fails.
            }
        }
        int total = 0;
        for (BaseMessage message : safeMessages) {
            total += estimateContentTokens(message == null ? "" : message.getContent());
        }
        return total;
    }

    public static int findLastCompletedApiRoundEndIdx(List<BaseMessage> messages, int startIdx, int endIdx) {
        if (endIdx < startIdx) {
            return endIdx;
        }
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        List<BaseMessage> candidateMessages = new ArrayList<>(safeMessages.subList(startIdx, endIdx + 1));
        List<SessionMemorySupport.ApiRound> completedRounds =
                SessionMemorySupport.groupCompletedApiRounds(candidateMessages);
        if (completedRounds.isEmpty()) {
            return startIdx - 1;
        }
        SessionMemorySupport.ApiRound completedEnd = completedRounds.get(completedRounds.size() - 1);
        return startIdx + completedEnd.end() - 1;
    }

    public static List<SummaryMergeRange> iterSummaryMergeRanges(List<BaseMessage> messages, String summaryMarker,
                                                                 int minBlocks) {
        List<SummaryMergeRange> ranges = new ArrayList<>();
        Integer startIdx = null;
        Integer previousIdx = null;
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = 0; index < safeMessages.size(); index++) {
            if (isSummaryMessage(safeMessages.get(index), summaryMarker)) {
                if (startIdx == null) {
                    startIdx = index;
                }
                previousIdx = index;
                continue;
            }
            if (startIdx != null && previousIdx != null) {
                if (previousIdx - startIdx + 1 >= minBlocks) {
                    ranges.add(new SummaryMergeRange(startIdx, previousIdx));
                }
                startIdx = null;
                previousIdx = null;
            }
        }
        if (startIdx != null && previousIdx != null && previousIdx - startIdx + 1 >= minBlocks) {
            ranges.add(new SummaryMergeRange(startIdx, previousIdx));
        }
        return ranges;
    }

    /**
     * Contiguous summary-message range using Python's inclusive indexes from util.py.
     *
     * <p>Mirrors Python's {@code Tuple[int, int]} in
     * {@code openjiuwen/core/context_engine/processor/compressor/util.py}.</p>
     */
    public record SummaryMergeRange(int startIndex, int endIndex) {
    }

    private static String findJsonString(String text, String key) {
        Matcher matcher = Pattern.compile(String.format(JSON_STRING_FIELD.pattern(), Pattern.quote(key))).matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String findJsonInt(String text, String key) {
        Matcher matcher = Pattern.compile(String.format(JSON_INT_FIELD.pattern(), Pattern.quote(key))).matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}
