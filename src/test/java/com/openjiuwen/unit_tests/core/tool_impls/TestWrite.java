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

import com.openjiuwen.harness.tools.WriteFileTool;
import com.openjiuwen.harness.tools.ReadFileTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for write tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_filesystem_tools write-related tests.
 * Tests file writing functionality including read-before-write validation.
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
        WriteFileTool tool = new WriteFileTool(sysOp);
        assertNotNull(tool);
    }

    @Test
    @Tag("level1")
    void testWriteFileNewFile() throws IOException {
        Path filePath = tempDir.resolve("newfile.txt");
        String content = "第一行\n第二行\n第三行";

        WriteFileTool tool = new WriteFileTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", filePath.toString(), "content", content),
            Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertTrue(Files.exists(filePath));
        
        // Verify content was written
        String writtenContent = Files.readString(filePath);
        assertTrue(writtenContent.contains("第一行"));
    }

    @Test
    @Tag("level1")
    void testWriteAndReadRoundtrip() throws IOException {
        Path filePath = tempDir.resolve("roundtrip.txt");
        String content = "测试内容写入和读取";

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
        assertTrue(data.get("content").toString().contains("测试内容"));
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
        String writtenContent = Files.readString(filePath);
        assertTrue(writtenContent.contains("中文测试"));
    }

    @Test
    @Tag("level0")
    void testWriteToolMethods() {
        assertTrue(WriteFileTool.class.getDeclaredMethods().length > 0);
    }
}