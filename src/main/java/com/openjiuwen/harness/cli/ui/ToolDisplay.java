/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tool display formatting: name mapping, args, and result summary.
 * <p>
 * Mirrors Python's module in
 * {@code openjiuwen/harness/cli/ui/tool_display.py}.
 */
public final class ToolDisplay {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("read_file", "Read"),
            Map.entry("write_file", "Write"),
            Map.entry("edit_file", "Edit"),
            Map.entry("bash", "Bash"),
            Map.entry("grep", "Grep"),
            Map.entry("glob", "Glob"),
            Map.entry("ls", "LS"),
            Map.entry("list_dir", "LS"),
            Map.entry("todo_create", "TodoWrite"),
            Map.entry("todo_modify", "TodoWrite"),
            Map.entry("todo_list", "TodoList"),
            Map.entry("web_search", "WebSearch"),
            Map.entry("web_free_search", "WebSearch"),
            Map.entry("web_fetch", "WebFetch"),
            Map.entry("web_fetch_webpage", "WebFetch"),
            Map.entry("image_ocr", "ImageOCR"),
            Map.entry("visual_question_answering", "VisionQA"),
            Map.entry("audio_transcription", "AudioTranscribe"),
            Map.entry("audio_question_answering", "AudioQA"),
            Map.entry("audio_metadata", "AudioMetadata")
    );

    public static final Set<String> TODO_TOOLS = Set.of("todo_create", "todo_modify", "todo_list");

    private ToolDisplay() {
    }

    public static String getDisplayName(String toolName) {
        String displayName = TOOL_DISPLAY_NAMES.get(toolName);
        if (displayName != null) {
            return displayName;
        }
        StringBuilder builder = new StringBuilder();
        for (String word : toolName.replace("_", " ").split(" ")) {
            if (!word.isEmpty()) {
                builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return builder.toString().trim();
    }

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
            case "glob" -> stringValue(args.get("pattern"));
            case "bash" -> {
                String command = stringValue(args.get("command"));
                yield command.length() > 60 ? command.substring(0, 57) + "..." : command;
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
                yield firstValue.length() > 60 ? firstValue.substring(0, 57) + "..." : firstValue;
            }
        };
    }

    public static String formatToolResult(String toolName, Object result) {
        return formatToolResult(toolName, result, null, null);
    }

    public static String formatToolResult(String toolName, Object result, Object toolArgs, Map<String, Object> toolMeta) {
        if (result == null || result instanceof String text && text.isEmpty()) {
            return "Done";
        }
        return switch (toolName) {
            case "read_file" -> "Read " + extractToolResultLineCount(String.valueOf(result), toolMeta) + " lines";
            case "write_file", "edit_file" -> formatWriteOrEditResult(toolName, result, toolArgs);
            case "bash" -> formatBashResult(String.valueOf(result));
            case "grep" -> formatCountedResult(String.valueOf(result), "matches", "No matches found");
            case "glob" -> formatCountedResult(String.valueOf(result), "files", "No files found");
            case "ls", "list_dir" -> "Listed " + countNonBlankLines(String.valueOf(result)) + " items";
            case "todo_create", "todo_modify", "todo_list" -> "";
            default -> {
                String firstLine = firstLine(String.valueOf(result));
                yield firstLine.length() > 80 ? firstLine.substring(0, 77) + "..." : firstLine;
            }
        };
    }

    public static String formatWritePreview(String toolResult) {
        String[] lines = toolResult != null ? toolResult.split("\n", -1) : new String[]{""};
        StringBuilder preview = new StringBuilder();
        int visibleLines = Math.min(lines.length, 5);
        for (int index = 0; index < visibleLines; index++) {
            String line = lines[index];
            if (line.length() > 80) {
                line = line.substring(0, 77) + "...";
            }
            if (preview.length() > 0) {
                preview.append('\n');
            }
            preview.append("     ").append(index + 1).append(' ').append(line);
        }
        if (lines.length > 5) {
            if (preview.length() > 0) {
                preview.append('\n');
            }
            preview.append("     \u2026 +").append(lines.length - 5).append(" lines");
        }
        return preview.toString();
    }

    public static String formatToolHeader(String toolName, Object toolArgs) {
        String displayName = getDisplayName(toolName);
        String argsDisplay = formatToolArgs(toolName, toolArgs);
        return argsDisplay.isEmpty() ? "\u25fc" + displayName : "\u25fc" + displayName + "(" + argsDisplay + ")";
    }

    public static String formatResultLine(String toolName, Object result) {
        return "\u23bf " + getDisplayName(toolName) + " " + formatToolResult(toolName, result);
    }

    private static String formatWriteOrEditResult(String toolName, Object result, Object toolArgs) {
        if ("write_file".equals(toolName)) {
            Map<String, Object> args = parseArgs(toolArgs);
            String path = shortPath(stringValue(args.get("file_path")));
            long lines = String.valueOf(result).chars().filter(ch -> ch == '\n').count();
            return lines > 0 ? "Wrote " + lines + " lines to " + path : "Wrote to " + path;
        }
        String firstLine = firstLine(String.valueOf(result));
        return !firstLine.isBlank() && firstLine.length() <= 80 ? firstLine : "Edited file";
    }

    private static String formatBashResult(String output) {
        String[] lines = output.strip().split("\\R", -1);
        if (lines.length == 1 && lines[0].length() <= 80) {
            return lines[0];
        }
        String first = lines.length > 0 ? lines[0] : "";
        if (first.length() > 60) {
            first = first.substring(0, 60);
        }
        return first + "... (+" + Math.max(0, lines.length - 1) + " lines)";
    }

    private static String formatCountedResult(String output, String label, String emptyMessage) {
        long count = countNonBlankLines(output);
        return count == 0 ? emptyMessage : "Found " + count + " " + label;
    }

    private static Map<String, Object> parseArgs(Object toolArgs) {
        if (toolArgs == null) {
            return new LinkedHashMap<>();
        }
        if (toolArgs instanceof Map<?, ?> rawMap) {
            Map<String, Object> parsed = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    parsed.put(String.valueOf(key), value);
                }
            });
            return parsed;
        }
        if (toolArgs instanceof String text) {
            try {
                Object parsed = OBJECT_MAPPER.readValue(text, new TypeReference<Object>() {
                });
                if (parsed instanceof Map<?, ?> rawMap) {
                    return parseArgs(rawMap);
                }
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private static String shortPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String cwd = System.getProperty("user.dir", "");
        if (!cwd.isBlank() && path.startsWith(cwd)) {
            try {
                return Path.of(cwd).relativize(Path.of(path)).toString();
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
                // Fall through.
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
