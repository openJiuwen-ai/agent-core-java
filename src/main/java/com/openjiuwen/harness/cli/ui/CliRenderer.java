/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI output renderer.
 * <p>
 * Mirrors Python's {@code renderer} in
 * {@code openjiuwen/harness/cli/ui/renderer.py}.
 */
public class CliRenderer {

    public static final String CHUNK_LLM_OUTPUT = "llm_output";
    public static final String CHUNK_LLM_REASONING = "llm_reasoning";
    public static final String CHUNK_ANSWER = "answer";
    public static final String CHUNK_INTERACTION = "__interaction__";
    public static final String CHUNK_MESSAGE = "message";
    public static final String CHUNK_TOOL_CALL = "tool_call";
    public static final String CHUNK_TOOL_RESULT = "tool_result";
    public static final String CHUNK_TODO_UPDATED = "todo.updated";
    public static final String CHUNK_CONTROLLER_OUTPUT = "controller_output";

    private static final Pattern CONTROLLER_TEXT_PATTERN = Pattern.compile("text=\"(.*?)\"");

    public RenderResult renderStream(Iterator<?> stream, PrintStream terminal, PrintStream console) {
        return renderStream(stream, terminal, console, null, false);
    }

    public RenderResult renderStream(
            Iterator<?> stream,
            PrintStream terminal,
            PrintStream console,
            InteractionHandler onInteraction,
            boolean showReasoning) {
        List<String> resultParts = new ArrayList<>();
        List<PendingInteraction> pendingInteractions = new ArrayList<>();
        List<Map<String, Object>> todoItems = null;
        List<String> seenTypes = new ArrayList<>();
        int chunkCount = 0;
        boolean hasLlmOutput = false;
        boolean inLlmOutput = false;
        boolean visibleChunkSeen = false;

        while (stream.hasNext()) {
            Object chunk = stream.next();
            chunkCount += 1;
            String type = stringValue(readMember(chunk, "type"));
            Object payload = readMember(chunk, "payload");
            if (!type.isBlank() && !seenTypes.contains(type)) {
                seenTypes.add(type);
            }

            if (!CHUNK_LLM_OUTPUT.equals(type) && inLlmOutput) {
                terminal.print(System.lineSeparator());
                inLlmOutput = false;
            }

            switch (type) {
                case CHUNK_LLM_OUTPUT -> {
                    String text = extractContent(payload);
                    if (!text.isBlank()) {
                        if (!inLlmOutput) {
                            terminal.print("\033[92m■\033[0m");
                            inLlmOutput = true;
                        }
                        hasLlmOutput = true;
                        resultParts.add(text);
                        terminal.print(text);
                        visibleChunkSeen = true;
                    }
                }
                case CHUNK_ANSWER -> {
                    if (!hasLlmOutput) {
                        String text = extractContent(payload);
                        if (!text.isBlank()) {
                            resultParts.add(text);
                            terminal.print(text);
                            visibleChunkSeen = true;
                        }
                    }
                }
                case CHUNK_LLM_REASONING -> {
                    if (showReasoning) {
                        String text = extractContent(payload);
                        if (!text.isBlank()) {
                            console.print("[dim]" + text + "[/dim]");
                        }
                    }
                }
                case CHUNK_MESSAGE -> {
                    String text = extractContent(payload);
                    if (!text.isBlank()) {
                        console.println("[dim]  ⚙ " + text + "[/dim]");
                        visibleChunkSeen = true;
                    }
                }
                case CHUNK_TOOL_CALL -> {
                    renderToolCall(mapPayload(payload), console);
                    visibleChunkSeen = true;
                }
                case CHUNK_TOOL_RESULT -> {
                    todoItems = renderToolResult(mapPayload(payload), console, todoItems);
                    visibleChunkSeen = true;
                }
                case CHUNK_TODO_UPDATED -> {
                    List<Map<String, Object>> parsed = TodoRender.parseTodoResult(
                            String.valueOf(mapPayload(payload).getOrDefault("items", "[]"))
                    );
                    if (parsed != null) {
                        todoItems = parsed;
                        for (String line : TodoRender.renderTodoList(parsed)) {
                            console.println(line);
                        }
                        visibleChunkSeen = true;
                    }
                }
                case CHUNK_CONTROLLER_OUTPUT -> {
                    String errorText = extractControllerOutputError(payload);
                    if (!errorText.isBlank()) {
                        console.println("[red]✗ " + errorText + "[/red]");
                        visibleChunkSeen = true;
                    }
                }
                case CHUNK_INTERACTION -> {
                    String id = stringValue(readMember(payload, "id"));
                    if (id.isBlank()) {
                        id = "unknown";
                    }
                    Object value = readMember(payload, "value");
                    if (value == null) {
                        value = payload;
                    }
                    if (onInteraction != null) {
                        onInteraction.onInteraction(id, value);
                    }
                    pendingInteractions.add(new PendingInteraction(id, value));
                    visibleChunkSeen = true;
                }
                default -> {
                    // Keep Python behavior: ignore unknown chunks unless everything is invisible.
                }
            }
        }

        if (inLlmOutput) {
            terminal.print(System.lineSeparator());
        }

        if (chunkCount == 0) {
            console.println("[dim]⚠ No output received. Check your API configuration.[/dim]");
        } else if (!visibleChunkSeen && pendingInteractions.isEmpty()) {
            String chunkTypes = seenTypes.isEmpty() ? "unknown" : String.join(", ", seenTypes);
            console.println("[dim]⚠ No visible output received. Chunk types: " + chunkTypes + "[/dim]");
        }

        return new RenderResult(String.join("", resultParts), pendingInteractions);
    }

    private static void renderToolCall(Map<String, Object> payload, PrintStream console) {
        String toolName = stringValue(payload.get("tool_name"));
        Object toolArgs = payload.get("tool_args");
        String displayName = ToolDisplay.getDisplayName(toolName);
        String args = ToolDisplay.formatToolArgs(toolName, toolArgs);
        if (args.isBlank()) {
            console.println("[cyan]■ " + displayName + "[/cyan]");
        } else {
            console.println("[cyan]■ " + displayName + "[/cyan][dim](" + args + ")[/dim]");
        }
    }

    private static List<Map<String, Object>> renderToolResult(
            Map<String, Object> payload,
            PrintStream console,
            List<Map<String, Object>> todoItems) {
        String toolName = stringValue(payload.get("tool_name"));
        Object toolResult = payload.getOrDefault("tool_result", "");
        Object toolArgs = payload.getOrDefault("tool_args", "");

        if (ToolDisplay.TODO_TOOLS.contains(toolName)) {
            List<Map<String, Object>> parsed = TodoRender.parseTodoResult(String.valueOf(toolResult));
            if (parsed != null) {
                for (String line : TodoRender.renderTodoList(parsed)) {
                    console.println(line);
                }
                console.println();
                return parsed;
            }
            if ("todo_modify".equals(toolName) && todoItems != null) {
                List<Map<String, Object>> modified = TodoRender.applyTodoModifyArgs(todoItems, toolArgs);
                if (modified != null) {
                    for (String line : TodoRender.renderTodoList(modified)) {
                        console.println(line);
                    }
                    console.println();
                    return modified;
                }
            }
            String message = extractTodoMessage(String.valueOf(toolResult));
            if (!message.isBlank()) {
                console.println("[dim]  ⏿ " + message + "[/dim]");
                console.println();
                return todoItems;
            }
        }

        String summary = formatToolResult(toolName, toolResult, toolArgs, payload);
        if (!summary.isBlank()) {
            console.println("[dim]  ⏿ " + summary + "[/dim]");
        }
        console.println();
        return todoItems;
    }

    private static String formatToolResult(String toolName, Object toolResult, Object toolArgs, Map<String, Object> payload) {
        if ("read_file".equals(toolName) && payload.get("line_count") instanceof Number number) {
            return "Read " + number.intValue() + " lines";
        }
        return ToolDisplay.formatToolResult(toolName, toolResult, toolArgs, payload);
    }

    private static String extractContent(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null && !String.valueOf(content).isBlank()) {
                return String.valueOf(content);
            }
            Object output = map.get("output");
            return output == null ? "" : String.valueOf(output);
        }
        return payload == null ? "" : String.valueOf(payload);
    }

    private static String extractControllerOutputError(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            String payloadType = stringValue(map.get("type")).toLowerCase();
            if (!payloadType.contains("task_failed")) {
                return "";
            }
            Object data = map.get("data");
            if (data instanceof List<?> list) {
                List<String> texts = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Object text = itemMap.get("text");
                        if (text != null && !String.valueOf(text).isBlank()) {
                            texts.add(String.valueOf(text));
                        }
                    }
                }
                return String.join(System.lineSeparator(), texts);
            }
            return "";
        }
        String raw = String.valueOf(payload);
        if (!raw.toLowerCase().contains("task_failed")) {
            return "";
        }
        Matcher matcher = CONTROLLER_TEXT_PATTERN.matcher(raw);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches.isEmpty() ? raw : String.join(System.lineSeparator(), matches);
    }

    private static String extractTodoMessage(String toolResult) {
        Matcher matcher = Pattern.compile("['\"]message['\"]\\s*:\\s*['\"](.+?)['\"]}\\s*$", Pattern.DOTALL)
                .matcher(toolResult);
        if (!matcher.find()) {
            return "";
        }
        String message = matcher.group(1).replace("\\n", "\n");
        int firstLineEnd = message.indexOf('\n');
        return firstLineEnd >= 0 ? message.substring(0, firstLineEnd).trim() : message.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapPayload(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return Map.of();
    }

    private static Object readMember(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            // Fall through to getter lookup.
        }
        try {
            String methodName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record PendingInteraction(String interactionId, Object request) {
    }

    public record RenderResult(String text, List<PendingInteraction> pendingInteractions) {
    }

    @FunctionalInterface
    public interface InteractionHandler {
        void onInteraction(String interactionId, Object question);
    }
}
