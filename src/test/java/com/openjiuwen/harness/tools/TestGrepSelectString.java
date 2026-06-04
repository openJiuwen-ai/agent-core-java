/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_grep_select_string.py} in
 * {@code tests/unit_tests/harness/tools/test_grep_select_string.py}.
 */
@DisplayName("Grep Select-String fallback tests")
class TestGrepSelectString {

    @TempDir
    Path tempDir;

    @Test
    void testCmdContentModeFormatsFilepathLinenum() {
        String cmd = command(Map.of("path", tempDir.toString()));

        assertTrue(cmd.contains("ForEach-Object"));
        assertTrue(cmd.contains("$_.LineNumber"));
        assertTrue(cmd.contains("$_.Path"));
    }

    @Test
    void testCmdFilesWithMatchesMode() {
        String cmd = command(Map.of("path", tempDir.toString(), "output_mode", "files_with_matches"));

        assertTrue(cmd.contains("Select-Object -ExpandProperty Path -Unique"));
        assertFalse(cmd.contains("Group-Object"));
    }

    @Test
    void testCmdCountMode() {
        String cmd = command(Map.of("path", tempDir.toString(), "output_mode", "count"));

        assertTrue(cmd.contains("Group-Object Path"));
        assertTrue(cmd.contains("ForEach-Object"));
    }

    @Test
    void testCmdCaseSensitiveFlagWhenNotIgnoreCase() {
        String cmd = command(Map.of("path", tempDir.toString(), "ignore_case", false));

        assertTrue(cmd.contains("-CaseSensitive"));
    }

    @Test
    void testCmdNoCaseSensitiveFlagWhenIgnoreCase() {
        String cmd = command(Map.of("path", tempDir.toString(), "ignore_case", true));

        assertFalse(cmd.contains("-CaseSensitive"));
    }

    @Test
    void testCmdGlobFilterUsesLike() {
        String cmd = command(Map.of("path", tempDir.toString(), "glob", "*.py"));

        assertTrue(cmd.contains("-like"));
        assertTrue(cmd.contains("*.py"));
    }

    @Test
    void testCmdBraceGlobExpanded() {
        String cmd = command(Map.of("path", tempDir.toString(), "glob", "*.{ts,tsx}"));

        assertTrue(cmd.contains("*.ts"));
        assertTrue(cmd.contains("*.tsx"));
    }

    @Test
    void testCmdVcsDirectoriesExcluded() {
        String cmd = command(Map.of("path", tempDir.toString()));

        assertTrue(cmd.contains("-notmatch"));
        assertTrue(cmd.contains("\\.git"));
        assertTrue(cmd.contains("\\.svn"));
    }

    @Test
    void testCmdContextLinesInContentMode() {
        String cmd = command(Map.of("path", tempDir.toString(), "-B", 2, "-A", 3));

        assertTrue(cmd.contains("-Context 2,3"));
    }

    @Test
    void testCmdContextCAppliesSymmetrically() {
        String cmd = command(Map.of("path", tempDir.toString(), "-C", 2));

        assertTrue(cmd.contains("-Context 2,2"));
    }

    @Test
    void testCmdContextLinesNotInNonContentMode() {
        String cmd = command(Map.of("path", tempDir.toString(), "output_mode", "files_with_matches", "-B", 2));

        assertFalse(cmd.contains("-Context"));
    }

    @Test
    void testCmdSingleFileUsesGetItemNotRecurse() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "x");

        String cmd = command(Map.of("path", file.toString()));

        assertTrue(cmd.contains("Get-Item -LiteralPath"));
        assertFalse(cmd.contains("Get-ChildItem"));
    }

    @Test
    void testCmdDirectoryUsesGetChilditemRecurse() {
        String cmd = command(Map.of("path", tempDir.toString()));

        assertTrue(cmd.contains("Get-ChildItem"));
        assertTrue(cmd.contains("-Recurse"));
    }

    @Test
    void testCmdErrorActionSilentlyContinue() {
        String cmd = command(Map.of("path", tempDir.toString()));

        assertTrue(cmd.startsWith("$ErrorActionPreference='SilentlyContinue'"));
    }

    @Test
    void testTypeFilterReturnsErrorWithoutRg() {
        ToolOutput result = invoke(Map.of("pattern", "x", "path", tempDir.toString(), "type", "py"));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().toLowerCase().contains("rg")
                || result.getError().toLowerCase().contains("type"));
    }

    @Test
    void testMultilineReturnsErrorWithoutRg() {
        ToolOutput result = invoke(Map.of("pattern", "x", "path", tempDir.toString(), "multiline", true));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().toLowerCase().contains("rg")
                || result.getError().toLowerCase().contains("multiline"));
    }

    @Test
    void testSsContentModeBasic() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "Target\nOther\n");

        ToolOutput result = invoke(Map.of("pattern", "Target", "path", tempDir.toString()));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(data(result).get("count").equals(1));
        assertFalse(String.valueOf(data(result).get("stdout")).contains("Other"));
    }

    @Test
    void testSsFilesWithMatchesMode() throws Exception {
        Files.writeString(tempDir.resolve("match.txt"), "needle\n");
        Files.writeString(tempDir.resolve("nomatch.txt"), "other\n");

        ToolOutput result = invoke(Map.of("pattern", "needle", "path", tempDir.toString(),
                "output_mode", "files_with_matches"));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(data(result).get("numFiles").equals(1));
        assertTrue(String.valueOf(data(result).get("filenames")).contains("match.txt"));
        assertFalse(String.valueOf(data(result).get("filenames")).contains("nomatch.txt"));
    }

    @Test
    void testSsCountMode() throws Exception {
        Files.writeString(tempDir.resolve("f.txt"), "hit\nhit\nmiss\n");

        ToolOutput result = invoke(Map.of("pattern", "hit", "path", tempDir.toString(), "output_mode", "count"));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(data(result).get("numMatches").equals(2));
    }

    @Test
    void testSsGlobFilter() throws Exception {
        Files.writeString(tempDir.resolve("keep.py"), "needle\n");
        Files.writeString(tempDir.resolve("skip.txt"), "needle\n");

        ToolOutput result = invoke(Map.of("pattern", "needle", "path", tempDir.toString(),
                "glob", "*.py", "output_mode", "files_with_matches"));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(data(result).get("numFiles").equals(1));
        assertTrue(String.valueOf(data(result).get("filenames")).contains("keep.py"));
    }

    @Test
    void testSsExcludesVcsDirectory() throws Exception {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(tempDir.resolve("main.py"), "needle\n");
        Files.writeString(gitDir.resolve("ignored.txt"), "needle\n");

        ToolOutput result = invoke(Map.of("pattern", "needle", "path", tempDir.toString(),
                "output_mode", "files_with_matches"));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(data(result).get("numFiles").equals(1));
        assertFalse(String.valueOf(data(result).get("filenames")).contains(".git"));
    }

    @Test
    void testSsCaseInsensitive() throws Exception {
        Files.writeString(tempDir.resolve("f.txt"), "HELLO world\n");

        ToolOutput result = invoke(Map.of("pattern", "hello", "path", tempDir.toString(), "ignore_case", true));

        assertTrue(result.isSuccess(), result.getError());
        assertTrue(data(result).get("count").equals(1));
    }

    private String command(Map<String, Object> inputs) {
        return new GrepTool(null).buildSelectStringCommand(withPattern(inputs));
    }

    private ToolOutput invoke(Map<String, Object> inputs) {
        return (ToolOutput) new GrepTool(null).invoke(inputs, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private Map<String, Object> withPattern(Map<String, Object> inputs) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(inputs);
        merged.putIfAbsent("pattern", "needle");
        return merged;
    }
}
