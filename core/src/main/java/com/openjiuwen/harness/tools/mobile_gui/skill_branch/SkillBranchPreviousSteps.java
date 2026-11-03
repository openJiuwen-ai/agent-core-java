/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's history-formatting helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/previous_steps.py}.
 */
public final class SkillBranchPreviousSteps {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SkillBranchPreviousSteps() {
    }

    public static String formatPreviousStepsForBranch(
            List<?> messages,
            String skipToolCallId,
            int lastNTurns
    ) {
        if (messages == null || messages.isEmpty()) {
            return "(no previous steps)";
        }

        List<List<String>> turns = new ArrayList<>();
        List<String> currentTurn = new ArrayList<>();

        for (Object msg : messages) {
            String role = stringValue(readField(msg, "role"));
            if ("assistant".equals(role)) {
                if (!currentTurn.isEmpty()) {
                    turns.add(currentTurn);
                }
                List<String> assistantLines = formatAssistantTurnLines(msg);
                currentTurn = assistantLines.isEmpty() ? new ArrayList<>() : new ArrayList<>(assistantLines);
            } else if ("tool".equals(role)) {
                String toolCallId = stringValue(readField(msg, "tool_call_id"));
                if (!stringValue(skipToolCallId).isEmpty() && stringValue(skipToolCallId).equals(toolCallId)) {
                    continue;
                }
                currentTurn.add(formatToolLine(msg));
            }
        }

        if (!currentTurn.isEmpty()) {
            turns.add(currentTurn);
        }

        List<String> prefixLines = new ArrayList<>();
        if (lastNTurns > 0 && turns.size() > lastNTurns) {
            int omitted = turns.size() - lastNTurns;
            turns = new ArrayList<>(turns.subList(turns.size() - lastNTurns, turns.size()));
            prefixLines.add("... (" + omitted + " earlier assistant turn(s) omitted)");
        }

        List<String> stepLines = new ArrayList<>(prefixLines);
        int stepNum = 1;
        for (List<String> turnLines : turns) {
            if (turnLines.isEmpty()) {
                continue;
            }
            stepLines.add("--- Step " + stepNum + " (assistant) ---");
            stepLines.addAll(turnLines);
            stepNum += 1;
        }

        if (stepLines.isEmpty()) {
            return "(no previous steps)";
        }
        return String.join("\n", stepLines);
    }

    private static String contentToText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (!(content instanceof List<?> list)) {
            return String.valueOf(content);
        }

        List<String> parts = new ArrayList<>();
        for (Object block : list) {
            if (block instanceof String text) {
                parts.add(text);
                continue;
            }
            if (!(block instanceof Map<?, ?>) && block == null) {
                continue;
            }
            String type = stringValue(readField(block, "type"));
            if ("image_url".equals(type)) {
                continue;
            }
            if ("text".equals(type)) {
                Object text = readField(block, "text");
                if (text instanceof String value && !value.strip().isEmpty()) {
                    parts.add(value);
                }
            }
        }
        return String.join("\n", parts);
    }

    private static String formatToolCalls(Object msg) {
        Object toolCalls = readField(msg, "tool_calls");
        if (!(toolCalls instanceof List<?> list) || list.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        for (Object toolCall : list) {
            String name = stringValue(readField(toolCall, "name"));
            Object arguments = readField(toolCall, "arguments");
            if (name.isEmpty()) {
                continue;
            }
            String argText;
            if (arguments instanceof Map<?, ?>) {
                try {
                    argText = OBJECT_MAPPER.writeValueAsString(arguments);
                } catch (JsonProcessingException ex) {
                    argText = String.valueOf(arguments);
                }
            } else {
                argText = stringValue(arguments);
            }
            lines.add("Tool call: " + name + "(" + argText + ")");
        }
        return String.join("\n", lines);
    }

    private static List<String> formatAssistantTurnLines(Object msg) {
        List<String> lines = new ArrayList<>();
        String text = contentToText(readField(msg, "content")).trim();
        if (!text.isEmpty()) {
            lines.add(text);
        }
        String toolLines = formatToolCalls(msg);
        if (!toolLines.isEmpty()) {
            lines.add(toolLines);
        }
        return lines;
    }

    private static String formatToolLine(Object msg) {
        String toolName = stringValue(readField(msg, "name"));
        if (toolName.isEmpty()) {
            toolName = "tool";
        }
        String body = contentToText(readField(msg, "content")).trim();
        if (body.isEmpty()) {
            body = "(empty tool result)";
        }
        return "Tool result (" + toolName + "): " + body;
    }

    private static Object readField(Object obj, String name) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map<?, ?> map) {
            return map.get(name);
        }

        String camel = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        List<String> methodNames = List.of(name, "get" + camel, "is" + camel);
        for (String methodName : methodNames) {
            try {
                Method method = obj.getClass().getMethod(methodName);
                return method.invoke(obj);
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor.
            }
        }

        try {
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(obj);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
