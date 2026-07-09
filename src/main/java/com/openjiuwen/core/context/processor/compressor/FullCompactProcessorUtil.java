/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers ported from Python's {@code processor/compressor/util.py}.
 */
final class FullCompactProcessorUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern CONTENT_PATTERN =
        Pattern.compile("\"content\"\\s*:\\s*\"(?<content>(?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern READ_FILE_PATH_PATTERN = Pattern.compile("\"file_path\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern READ_FILE_LINE_COUNT_PATTERN = Pattern.compile("\"line_count\"\\s*:\\s*(\\d+)");

    /**
     * FullCompactProcessorUtil.
     * 
     * @since 0.1.7
     */
    private FullCompactProcessorUtil() {
    }

    record ReinjectedStateBuilderSpec(String name, String label, StateBuilder builder) {
    }

    @FunctionalInterface
    interface StateBuilder {
        /**
         * build.
         * 
         * @param processor processor
         * @param context context
         * @param messages messages
         * @param messagesToKeep messagesToKeep
         * @return the result
         * @since 0.1.7
         */
        Object build(FullCompactProcessor processor, ModelContext context, List<BaseMessage> messages,
                List<BaseMessage> messagesToKeep);
    }

    static final class FullCompactStateReinjector {
        private final List<ReinjectedStateBuilderSpec> builders = new ArrayList<>();

        void registerBuilder(String name, String label, StateBuilder builder) {
            ReinjectedStateBuilderSpec spec = new ReinjectedStateBuilderSpec(name, label, builder);
            for (int index = 0; index < builders.size(); index++) {
                if (builders.get(index).name().equals(name)) {
                    builders.set(index, spec);
                    return;
                }
            }
            builders.add(spec);
        }

        List<ReinjectedStateBuilderSpec> iterBuilders() {
            return List.copyOf(builders);
        }
    }

    static String buildPlanReinjectedContent(FullCompactProcessor processor, ModelContext context,
            List<BaseMessage> messages, List<BaseMessage> messagesToKeep) {
        return "";
    }

    static List<UserMessage> buildSkillReinjectedContent(FullCompactProcessor processor, ModelContext context,
            List<BaseMessage> messages, List<BaseMessage> messagesToKeep) {
        Set<String> keepSignatures = new LinkedHashSet<>();
        for (BaseMessage message : messagesToKeep) {
            keepSignatures.add(messageSignature(message));
        }

        List<List<BaseMessage>> selectedRounds = new ArrayList<>();
        Set<List<String>> seenRoundSignatures = new LinkedHashSet<>();
        List<List<BaseMessage>> rounds = groupCompletedApiRounds(messages);
        for (int index = rounds.size() - 1; index >= 0; index--) {
            List<BaseMessage> roundMessages = rounds.get(index);
            List<String> roundSignatures =
                roundMessages.stream().map(FullCompactProcessorUtil::messageSignature).toList();
            if (seenRoundSignatures.contains(roundSignatures)) {
                continue;
            }
            if (roundSignatures.stream().anyMatch(keepSignatures::contains)) {
                continue;
            }
            if (!roundContainsSkillRead(roundMessages)) {
                continue;
            }
            selectedRounds.add(copyMessages(roundMessages));
            seenRoundSignatures.add(roundSignatures);
            if (selectedRounds.size() >= processor.getAdvancedConfig().getReinjectRecentSkills()) {
                break;
            }
        }

        List<UserMessage> reinjectedMessages = new ArrayList<>();
        for (int index = selectedRounds.size() - 1; index >= 0; index--) {
            List<BaseMessage> roundMessages = selectedRounds.get(index);
            String serializedRound = String.join("\n", roundMessages.stream()
                    .map(message -> "role=" + message.getRole() + ", content=" + messageToText(message)).toList());
            reinjectedMessages.add(new UserMessage(
                    processor.getStateMarker() + "\n[SKILLS]\n" + processor.truncateStateText(serializedRound)));
        }
        return reinjectedMessages;
    }

    static String buildFileReinjectedContent(FullCompactProcessor processor, ModelContext context,
            List<BaseMessage> messages, List<BaseMessage> messagesToKeep) {
        return "";
    }

    static String buildTaskStatusReinjectedContent(FullCompactProcessor processor, ModelContext context,
            List<BaseMessage> messages, List<BaseMessage> messagesToKeep) {
        Map<String, Object> sessionState = getSessionState(context);
        Object taskStateRaw = sessionState.get("task_state");
        Map<?, ?> taskState = taskStateRaw instanceof Map<?, ?> map ? map : Map.of();

        int iteration = asInt(taskState.get("iteration"));
        List<?> pendingFollowUps = taskState.get("pending_follow_ups") instanceof List<?> list ? list : List.of();
        Object stopStateRaw = taskState.get("stop_condition_state");
        Map<?, ?> stopState = stopStateRaw instanceof Map<?, ?> map ? map : Map.of();
        Object stopReason = stopState.get("stop_reason");

        if (iteration == 0 && pendingFollowUps.isEmpty() && stopReason == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Current task-loop status for this session:");
        lines.add("- Completed outer-loop rounds: " + iteration + ".");
        lines.add("- Pending follow-up queries: " + pendingFollowUps.size() + ".");
        if (stopReason != null) {
            lines.add("- Last recorded stop reason: " + stopReason + ".");
        }
        return processor.truncateStateText(String.join("\n", lines));
    }

    static String buildPlanModeReinjectedContent(FullCompactProcessor processor, ModelContext context,
            List<BaseMessage> messages, List<BaseMessage> messagesToKeep) {
        Map<String, Object> sessionState = getSessionState(context);
        Object planModeRaw = sessionState.get("plan_mode");
        if (!(planModeRaw instanceof Map<?, ?> planMode)) {
            return "";
        }

        Object mode = planMode.containsKey("mode") ? planMode.get("mode") : "auto";
        Object prePlanMode = planMode.containsKey("pre_plan_mode") ? planMode.get("pre_plan_mode") : "";
        Object planSlug = planMode.containsKey("plan_slug") ? planMode.get("plan_slug") : "";
        List<String> lines = new ArrayList<>();
        lines.add("Current plan-mode status for this session:");
        lines.add("- Active mode: " + mode + ".");
        if (prePlanMode != null && !String.valueOf(prePlanMode).isBlank()) {
            lines.add("- Previous mode before entering plan mode: " + prePlanMode + ".");
        }
        if (planSlug != null && !String.valueOf(planSlug).isBlank()) {
            lines.add("- Active plan identifier: " + planSlug + ".");
        }
        return processor.truncateStateText(String.join("\n", lines));
    }

    static boolean isSkillFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String normalized = filePath.replace("\\", "/").toLowerCase(Locale.ROOT);
        return normalized.endsWith("/skill.md") || normalized.endsWith("skill.md");
    }

    static String extractSkillNameFromPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        String normalized = filePath.replace("\\", "/").replaceAll("/+$", "");
        String[] parts = normalized.split("/");
        if (parts.length >= 2 && "skill.md".equalsIgnoreCase(parts[parts.length - 1])) {
            return parts[parts.length - 2];
        }
        return "";
    }

    static boolean roundContainsSkillRead(List<BaseMessage> messages) {
        for (BaseMessage message : messages) {
            if (!(message instanceof AssistantMessage assistantMessage)) {
                continue;
            }
            List<ToolCall> toolCalls =
                assistantMessage.getToolCalls() != null ? assistantMessage.getToolCalls() : List.of();
            for (ToolCall toolCall : toolCalls) {
                String toolName = toolCall.getName() != null ? toolCall.getName() : "";
                if (!"read_file".equals(toolName)) {
                    continue;
                }
                String argumentsText = toolCall.getArguments() != null ? toolCall.getArguments() : "";
                Map<String, Object> parsedArguments = parseToolArguments(argumentsText);
                String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
                if (isSkillFilePath(filePath)) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<List<BaseMessage>> groupCompletedApiRounds(List<BaseMessage> messages) {
        List<List<BaseMessage>> result = new ArrayList<>();
        for (int[] range : SessionMemoryManager.groupCompletedApiRounds(messages)) {
            result.add(new ArrayList<>(messages.subList(range[0], range[1])));
        }
        return result;
    }

    static String messageSignature(BaseMessage message) {
        List<String> toolCallIds = new ArrayList<>();
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null) {
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                toolCallIds.add(toolCall.getId() != null ? toolCall.getId() : "");
            }
        }
        return message.getRole() + "|" + messageToText(message) + "|" + String.join("|", toolCallIds);
    }

    static String extractSkillFileContent(FullCompactProcessor processor, String resultText) {
        if (resultText == null || resultText.isBlank()) {
            return "";
        }
        Matcher contentMatcher = CONTENT_PATTERN.matcher(resultText);
        String content;
        if (contentMatcher.find()) {
            String rawContent = contentMatcher.group("content");
            try {
                content = MAPPER.readValue("\"" + rawContent + "\"", String.class);
            } catch (JsonProcessingException ignored) {
                content = rawContent.replace("\\\"", "\"").replace("\\n", "\n");
            }
        } else {
            content = resultText;
        }
        content = content.strip();
        if (content.isEmpty()) {
            return "";
        }
        return processor.truncateStateText(content);
    }

    static String describeToolCall(String toolName, String argumentsText) {
        Map<String, Object> parsedArguments = parseToolArguments(argumentsText);
        if ("read_file".equals(toolName)) {
            String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
            return "read_file path=" + (!filePath.isBlank() ? filePath : "[unknown]");
        }
        if ("write_file".equals(toolName)) {
            String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
            return "write_file path=" + (!filePath.isBlank() ? filePath : "[unknown]");
        }
        if ("edit_file".equals(toolName)) {
            String filePath = extractArgumentValue(parsedArguments, argumentsText, List.of("file_path"));
            return "edit_file path=" + (!filePath.isBlank() ? filePath : "[unknown]");
        }
        if ("glob".equals(toolName)) {
            String pattern = extractArgumentValue(parsedArguments, argumentsText, List.of("pattern"));
            String path = extractArgumentValue(parsedArguments, argumentsText, List.of("path"));
            return "glob pattern=" + (!pattern.isBlank() ? pattern : "[unknown]") + " path="
                    + (!path.isBlank() ? path : ".");
        }
        if ("grep".equals(toolName)) {
            String pattern = extractArgumentValue(parsedArguments, argumentsText, List.of("pattern"));
            String path = extractArgumentValue(parsedArguments, argumentsText, List.of("path", "file_path"));
            return "grep pattern=" + (!pattern.isBlank() ? pattern : "[unknown]") + " path="
                    + (!path.isBlank() ? path : "[unknown]");
        }
        return toolName + " args=" + argumentsText;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseToolArguments(String argumentsText) {
        if (argumentsText == null || argumentsText.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = MAPPER.readValue(argumentsText, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (JsonProcessingException ignored) {
            // Malformed tool arguments are treated as absent arguments.
        }
        return Map.of();
    }

    static String extractArgumentValue(Map<String, Object> parsedArguments, String argumentsText, List<String> keys) {
        for (String key : keys) {
            Object value = parsedArguments.get(key);
            if (value instanceof String text && !text.strip().isEmpty()) {
                return text.strip();
            }
        }
        String source = argumentsText != null ? argumentsText : "";
        for (String key : keys) {
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(source);
            if (matcher.find()) {
                return matcher.group(1).strip();
            }
        }
        return "";
    }

    static String findToolResultText(List<BaseMessage> messages, String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            BaseMessage message = messages.get(index);
            if (message instanceof ToolMessage toolMessage && toolCallId.equals(toolMessage.getToolCallId())) {
                return messageToText(message);
            }
        }
        return "";
    }

    static String extractToolResultHint(String toolName, String resultText, List<String> allowedToolNames) {
        if (resultText == null || resultText.isBlank()) {
            return "";
        }
        if (allowedToolNames == null || !allowedToolNames.contains(toolName)) {
            return "";
        }
        if ("read_file".equals(toolName)) {
            Matcher filePathMatcher = READ_FILE_PATH_PATTERN.matcher(resultText);
            Matcher lineCountMatcher = READ_FILE_LINE_COUNT_PATTERN.matcher(resultText);
            List<String> parts = new ArrayList<>();
            if (filePathMatcher.find()) {
                parts.add("result_path=" + filePathMatcher.group(1));
            }
            if (lineCountMatcher.find()) {
                parts.add("lines=" + lineCountMatcher.group(1));
            }
            return String.join(" ", parts);
        }
        return extractNumericHint(toolName, resultText);
    }

    static String messageToText(BaseMessage message) {
        Object content = message != null ? message.getContent() : "";
        if (content instanceof String text) {
            return text;
        }
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            return String.valueOf(content);
        }
    }

    static List<BaseMessage> copyMessages(List<BaseMessage> messages) {
        List<BaseMessage> copied = new ArrayList<>();
        for (BaseMessage message : messages) {
            copied.add(copyMessage(message));
        }
        return copied;
    }

    static BaseMessage copyMessage(BaseMessage message) {
        Map<String, Object> metadata =
            message.getMetadata() != null ? new LinkedHashMap<>(message.getMetadata()) : null;
        if (message instanceof AssistantMessage assistantMessage) {
            return AssistantMessage.builder().role(assistantMessage.getRole()).content(assistantMessage.getContent())
                    .name(assistantMessage.getName()).metadata(metadata)
                    .toolCalls(assistantMessage.getToolCalls() != null
                            ? new ArrayList<>(assistantMessage.getToolCalls())
                            : null)
                    .usageMetadata(assistantMessage.getUsageMetadata()).finishReason(assistantMessage.getFinishReason())
                    .parserContent(assistantMessage.getParserContent())
                    .reasoningContent(assistantMessage.getReasoningContent()).build();
        }
        if (message instanceof ToolMessage toolMessage) {
            return ToolMessage.builder().role(toolMessage.getRole()).content(toolMessage.getContent())
                    .name(toolMessage.getName()).metadata(metadata).toolCallId(toolMessage.getToolCallId()).build();
        }
        if (message instanceof UserMessage) {
            return UserMessage.builder().role(message.getRole()).content(message.getContent()).name(message.getName())
                    .metadata(metadata).build();
        }
        return BaseMessage.builder().role(message.getRole()).content(message.getContent()).name(message.getName())
                .metadata(metadata).build();
    }

    static Session getSessionRef(ModelContext context) {
        if (context instanceof SessionModelContext sessionModelContext) {
            return sessionModelContext.sessionRef();
        }
        return null;
    }

    /**
     * getSessionState.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> getSessionState(ModelContext context) {
        Session session = getSessionRef(context);
        if (session == null) {
            return Map.of();
        }
        Object state = session.getState("context");
        if (state instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    /**
     * asInt.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Non-numeric task state values use the Python-compatible zero default.
            }
        }
        return 0;
    }

    /**
     * extractNumericHint.
     * 
     * @param toolName toolName
     * @param resultText resultText
     * @return the result
     * @since 0.1.7
     */
    private static String extractNumericHint(String toolName, String resultText) {
        Map<String, String> keyByTool =
            Map.of("glob", "count", "grep", "count", "edit_file", "replacements", "write_file", "bytes_written");
        Map<String, String> labelByTool =
            Map.of("glob", "matches", "grep", "hits", "edit_file", "replacements", "write_file", "bytes_written");
        String key = keyByTool.get(toolName);
        if (key == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(resultText);
        if (matcher.find()) {
            return labelByTool.get(toolName) + "=" + matcher.group(1);
        }
        return "";
    }
}
