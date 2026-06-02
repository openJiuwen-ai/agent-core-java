/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.ToolDisplay;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CLI tool display formatting.
 * <p>
 * Mirrors Python's {@code test_tool_display} in
 * {@code tests.cli.unit.test_tool_display}.
 */
class ToolDisplayTest {

    @Test
    void getDisplayNameReadFile() {
        assertEquals("Read", ToolDisplay.getDisplayName("read_file"));
    }

    @Test
    void getDisplayNameWriteFile() {
        assertEquals("Write", ToolDisplay.getDisplayName("write_file"));
    }

    @Test
    void getDisplayNameEditFile() {
        assertEquals("Edit", ToolDisplay.getDisplayName("edit_file"));
    }

    @Test
    void getDisplayNameBash() {
        assertEquals("Bash", ToolDisplay.getDisplayName("bash"));
    }

    @Test
    void getDisplayNameGrep() {
        assertEquals("Grep", ToolDisplay.getDisplayName("grep"));
    }

    @Test
    void getDisplayNameGlob() {
        assertEquals("Glob", ToolDisplay.getDisplayName("glob"));
    }

    @Test
    void getDisplayNameTodoCreate() {
        assertEquals("TodoWrite", ToolDisplay.getDisplayName("todo_create"));
    }

    @Test
    void getDisplayNameWebSearch() {
        assertEquals("WebSearch", ToolDisplay.getDisplayName("web_free_search"));
    }

    @Test
    void getDisplayNameUnknownTool() {
        assertEquals("My Custom Tool", ToolDisplay.getDisplayName("my_custom_tool"));
    }

    @Test
    void formatArgsReadFilePath() {
        String result = ToolDisplay.formatToolArgs("read_file", Map.of("file_path", "/src/main.py"));
        assertTrue(result.contains("main.py"));
    }

    @Test
    void formatArgsReadFileWithLimit() {
        String result = ToolDisplay.formatToolArgs("read_file",
                Map.of("file_path", "/src/main.py", "limit", 10));
        assertTrue(result.contains("limit=10"));
    }

    @Test
    void formatArgsBashTruncatesLong() {
        String longCmd = "a".repeat(80);
        String result = ToolDisplay.formatToolArgs("bash", Map.of("command", longCmd));
        assertTrue(result.length() <= 63);
        assertTrue(result.endsWith("..."));
    }

    @Test
    void formatArgsBashShortCommand() {
        assertEquals("git status", ToolDisplay.formatToolArgs("bash", Map.of("command", "git status")));
    }

    @Test
    void formatArgsGrepPatternAndPath() {
        String result = ToolDisplay.formatToolArgs("grep",
                Map.of("pattern", "def hello", "path", "src/"));
        assertTrue(result.contains("\"def hello\""));
        assertTrue(result.contains("src/"));
    }

    @Test
    void formatArgsGlobPattern() {
        assertEquals("**/*.py", ToolDisplay.formatToolArgs("glob", Map.of("pattern", "**/*.py")));
    }

    @Test
    void formatArgsTodoNoArgs() {
        assertEquals("", ToolDisplay.formatToolArgs("todo_create", Map.of("tasks", "a;b")));
    }

    @Test
    void formatArgsStringArgsParsed() {
        String result = ToolDisplay.formatToolArgs("read_file", "{\"file_path\": \"/test.py\"}");
        assertTrue(result.contains("test.py"));
    }

    @Test
    void formatResultReadFileLines() {
        assertTrue(ToolDisplay.formatToolResult("read_file", "line1\nline2\nline3\n")
                .contains("Read 3 lines"));
    }

    @Test
    void formatResultReadFileSingleLine() {
        assertEquals("Read 1 lines", ToolDisplay.formatToolResult("read_file", "line1"));
    }

    @Test
    void formatResultReadFileSingleLineWithTrailingNewline() {
        assertEquals("Read 1 lines", ToolDisplay.formatToolResult("read_file", "line1\n"));
    }

    @Test
    void formatResultBashSingleLine() {
        assertEquals("hello world", ToolDisplay.formatToolResult("bash", "hello world"));
    }

    @Test
    void formatResultBashMultiLine() {
        assertTrue(ToolDisplay.formatToolResult("bash", "line1\nline2\nline3\nline4")
                .contains("+3 lines"));
    }

    @Test
    void formatResultGrepMatches() {
        assertTrue(ToolDisplay.formatToolResult("grep", "file1.py:10:match\nfile2.py:20:match\n")
                .contains("Found 2 matches"));
    }

    @Test
    void formatResultGrepNoMatches() {
        assertTrue(ToolDisplay.formatToolResult("grep", "").contains("Done"));
    }

    @Test
    void formatResultGlobFiles() {
        assertTrue(ToolDisplay.formatToolResult("glob", "a.py\nb.py\nc.py\n")
                .contains("Found 3 files"));
    }

    @Test
    void formatResultEmpty() {
        assertEquals("Done", ToolDisplay.formatToolResult("bash", ""));
    }

    @Test
    void formatWritePreviewShortContent() {
        String result = ToolDisplay.formatWritePreview("line1\nline2\nline3");
        assertTrue(result.contains("1 line1"));
        assertTrue(result.contains("2 line2"));
        assertTrue(result.contains("3 line3"));
        assertFalse(result.contains("+"));
    }

    @Test
    void formatWritePreviewLongContent() {
        String content = String.join("\n",
                IntStream.range(0, 10).mapToObj(i -> "line" + i).toArray(String[]::new));
        String result = ToolDisplay.formatWritePreview(content);
        assertTrue(result.contains("+5 lines"));
    }
}
