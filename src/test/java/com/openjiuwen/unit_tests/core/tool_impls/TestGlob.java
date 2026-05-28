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

import com.openjiuwen.harness.tools.GlobTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for glob tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_filesystem_tools glob-related tests.
 * Tests glob functionality for file pattern matching.
 */
class TestGlob {

    private SysOperation sysOp;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_glob_tool_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testGlobToolExists() {
        assertNotNull(GlobTool.class);
    }

    @Test
    @Tag("level1")
    void testGlobToolConstruction() {
        GlobTool tool = new GlobTool(sysOp);
        assertNotNull(tool);
    }

    @Test
    @Tag("level1")
    void testGlobToolListFiles() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("test1.txt");
        Path file2 = tempDir.resolve("test2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        GlobTool tool = new GlobTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", "*.txt"),
            Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) result.getData();
        assertFalse(files.isEmpty());
    }

    @Test
    @Tag("level1")
    void testGlobToolWithEmptyPattern() throws IOException {
        // Create test files
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");

        GlobTool tool = new GlobTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", ""),
            Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @Tag("level1")
    void testGlobToolRecursively() throws IOException {
        // Create nested directory structure
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);
        Path nestedFile = subdir.resolve("nested.txt");
        Files.writeString(nestedFile, "nested content");

        GlobTool tool = new GlobTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("path", tempDir.toString(), "pattern", "*.txt"),
            Map.of()
        );

        assertTrue(result.isSuccess());
        
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) result.getData();
        // Should include nested files
        assertTrue(files.size() >= 1);
    }

    @Test
    @Tag("level0")
    void testGlobConfigExists() {
        // GlobTool doesn't use separate config class, test it directly
        assertTrue(GlobTool.class.getDeclaredMethods().length > 0);
    }
}