/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.tools.ReadFileTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.WriteFileTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for updating existing files through {@link WriteFileTool}.
 *
 * <p>Mirrors the Python write/update workflow coverage in
 * {@code tests.unit_tests.harness.tools.test_filesystem_tools}.
 */
class TestWriteDiff {

    private SysOperation sysOp;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_write_diff_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testWriteDiffToolExists() {
        assertNotNull(WriteFileTool.class);
    }

    @Test
    @Tag("level1")
    void testWriteDiffConstruction() {
        assertNotNull(new WriteFileTool(sysOp));
    }

    @Test
    @Tag("level1")
    void testWriteFileUpdate() throws IOException {
        Path filePath = tempDir.resolve("update_test.txt");
        Files.writeString(filePath, "old content");

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
                Map.of("path", filePath.toString()),
                Map.of()
        );
        assertTrue(readResult.isSuccess());

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", "new content"),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("update", data.get("type"));
        assertEquals(Boolean.FALSE, data.get("created"));
        assertEquals("old content", data.get("original_file"));
        assertEquals("new content", Files.readString(filePath));
    }

    @Test
    @Tag("level1")
    void testWriteDiffChineseContent() throws IOException {
        Path filePath = tempDir.resolve("chinese_diff.txt");
        String content = "原始中文内容\n修改后的内容\n";

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", content),
                Map.of()
        );

        assertTrue(result.isSuccess());
        assertTrue(Files.readString(filePath).contains("原始中文内容"));
    }

    @Test
    @Tag("level1")
    void testWriteDiffRejectsExternallyModifiedFile() throws IOException {
        Path filePath = tempDir.resolve("stale.txt");
        Files.writeString(filePath, "before");

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
                Map.of("path", filePath.toString()),
                Map.of()
        );
        assertTrue(readResult.isSuccess());

        Files.writeString(filePath, "changed externally");

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", "replacement"),
                Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("modified since read"));
        assertEquals("changed externally", Files.readString(filePath));
    }

    @Test
    @Tag("level1")
    void testWriteDiffOverwriteNonexistentFile() {
        Path filePath = tempDir.resolve("new_file.txt");

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", "new file content"),
                Map.of()
        );

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(filePath));
    }

    @Test
    @Tag("level0")
    void testWriteDiffMethods() {
        assertTrue(WriteFileTool.class.getDeclaredMethods().length > 0);
    }
}
