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
 * Tests for write tool implementation.
 *
 * <p>Mirrors Python's write-file coverage in
 * {@code tests.unit_tests.harness.tools.test_filesystem_tools}.
 */
class TestWrite {

    private SysOperation sysOp;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_write_tool_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testWriteToolExists() {
        assertNotNull(WriteFileTool.class);
    }

    @Test
    @Tag("level1")
    void testWriteToolConstruction() {
        assertNotNull(new WriteFileTool(sysOp));
    }

    @Test
    @Tag("level1")
    void testWriteFileNewFile() throws IOException {
        Path filePath = tempDir.resolve("newfile.txt");
        String content = "first line\nsecond line\nthird line";

        WriteFileTool tool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", filePath.toString(), "content", content),
                Map.of()
        );

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(filePath));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("create", data.get("type"));
        assertEquals(Boolean.TRUE, data.get("created"));
        assertTrue((Integer) data.get("bytes_written") > 0);
        assertEquals(content, Files.readString(filePath));
    }

    @Test
    @Tag("level1")
    void testWriteAndReadRoundtrip() throws IOException {
        Path filePath = tempDir.resolve("roundtrip.txt");
        String content = "write and read roundtrip";

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
        assertEquals(content, data.get("content"));
    }

    @Test
    @Tag("level1")
    void testWriteEmptyPath() {
        WriteFileTool tool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", "", "content", "some content"),
                Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().toLowerCase().contains("path"));
    }

    @Test
    @Tag("level1")
    void testWriteChineseContent() throws IOException {
        Path filePath = tempDir.resolve("chinese.txt");
        String content = "中文测试\n更多中文\n测试成功";

        WriteFileTool tool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", filePath.toString(), "content", content),
                Map.of()
        );

        assertTrue(result.isSuccess());
        assertTrue(Files.readString(filePath).contains("中文测试"));
    }

    @Test
    @Tag("level1")
    void testWriteExistingFileRequiresReadBeforeOverwrite() throws IOException {
        Path filePath = tempDir.resolve("existing.txt");
        Files.writeString(filePath, "existing content");

        WriteFileTool tool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", filePath.toString(), "content", "replacement"),
                Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().toLowerCase().contains("read"));
    }

    @Test
    @Tag("level1")
    void testWriteExistingFileUpdatesAfterRead() throws IOException {
        Path filePath = tempDir.resolve("rewrite.txt");
        Files.writeString(filePath, "old content");

        ReadFileTool readTool = new ReadFileTool(sysOp);
        ToolOutput readResult = (ToolOutput) readTool.invoke(
                Map.of("path", filePath.toString()),
                Map.of()
        );
        assertTrue(readResult.isSuccess());

        WriteFileTool writeTool = new WriteFileTool(sysOp);
        ToolOutput writeResult = (ToolOutput) writeTool.invoke(
                Map.of("path", filePath.toString(), "content", "new content\n"),
                Map.of()
        );

        assertTrue(writeResult.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) writeResult.getData();
        assertEquals("update", data.get("type"));
        assertEquals(Boolean.FALSE, data.get("created"));
        assertEquals("old content", data.get("original_file"));
        assertEquals("new content\n", Files.readString(filePath));
    }

    @Test
    @Tag("level0")
    void testWriteToolMethods() {
        assertTrue(WriteFileTool.class.getDeclaredMethods().length > 0);
    }
}
