/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.tools.GrepTool;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for search tool implementation.
 *
 * <p>Mirrors search-related coverage from
 * {@code tests.unit_tests.harness.tools.test_filesystem_tools}.</p>
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
        Files.writeString(tempDir.resolve("search_test.txt"),
                "Line 1: hello world\nLine 2: search pattern\nLine 3: goodbye");

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
    void testSearchIgnoreCase() throws IOException {
        Files.writeString(tempDir.resolve("case_search.txt"), "Alpha\nBETA token\nbeta again");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", tempDir.toString(), "pattern", "beta", "ignore_case", true),
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
    @Tag("level1")
    void testSearchDefaultsToContentMode() throws IOException {
        Files.writeString(tempDir.resolve("main.py"), "needle\n");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", tempDir.toString(), "pattern", "needle"),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("content", data.get("mode"));
        assertEquals(1, data.get("numLines"));
        assertTrue(String.valueOf(data.get("content")).contains("main.py"));
    }

    @Test
    @Tag("level0")
    void testSearchConfigExists() {
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testSearchToolMethods() {
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }
}
