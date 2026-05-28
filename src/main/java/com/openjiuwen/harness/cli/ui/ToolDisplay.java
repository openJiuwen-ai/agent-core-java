/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tool display formatting — name mapping, args, and result summary.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.ui.tool_display}.
 *
 * Provides Claude Code–style tool display:
 * - ● Read(src/main.py) — tool call header
 * - ⎿  Read 42 lines — result summary
 */
public final class ToolDisplay {

    /** Tool name mapping: internal SDK name → friendly display name */
    private static final Map<String, String> TOOL_DISPLAY_NAMES = new HashMap<>();

    /** Tools whose results should be rendered as todo checkboxes */
    public static final java.util.Set<String> TODO_TOOLS = java.util.Set.of(
        "todo_create", "todo_modify", "todo_list"
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PATH_PATTERN = Pattern.compile("[^/]+$");

    static {
        TOOL_DISPLAY_NAMES.put("read_file", "Read");
        TOOL_DISPLAY_NAMES.put("write_file", "Write");
        TOOL_DISPLAY_NAMES.put("edit_file", "Edit");
        TOOL_DISPLAY_NAMES.put("bash", "Bash");
        TOOL_DISPLAY_NAMES.put("grep", "Grep");
        TOOL_DISPLAY_NAMES.put("glob", "Glob");
        TOOL_DISPLAY_NAMES.put("ls", "LS");
        TOOL_DISPLAY_NAMES.put("list_dir", "LS");
        TOOL_DISPLAY_NAMES.put("todo_create", "TodoWrite");
        TOOL_DISPLAY_NAMES.put("todo_modify", "TodoWrite");
        TOOL_DISPLAY_NAMES.put("todo_list", "TodoList");
        TOOL_DISPLAY_NAMES.put("web_search", "WebSearch");
        TOOL_DISPLAY_NAMES.put("web_free_search", "WebSearch");
        TOOL_DISPLAY_NAMES.put("web_fetch", "WebFetch");
        TOOL_DISPLAY_NAMES.put("web_fetch_webpage", "WebFetch");
        TOOL_DISPLAY_NAMES.put("image_ocr", "ImageOCR");
        TOOL_DISPLAY_NAMES.put("visual_question_answering", "VisionQA");
        TOOL_DISPLAY_NAMES.put("audio_transcription", "AudioTranscribe");
        TOOL_DISPLAY_NAMES.put("audio_question_answering", "AudioQA");
        TOOL_DISPLAY_NAMES.put("audio_metadata", "AudioMetadata");
    }

    private ToolDisplay() {
    }

    /**
     * Map an internal tool name to a friendly display name.
     *
     * @param toolName Internal SDK tool name (e.g. "read_file").
     * @return Friendly display name (e.g. "Read").
     */
    public static String getDisplayName(String toolName) {
        String displayName = TOOL_DISPLAY_NAMES.get(toolName);
        if (displayName != null) {
            return displayName;
        }
        // Fallback: convert snake_case to Title Case
        StringBuilder sb = new StringBuilder();
        for (String word : toolName.replace("_", " ").split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Ensure tool_args is a dict.
     */
    private static Map<String, Object> parseArgs(Object toolArgs) {
        if (toolArgs == null) {
            return new HashMap<>();
        }
        if (toolArgs instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) toolArgs;
            return map;
        }
        if (toolArgs instanceof String) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(
                    (String) toolArgs,
                    new TypeReference<Map<String, Object>>() {}
                );
                return parsed;
            } catch (Exception e) {
                // Fall through
            }
        }
        return new HashMap<>();
    }

    /**
     * Format tool arguments for display.
     *
     * @param toolName Internal tool name.
     * @param toolArgs Raw tool arguments (str or dict).
     * @return Formatted argument string for display.
     */
    public static String formatToolArgs(String toolName, Object toolArgs) {
        Map<String, Object> args = parseArgs(toolArgs);

        return switch (toolName) {
            case "read_file", "write_file", "edit_file" -> {
                String path = (String) args.getOrDefault("file_path", args.getOrDefault("path", ""));
                String baseName = extractBaseName(path);
                if (args.containsKey("limit")) {
                    yield baseName + " limit=" + args.get("limit");
                }
                if (args.containsKey("offset")) {
                    yield baseName + " offset=" + args.get("offset");
                }
                yield baseName;
            }
            case "grep" -> {
                String pattern = (String) args.getOrDefault("pattern", args.getOrDefault("query", ""));
                String include = (String) args.get("include");
                if (include != null) {
                    yield pattern + " in " + include;
                }
                yield pattern;
            }
            case "glob" -> {
                String pattern = (String) args.getOrDefault("pattern", args.getOrDefault("glob", ""));
                yield pattern;
            }
            case "bash" -> {
                String cmd = (String) args.getOrDefault("command", args.getOrDefault("cmd", ""));
                if (cmd.length() > 50) {
                    yield cmd.substring(0, 50) + "...";
                }
                yield cmd;
            }
            case "todo_create", "todo_modify" -> {
                Object todos = args.get("todos");
                if (todos instanceof java.util.List) {
                    yield ((java.util.List<?>) todos).size() + " items";
                }
                yield "update";
            }
            default -> {
                if (args.containsKey("query")) {
                    String query = (String) args.get("query");
                    if (query.length() > 30) {
                        yield query.substring(0, 30) + "...";
                    }
                    yield query;
                }
                yield "";
            }
        };
    }

    /**
     * Format tool result summary.
     *
     * @param toolName Internal tool name.
     * @param result   Raw tool result.
     * @return Formatted result summary string.
     */
    public static String formatToolResult(String toolName, Object result) {
        if (result == null) {
            return "Done";
        }

        return switch (toolName) {
            case "read_file" -> {
                if (result instanceof String) {
                    String content = (String) result;
                    int lines = content.split("\n").length;
                    yield "Read " + lines + " lines";
                }
                yield "Read";
            }
            case "write_file", "edit_file" -> {
                yield "Written";
            }
            case "bash" -> {
                if (result instanceof String) {
                    String output = (String) result;
                    if (output.isEmpty()) {
                        yield "Done (no output)";
                    }
                    int lines = output.split("\n").length;
                    yield "Output: " + lines + " lines";
                }
                yield "Done";
            }
            case "grep" -> {
                if (result instanceof java.util.List) {
                    yield ((java.util.List<?>) result).size() + " matches";
                }
                if (result instanceof String) {
                    String content = (String) result;
                    int lines = content.split("\n").length;
                    yield lines + " matches";
                }
                yield "Done";
            }
            case "glob" -> {
                if (result instanceof java.util.List) {
                    yield ((java.util.List<?>) result).size() + " files";
                }
                yield "Done";
            }
            default -> "Done";
        };
    }

    /**
     * Format the full tool call header for display.
     *
     * @param toolName Internal tool name.
     * @param toolArgs Tool arguments.
     * @return Formatted header like "● Read(src/main.py)".
     */
    public static String formatToolHeader(String toolName, Object toolArgs) {
        String displayName = getDisplayName(toolName);
        String argsDisplay = formatToolArgs(toolName, toolArgs);
        if (argsDisplay.isEmpty()) {
            return "● " + displayName;
        }
        return "● " + displayName + "(" + argsDisplay + ")";
    }

    /**
     * Format the tool result line for display.
     *
     * @param toolName Internal tool name.
     * @param result   Tool result.
     * @return Formatted line like "⎿  Read 42 lines".
     */
    public static String formatResultLine(String toolName, Object result) {
        String displayName = getDisplayName(toolName);
        String resultSummary = formatToolResult(toolName, result);
        return "⎿  " + displayName + " " + resultSummary;
    }

    private static String extractBaseName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group();
        }
        return path;
    }
}