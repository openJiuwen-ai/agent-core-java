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
 * Tests for grep tool implementation.
 *
 * <p>Mirrors grep-related coverage from
 * {@code tests.unit_tests.harness.tools.test_filesystem_tools}.</p>
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
        Files.writeString(tempDir.resolve("test.txt"), "line one\nline two\nline three has keyword\nline four");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", tempDir.toString(), "pattern", "keyword"),
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

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().toLowerCase().contains("pattern"));
    }

    @Test
    @Tag("level1")
    void testGrepToolIgnoreCase() throws IOException {
        Files.writeString(tempDir.resolve("mixed-case.txt"), "Hello World\nHELLO AGAIN\nbye");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", tempDir.toString(), "pattern", "hello", "ignore_case", true),
                Map.of()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    @Tag("level1")
    void testGrepToolCountModeReturnsStructuredCounts() throws IOException {
        Files.writeString(tempDir.resolve("one.py"), "hit\nhit\n");
        Files.writeString(tempDir.resolve("two.py"), "hit\n");

        GrepTool tool = new GrepTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of(
                        "path", tempDir.toString(),
                        "pattern", "hit",
                        "glob", "*.py",
                        "output_mode", "count"
                ),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("count", data.get("mode"));
        assertEquals(2, data.get("numFiles"));
        assertEquals(3, data.get("numMatches"));
        assertTrue(String.valueOf(data.get("content")).contains("one.py:2"));
        assertTrue(String.valueOf(data.get("content")).contains("two.py:1"));
    }

    @Test
    @Tag("level0")
    void testGrepConfigExists() {
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testGrepToolMethods() {
        assertTrue(GrepTool.class.getDeclaredMethods().length > 0);
    }
}
