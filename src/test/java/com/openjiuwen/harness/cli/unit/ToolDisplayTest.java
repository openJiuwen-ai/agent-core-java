/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI tool display formatting.
 * <p>
 * Mirrors Python's {@code test_tool_display} in
 * {@code tests.cli.unit.test_tool_display}.
 */
class ToolDisplayTest {

    private String getDisplayName(String toolName) {
        return switch (toolName) {
            case "read_file" -> "Read";
            case "write_file" -> "Write";
            case "edit_file" -> "Edit";
            case "bash" -> "Bash";
            case "grep" -> "Grep";
            case "glob" -> "Glob";
            case "todo_create" -> "TodoWrite";
            case "web_free_search" -> "WebSearch";
            default -> {
                StringBuilder sb = new StringBuilder();
                for (String word : toolName.replace("_", " ").split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0)))
                                .append(word.substring(1)).append(" ");
                    }
                }
                yield sb.toString().trim();
            }
        };
    }

    private String formatToolArgs(String toolName, Object args) {
        if (args instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) args;
            return switch (toolName) {
                case "read_file" -> {
                    String path = (String) m.getOrDefault("file_path", "");
                    String base = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
                    if (m.containsKey("limit")) yield base + " limit=" + m.get("limit");
                    yield base;
                }
                case "bash" -> {
                    String cmd = (String) m.getOrDefault("command", "");
                    if (cmd.length() > 60) yield cmd.substring(0, 57) + "...";
                    yield cmd;
                }
                case "grep" -> "\"" + m.getOrDefault("pattern", "") + "\" " + m.getOrDefault("path", "");
                case "glob" -> (String) m.getOrDefault("pattern", "");
                case "todo_create" -> "";
                default -> m.toString();
            };
        }
        return args != null ? args.toString() : "";
    }

    private String formatToolResult(String toolName, String result) {
        if (result == null || result.isEmpty()) return "Done";
        return switch (toolName) {
            case "read_file" -> {
                long lines = result.lines().count();
                yield "Read " + lines + " lines";
            }
            case "bash" -> {
                long lines = result.lines().count();
                if (lines <= 1) yield result;
                yield result.lines().findFirst().orElse(result) + " +" + (lines - 1) + " lines";
            }
            case "grep" -> {
                long matches = result.lines().filter(l -> !l.isEmpty()).count();
                if (matches == 0) yield "Done";
                yield "Found " + matches + " matches";
            }
            case "glob" -> {
                long files = result.lines().filter(l -> !l.isEmpty()).count();
                if (files == 0) yield "Done";
                yield "Found " + files + " files";
            }
            default -> result;
        };
    }

    private String formatWritePreview(String content) {
        String[] lines = content.split("\n");
        if (lines.length <= 5) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                sb.append(i + 1).append(" ").append(lines[i]).append("\n");
            }
            return sb.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i + 1).append(" ").append(lines[i]).append("\n");
        }
        sb.append("… +").append(lines.length - 5).append(" lines");
        return sb.toString().trim();
    }

    @Test
    void getDisplayNameReadFile() { assertEquals("Read", getDisplayName("read_file")); }
    @Test
    void getDisplayNameWriteFile() { assertEquals("Write", getDisplayName("write_file")); }
    @Test
    void getDisplayNameEditFile() { assertEquals("Edit", getDisplayName("edit_file")); }
    @Test
    void getDisplayNameBash() { assertEquals("Bash", getDisplayName("bash")); }
    @Test
    void getDisplayNameGrep() { assertEquals("Grep", getDisplayName("grep")); }
    @Test
    void getDisplayNameGlob() { assertEquals("Glob", getDisplayName("glob")); }
    @Test
    void getDisplayNameTodoCreate() { assertEquals("TodoWrite", getDisplayName("todo_create")); }
    @Test
    void getDisplayNameWebSearch() { assertEquals("WebSearch", getDisplayName("web_free_search")); }
    @Test
    void getDisplayNameUnknownTool() { assertEquals("My Custom Tool", getDisplayName("my_custom_tool")); }

    @Test
    void formatArgsReadFilePath() {
        String result = formatToolArgs("read_file", java.util.Map.of("file_path", "/src/main.py"));
        assertTrue(result.contains("main.py"));
    }

    @Test
    void formatArgsReadFileWithLimit() {
        String result = formatToolArgs("read_file",
                java.util.Map.of("file_path", "/src/main.py", "limit", 10));
        assertTrue(result.contains("limit=10"));
    }

    @Test
    void formatArgsBashTruncatesLong() {
        String longCmd = "a".repeat(80);
        String result = formatToolArgs("bash", java.util.Map.of("command", longCmd));
        assertTrue(result.length() <= 63);
        assertTrue(result.endsWith("..."));
    }

    @Test
    void formatArgsBashShortCommand() {
        assertEquals("git status", formatToolArgs("bash", java.util.Map.of("command", "git status")));
    }

    @Test
    void formatArgsGrepPatternAndPath() {
        String result = formatToolArgs("grep",
                java.util.Map.of("pattern", "def hello", "path", "src/"));
        assertTrue(result.contains("\"def hello\""));
        assertTrue(result.contains("src/"));
    }

    @Test
    void formatArgsGlobPattern() {
        assertEquals("**/*.py", formatToolArgs("glob", java.util.Map.of("pattern", "**/*.py")));
    }

    @Test
    void formatArgsTodoNoArgs() {
        assertEquals("", formatToolArgs("todo_create", java.util.Map.of("tasks", "a;b")));
    }

    @Test
    void formatResultReadFileLines() {
        assertTrue(formatToolResult("read_file", "line1\nline2\nline3\n").contains("Read 3 lines"));
    }

    @Test
    void formatResultReadFileSingleLine() {
        assertEquals("Read 1 lines", formatToolResult("read_file", "line1"));
    }

    @Test
    void formatResultReadFileSingleLineWithTrailingNewline() {
        assertEquals("Read 1 lines", formatToolResult("read_file", "line1\n"));
    }

    @Test
    void formatResultBashSingleLine() {
        assertEquals("hello world", formatToolResult("bash", "hello world"));
    }

    @Test
    void formatResultBashMultiLine() {
        assertTrue(formatToolResult("bash", "line1\nline2\nline3\nline4").contains("+3 lines"));
    }

    @Test
    void formatResultGrepMatches() {
        assertTrue(formatToolResult("grep", "file1.py:10:match\nfile2.py:20:match\n").contains("Found 2 matches"));
    }

    @Test
    void formatResultGrepNoMatches() {
        assertTrue(formatToolResult("grep", "").contains("Done"));
    }

    @Test
    void formatResultGlobFiles() {
        assertTrue(formatToolResult("glob", "a.py\nb.py\nc.py\n").contains("Found 3 files"));
    }

    @Test
    void formatResultEmpty() { assertEquals("Done", formatToolResult("bash", "")); }

    @Test
    void formatWritePreviewShortContent() {
        String content = "line1\nline2\nline3";
        String result = formatWritePreview(content);
        assertTrue(result.contains("1 line1"));
        assertTrue(result.contains("2 line2"));
        assertTrue(result.contains("3 line3"));
        assertFalse(result.contains("…"));
    }

    @Test
    void formatWritePreviewLongContent() {
        String content = String.join("\n",
                java.util.stream.IntStream.range(0, 10).mapToObj(i -> "line" + i).toArray(String[]::new));
        String result = formatWritePreview(content);
        assertTrue(result.contains("… +5 lines"));
    }
}
