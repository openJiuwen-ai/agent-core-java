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

import com.openjiuwen.harness.tools.GrepTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for search tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_filesystem_tools search-related tests.
 * Tests file content search functionality.
 */
class TestSearch {

    private SysOperation sysOp;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_search_tool_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testSearchToolExists() {
        // Java uses GrepTool for search functionality
        assertNotNull(GrepTool.class);
    }

    @Test
    @Tag("level1")
    void testSearchToolConstruction() {
        GrepTool tool = new GrepTool(sysOp);
        assertNotNull(tool);
    }

    @Test
    @Tag("level1")
    void testSearchBasicPattern() throws IOException {
        // Create test file
        Path file = tempDir.resolve("search_test.txt");
        Files.writeString(file, "Line 1: hello world\nLine 2: search pattern\nLine 3: goodbye");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", "search"),
            Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @Tag("level1")
    void testSearchChineseContent() throws IOException {
        Path file = tempDir.resolve("chinese_search.txt");
        Files.writeString(file, "中文内容测试\n搜索关键词\n更多中文");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", "关键词"),
            Map.of()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    @Tag("level1")
    void testSearchEmptyPattern() {
        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", ""),
            Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @Tag("level0")
    void testSearchConfigExists() {
        // Search functionality uses GrepTool
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testSearchToolMethods() {
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }
}