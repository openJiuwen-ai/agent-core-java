/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.ui.ToolDisplay;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.cli.unit.test_tool_display} in
 * {@code tests/cli/unit/test_tool_display.py}.
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
    void formatReadFileArgsShowsPath() {
        String result = ToolDisplay.formatToolArgs("read_file", Map.of("file_path", "/src/main.py"));

        assertTrue(result.contains("main.py"));
    }

    @Test
    void formatReadFileArgsShowsLimit() {
        String result = ToolDisplay.formatToolArgs("read_file", Map.of("file_path", "/src/main.py", "limit", 10));

        assertTrue(result.contains("limit=10"));
    }

    @Test
    void formatBashArgsTruncatesLongCommand() {
        String result = ToolDisplay.formatToolArgs("bash", Map.of("command", "a".repeat(80)));

        assertTrue(result.length() <= 63);
        assertTrue(result.endsWith("..."));
    }

    @Test
    void formatBashArgsShowsShortCommand() {
        assertEquals("git status", ToolDisplay.formatToolArgs("bash", Map.of("command", "git status")));
    }

    @Test
    void formatGrepArgsShowsPatternAndPath() {
        String result = ToolDisplay.formatToolArgs("grep", Map.of("pattern", "def hello", "path", "src/"));

        assertTrue(result.contains("\"def hello\""));
        assertTrue(result.contains("src/"));
    }

    @Test
    void formatGlobArgsShowsPattern() {
        assertEquals("**/*.py", ToolDisplay.formatToolArgs("glob", Map.of("pattern", "**/*.py")));
    }

    @Test
    void formatTodoArgsAreEmpty() {
        assertEquals("", ToolDisplay.formatToolArgs("todo_create", Map.of("tasks", "a;b")));
    }

    @Test
    void formatStringArgsAreParsed() {
        String result = ToolDisplay.formatToolArgs("read_file", "{\"file_path\": \"/test.py\"}");

        assertTrue(result.contains("test.py"));
    }

    @Test
    void formatReadFileResultCountsLines() {
        assertTrue(ToolDisplay.formatToolResult("read_file", "line1\nline2\nline3\n").contains("Read 3 lines"));
    }

    @Test
    void formatReadFileResultCountsSingleLine() {
        assertEquals("Read 1 lines", ToolDisplay.formatToolResult("read_file", "line1"));
    }

    @Test
    void formatReadFileResultIgnoresTrailingNewlineAsExtraLine() {
        assertEquals("Read 1 lines", ToolDisplay.formatToolResult("read_file", "line1\n"));
    }

    @Test
    void formatBashResultShowsSingleLine() {
        assertEquals("hello world", ToolDisplay.formatToolResult("bash", "hello world"));
    }

    @Test
    void formatBashResultSummarizesMultiLineOutput() {
        assertTrue(ToolDisplay.formatToolResult("bash", "line1\nline2\nline3\nline4").contains("+3 lines"));
    }

    @Test
    void formatGrepResultCountsMatches() {
        assertTrue(ToolDisplay.formatToolResult("grep", "file1.py:10:match\nfile2.py:20:match\n")
                .contains("Found 2 matches"));
    }

    @Test
    void formatGrepEmptyResultIsDone() {
        assertTrue(ToolDisplay.formatToolResult("grep", "").contains("Done"));
    }

    @Test
    void formatGlobResultCountsFiles() {
        assertTrue(ToolDisplay.formatToolResult("glob", "a.py\nb.py\nc.py\n").contains("Found 3 files"));
    }

    @Test
    void formatEmptyBashResultIsDone() {
        assertEquals("Done", ToolDisplay.formatToolResult("bash", ""));
    }

    @Test
    void formatWritePreviewShowsShortContent() {
        String result = ToolDisplay.formatWritePreview("line1\nline2\nline3");

        assertTrue(result.contains("1 line1"));
        assertTrue(result.contains("2 line2"));
        assertTrue(result.contains("3 line3"));
        assertFalse(result.contains("\u2026"));
    }

    @Test
    void formatWritePreviewTruncatesLongContent() {
        String result = ToolDisplay.formatWritePreview(String.join("\n",
                "line0", "line1", "line2", "line3", "line4", "line5", "line6", "line7", "line8", "line9"));

        assertTrue(result.contains("\u2026 +5 lines"));
    }
}
