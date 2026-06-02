/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.tools.ListDirTool;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for file operation tool implementation.
 *
 * <p>Mirrors Python's filesystem file-operation coverage from
 * {@code tests.unit_tests.harness.tools.test_filesystem_tools}.</p>
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
        String content = "line1\nline2\nline3";

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput writeResult = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", content),
                Map.of()
        );
        assertTrue(writeResult.isSuccess());

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
                Map.of("path", filePath.toString()),
                Map.of()
        );
        assertTrue(readResult.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) readResult.getData();
        assertEquals(filePath.toString(), data.get("path"));
        assertEquals(content, data.get("content"));
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
    void testListDirectoryReturnsOnlyDirectories() throws IOException {
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        Files.createDirectory(tempDir.resolve("subdir"));

        ListDirTool listTool = new ListDirTool(sysOp);
        ToolOutput result = (ToolOutput) listTool.invoke(
                Map.of("path", tempDir.toString()),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<String> directories = (List<String>) result.getData();
        assertEquals(1, directories.size());
        assertTrue(directories.get(0).endsWith("subdir"));
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
    void testFileOperationSupportsOrdinaryFilenames() throws IOException {
        Path filePath = tempDir.resolve("sample-data.txt");
        String content = "plain text payload";

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput writeResult = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", content),
                Map.of()
        );
        assertTrue(writeResult.isSuccess());

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
                Map.of("path", filePath.toString()),
                Map.of()
        );
        assertTrue(readResult.isSuccess());
    }

    @Test
    @Tag("level1")
    void testReadFileReturnsPathAndContent() throws IOException {
        Path filePath = tempDir.resolve("metadata.txt");
        Files.writeString(filePath, "metadata-body");

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput result = (ToolOutput) readTool.invoke(
                Map.of("path", filePath.toString()),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(filePath.toString(), data.get("path"));
        assertEquals("metadata-body", data.get("content"));
    }

    @Test
    @Tag("level0")
    void testFileOperationMethods() {
        assertTrue(ReadFileTool.class.getDeclaredMethods().length > 0);
        assertTrue(WriteFileTool.class.getDeclaredMethods().length > 0);
        assertTrue(ListDirTool.class.getDeclaredMethods().length > 0);
    }
}
