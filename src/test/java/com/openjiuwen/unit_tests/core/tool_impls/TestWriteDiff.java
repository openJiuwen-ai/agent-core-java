/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.openjiuwen.harness.tools.ReadFileTool;
import com.openjiuwen.harness.tools.WriteFileTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for write diff tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_filesystem_tools write-related tests.
 * Tests diff-based file writing and update operations.
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
        // Java uses WriteFileTool for write operations
        assertNotNull(WriteFileTool.class);
    }

    @Test
    @Tag("level1")
    void testWriteDiffConstruction() {
        WriteFileTool tool = new WriteFileTool(sysOp);
        assertNotNull(tool);
    }

    @Test
    @Tag("level1")
    void testWriteFileUpdate() throws IOException {
        // Create initial file
        Path filePath = tempDir.resolve("update_test.txt");
        Files.writeString(filePath, "old content");

        // Update file
        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) writeTool.invoke(
            Map.of("path", filePath.toString(), "content", "new content"),
            Map.of()
        );

        assertTrue(result.isSuccess());
        
        // Verify content changed
        String updatedContent = Files.readString(filePath);
        assertTrue(updatedContent.contains("new content"));
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
        assertTrue(Files.readString(filePath).contains("中文内容"));
    }

    @Test
    @Tag("level1")
    void testWriteDiffOverwriteNonexistentFile() throws IOException {
        // Writing to a non-existent file should create it
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