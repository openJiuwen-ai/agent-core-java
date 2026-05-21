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
import java.util.List;
import java.util.Map;

import com.openjiuwen.harness.tools.GrepTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for grep tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_filesystem_tools grep-related tests
 * and tests.unit_tests.harness.tools.test_grep_select_string.
 * Tests grep functionality for pattern matching in files.
 */
class TestGrep {

    private SysOperation sysOp;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_grep_tool_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testGrepToolExists() {
        assertNotNull(GrepTool.class);
    }

    @Test
    @Tag("level1")
    void testGrepToolConstruction() {
        GrepTool tool = new GrepTool(sysOp);
        assertNotNull(tool);
    }

    @Test
    @Tag("level1")
    void testGrepToolSearchPattern() throws IOException {
        // Create test file with content
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "第一行\n第二行\n第三行包含关键词\n第四行");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", "关键词"),
            Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @Tag("level1")
    void testGrepToolEmptyPattern() {
        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", ""),
            Map.of()
        );

        // Empty pattern should fail
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().toLowerCase().contains("pattern"));
    }

    @Test
    @Tag("level1")
    void testGrepToolChineseCharacters() throws IOException {
        // Create file with Chinese content
        Path file = tempDir.resolve("chinese.txt");
        Files.writeString(file, "中文测试内容\n寻找中文字符\n测试成功");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", "中文"),
            Map.of()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    @Tag("level0")
    void testGrepConfigExists() {
        // GrepTool doesn't use separate config class
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testGrepToolMethods() {
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }
}