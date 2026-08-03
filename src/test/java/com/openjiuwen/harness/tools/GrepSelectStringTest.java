/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.FilesystemTools.GrepTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_grep_select_string} in
 * {@code tests/unit_tests/harness/tools/test_grep_select_string.py}.
 */
class GrepSelectStringTest {

    @TempDir
    Path tmpDir;

    @Test
    void cmdContentModeFormatsFilepathLineNumber() {
        String command = build(tmpDir);

        assertTrue(command.contains("ForEach-Object"));
        assertTrue(command.contains("$_.LineNumber"));
        assertTrue(command.contains("$_.Path"));
    }

    @Test
    void cmdFilesWithMatchesMode() {
        String command = build(tmpDir, "files_with_matches", null, null, null, null, false, null);

        assertTrue(command.contains("Select-Object -ExpandProperty Path -Unique"));
        assertFalse(command.contains("Group-Object"));
    }

    @Test
    void cmdCountMode() {
        String command = build(tmpDir, "count", null, null, null, null, false, null);

        assertTrue(command.contains("Group-Object Path"));
        assertTrue(command.contains("ForEach-Object"));
    }

    @Test
    void cmdCaseSensitiveFlagWhenNotIgnoreCase() {
        String command = build(tmpDir, "content", null, null, null, null, false, null);

        assertTrue(command.contains("-CaseSensitive"));
    }

    @Test
    void cmdNoCaseSensitiveFlagWhenIgnoreCase() {
        String command = build(tmpDir, "content", null, null, null, null, true, null);

        assertFalse(command.contains("-CaseSensitive"));
    }

    @Test
    void cmdGlobFilterUsesLike() {
        String command = build(tmpDir, "content", null, null, null, null, false, "*.py");

        assertTrue(command.contains("-like"));
        assertTrue(command.contains("*.py"));
    }

    @Test
    void cmdBraceGlobExpanded() {
        String command = build(tmpDir, "content", null, null, null, null, false, "*.{ts,tsx}");

        assertTrue(command.contains("*.ts"));
        assertTrue(command.contains("*.tsx"));
    }

    @Test
    void cmdVcsDirectoriesExcluded() {
        String command = build(tmpDir);

        assertTrue(command.contains("-notmatch"));
        assertTrue(command.contains("\\.git"));
        assertTrue(command.contains("\\.svn"));
    }

    @Test
    void cmdContextLinesInContentMode() {
        String command = build(tmpDir, "content", 2, 3, null, null, false, null);

        assertTrue(command.contains("-Context 2,3"));
    }

    @Test
    void cmdContextCAppliesSymmetrically() {
        String command = build(tmpDir, "content", null, null, 2, null, false, null);

        assertTrue(command.contains("-Context 2,2"));
    }

    @Test
    void cmdContextLinesNotInNonContentMode() {
        String command = build(tmpDir, "files_with_matches", 2, null, null, null, false, null);

        assertFalse(command.contains("-Context"));
    }

    @Test
    void cmdSingleFileUsesGetItemNotRecurse() throws Exception {
        Path file = Files.writeString(tmpDir.resolve("file.txt"), "x");

        String command = build(file);

        assertTrue(command.contains("Get-Item -LiteralPath"));
        assertFalse(command.contains("Get-ChildItem"));
    }

    @Test
    void cmdDirectoryUsesGetChilditemRecurse() {
        String command = build(tmpDir);

        assertTrue(command.contains("Get-ChildItem"));
        assertTrue(command.contains("-Recurse"));
    }

    @Test
    void cmdErrorActionSilentlyContinue() {
        String command = build(tmpDir);

        assertTrue(command.startsWith("$ErrorActionPreference='SilentlyContinue'"));
    }

    @Test
    void typeFilterReturnsErrorWithoutRg() throws Exception {
        GrepTool tool = windowsWithoutRgTool();

        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "pattern", "x",
                "path", tmpDir.toString(),
                "type", "py"
        ), Map.of());

        assertFalse(output.isSuccess());
        assertTrue(output.getError().toLowerCase().contains("rg")
                || output.getError().toLowerCase().contains("type"));
    }

    @Test
    void multilineReturnsErrorWithoutRg() throws Exception {
        GrepTool tool = windowsWithoutRgTool();

        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "pattern", "x",
                "path", tmpDir.toString(),
                "multiline", true
        ), Map.of());

        assertFalse(output.isSuccess());
        assertTrue(output.getError().toLowerCase().contains("rg")
                || output.getError().toLowerCase().contains("multiline"));
    }

    @Test
    @Disabled("Skipped in Python source: requires Windows with no rg in PATH")
    void ssContentModeBasic() {
    }

    @Test
    @Disabled("Skipped in Python source: requires Windows with no rg in PATH")
    void ssFilesWithMatchesMode() {
    }

    @Test
    @Disabled("Skipped in Python source: requires Windows with no rg in PATH")
    void ssCountMode() {
    }

    @Test
    @Disabled("Skipped in Python source: requires Windows with no rg in PATH")
    void ssGlobFilter() {
    }

    @Test
    @Disabled("Skipped in Python source: requires Windows with no rg in PATH")
    void ssExcludesVcsDirectory() {
    }

    @Test
    @Disabled("Skipped in Python source: requires Windows with no rg in PATH")
    void ssCaseInsensitive() {
    }

    private String build(Path path) {
        return build(path, "content", null, null, null, null, false, null);
    }

    private String build(Path path,
                         String outputMode,
                         Integer contextBefore,
                         Integer contextAfter,
                         Integer contextC,
                         Integer context,
                         boolean caseInsensitive,
                         String glob) {
        return new GrepTool(tmpDir.toString()).buildSelectStringCommand(
                "needle",
                path,
                glob,
                outputMode,
                contextBefore,
                contextAfter,
                contextC,
                context,
                caseInsensitive);
    }

    private GrepTool windowsWithoutRgTool() {
        return new GrepTool(tmpDir.toString()) {
            @Override
            protected boolean hasRipgrep() {
                return false;
            }

            @Override
            protected boolean isWindows() {
                return true;
            }
        };
    }
}
