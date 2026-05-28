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
import com.openjiuwen.harness.tools.ListDirTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for file operation tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_filesystem_tools file operation tests.
 * Tests file read/write/list operations.
 */
class TestFileOperation {

    private SysOperation sysOp;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_file_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testFileOperationToolExists() {
        assertNotNull(ReadFileTool.class);
        assertNotNull(WriteFileTool.class);
        assertNotNull(ListDirTool.class);
    }

    @Test
    @Tag("level1")
    void testFileReadWriteCycle() throws IOException {
        Path filePath = tempDir.resolve("cycle_test.txt");
        String content = "第一行\n第二行\n第三行";

        // Write
        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput writeResult = (ToolOutput) writeTool.invoke(
            Map.of("path", filePath.toString(), "content", content),
            Map.of()
        );
        assertTrue(writeResult.isSuccess());

        // Read
        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
            Map.of("path", filePath.toString()),
            Map.of()
        );
        assertTrue(readResult.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) readResult.getData();
        String readContent = data.get("content").toString();
        assertTrue(readContent.contains("第一行"));
        assertTrue(readContent.contains("第二行"));
        assertTrue(readContent.contains("第三行"));
    }

    @Test
    @Tag("level1")
    void testFileReadNonExistent() {
        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput result = (ToolOutput) readTool.invoke(
            Map.of("path", "/nonexistent/file.txt"),
            Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @Tag("level1")
    void testListDirectory() throws IOException {
        // Create some files
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        Files.createDirectory(tempDir.resolve("subdir"));

        ListDirTool listTool = new ListDirTool(sysOp);
        ToolOutput result = (ToolOutput) listTool.invoke(
            Map.of("path", tempDir.toString()),
            Map.of()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    @Tag("level1")
    void testFileWriteEmptyPath() {
        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) writeTool.invoke(
            Map.of("path", "", "content", "some content"),
            Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @Tag("level1")
    void testFileReadEmptyPath() {
        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput result = (ToolOutput) readTool.invoke(
            Map.of("path", ""),
            Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @Tag("level1")
    void testFileOperationChineseFilename() throws IOException {
        Path chineseFile = tempDir.resolve("中文文件名.txt");
        String content = "中文内容测试";
        
        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput writeResult = (ToolOutput) writeTool.invoke(
            Map.of("path", chineseFile.toString(), "content", content),
            Map.of()
        );
        assertTrue(writeResult.isSuccess());

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
            Map.of("path", chineseFile.toString()),
            Map.of()
        );
        assertTrue(readResult.isSuccess());
    }

    @Test
    @Tag("level0")
    void testFileOperationMethods() {
        assertTrue(ReadFileTool.class.getDeclaredMethods().length > 0);
        assertTrue(WriteFileTool.class.getDeclaredMethods().length > 0);
        assertTrue(ListDirTool.class.getDeclaredMethods().length > 0);
    }
}