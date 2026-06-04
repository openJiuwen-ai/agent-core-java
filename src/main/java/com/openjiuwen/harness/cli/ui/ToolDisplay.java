/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import java.util.HashMap;
import java.util.Map;

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
            case "read_file" -> {
                String path = stringValue(args.get("file_path"));
                if (path.isBlank()) {
                    yield "";
                }
                String shortPath = shortPath(path);
                if (args.containsKey("limit")) {
                    yield shortPath + ", limit=" + args.get("limit");
                }
                yield shortPath;
            }
            case "write_file", "edit_file" -> {
                String path = stringValue(args.get("file_path"));
                yield path.isBlank() ? "" : shortPath(path);
            }
            case "grep" -> {
                String pattern = stringValue(args.get("pattern"));
                String path = stringValue(args.get("path"));
                yield ("\"" + pattern + "\" " + path).strip();
            }
            case "glob" -> {
                yield stringValue(args.get("pattern"));
            }
            case "bash" -> {
                String cmd = stringValue(args.get("command"));
                if (cmd.length() > 60) {
                    yield cmd.substring(0, 57) + "...";
                }
                yield cmd;
            }
            case "ls", "list_dir" -> stringValue(args.getOrDefault("path", "."));
            case "todo_create", "todo_modify", "todo_list" -> "";
            case "web_search", "web_free_search" -> stringValue(args.get("query"));
            case "web_fetch", "web_fetch_webpage" -> stringValue(args.get("url"));
            default -> {
                if (args.isEmpty()) {
                    yield "";
                }
                String firstValue = stringValue(args.values().iterator().next());
                if (firstValue.length() > 60) {
                    yield firstValue.substring(0, 57) + "...";
                }
                yield firstValue;
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
        if (result == null || (result instanceof String text && text.isEmpty())) {
            return "Done";
        }
        return formatToolResult(toolName, result, null, null);
    }

    /**
     * Format tool result with optional args and metadata.
     *
     * @param toolName Internal tool name.
     * @param result   Raw tool result.
     * @param toolArgs Raw tool arguments.
     * @param toolMeta Optional metadata such as structured line counts.
     * @return Formatted result summary string.
     */
    public static String formatToolResult(String toolName, Object result, Object toolArgs, Map<String, Object> toolMeta) {
        if (result == null || (result instanceof String text && text.isEmpty())) {
            return "Done";
        }

        return switch (toolName) {
            case "read_file" -> {
                if (result instanceof String) {
                    String content = (String) result;
                    yield "Read " + extractToolResultLineCount(content, toolMeta) + " lines";
                }
                yield "Read";
            }
            case "write_file", "edit_file" -> {
                if ("write_file".equals(toolName) && result instanceof String text) {
                    Map<String, Object> args = parseArgs(toolArgs);
                    String path = shortPath(stringValue(args.get("file_path")));
                    long lines = text.chars().filter(ch -> ch == '\n').count();
                    if (lines > 0) {
                        yield "Wrote " + lines + " lines to " + path;
                    }
                    yield "Wrote to " + path;
                }
                if (result instanceof String text) {
                    String firstLine = firstLine(text);
                    if (!firstLine.isBlank() && firstLine.length() <= 80) {
                        yield firstLine;
                    }
                }
                yield "Edited file";
            }
            case "bash" -> {
                if (result instanceof String) {
                    String output = ((String) result).strip();
                    String[] lines = output.split("\\R", -1);
                    if (lines.length == 1 && lines[0].length() <= 80) {
                        yield lines[0];
                    }
                    String first = lines.length > 0 ? lines[0] : "";
                    if (first.length() > 60) {
                        first = first.substring(0, 60);
                    }
                    yield first + "... (+" + (lines.length - 1) + " lines)";
                }
                yield "Done";
            }
            case "grep" -> {
                if (result instanceof String) {
                    long count = countNonBlankLines((String) result);
                    if (count == 0) {
                        yield "No matches found";
                    }
                    yield "Found " + count + " matches";
                }
                if (result instanceof java.util.List) {
                    yield "Found " + ((java.util.List<?>) result).size() + " matches";
                }
                yield "Done";
            }
            case "glob" -> {
                if (result instanceof String) {
                    long count = countNonBlankLines((String) result);
                    if (count == 0) {
                        yield "No files found";
                    }
                    yield "Found " + count + " files";
                }
                if (result instanceof java.util.List) {
                    yield "Found " + ((java.util.List<?>) result).size() + " files";
                }
                yield "Done";
            }
            case "ls", "list_dir" -> {
                if (result instanceof String) {
                    yield "Listed " + countNonBlankLines((String) result) + " items";
                }
                yield "Done";
            }
            case "todo_create", "todo_modify", "todo_list" -> "";
            default -> {
                if (result instanceof String text) {
                    String firstLine = firstLine(text);
                    if (firstLine.length() > 80) {
                        yield firstLine.substring(0, 77) + "...";
                    }
                    yield firstLine;
                }
                yield "Done";
            }
        };
    }

    /**
     * Format write/edit result as a numbered content preview.
     */
    public static String formatWritePreview(String toolResult) {
        String[] lines = toolResult != null ? toolResult.split("\n", -1) : new String[] {""};
        StringBuilder preview = new StringBuilder();
        int visibleLines = Math.min(lines.length, 5);
        for (int i = 0; i < visibleLines; i++) {
            String line = lines[i];
            if (line.length() > 80) {
                line = line.substring(0, 77) + "...";
            }
            if (preview.length() > 0) {
                preview.append("\n");
            }
            preview.append("     ").append(i + 1).append(" ").append(line);
        }
        if (lines.length > 5) {
            if (preview.length() > 0) {
                preview.append("\n");
            }
            preview.append("     \u2026+").append(lines.length - 5).append(" lines");
        }
        return preview.toString();
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

    private static String shortPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String cwd = System.getProperty("user.dir", "");
        if (!cwd.isBlank() && path.startsWith(cwd)) {
            try {
                return java.nio.file.Path.of(cwd).relativize(java.nio.file.Path.of(path)).toString();
            } catch (RuntimeException ignored) {
                return path;
            }
        }
        return path;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    private static long extractToolResultLineCount(String toolResult, Map<String, Object> toolMeta) {
        if (toolMeta != null && toolMeta.get("line_count") != null) {
            try {
                return Long.parseLong(String.valueOf(toolMeta.get("line_count")));
            } catch (NumberFormatException ignored) {
                // Fall through to rendered text counting.
            }
        }
        return toolResult.lines().count();
    }

    private static long countNonBlankLines(String text) {
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            return 0;
        }
        return stripped.lines().filter(line -> !line.isBlank()).count();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(0, newline) : text;
    }
}
