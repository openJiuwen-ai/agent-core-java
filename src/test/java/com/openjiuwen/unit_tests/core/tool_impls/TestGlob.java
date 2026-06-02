/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.tools.GlobTool;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for glob tool implementation.
 *
 * <p>Mirrors the glob-related behaviors from
 * {@code tests.unit_tests.harness.tools.test_filesystem_tools}.</p>
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
        Files.writeString(tempDir.resolve("test1.txt"), "content1");
        Files.writeString(tempDir.resolve("test2.txt"), "content2");

        GlobTool tool = new GlobTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", tempDir.toString(), "pattern", ".txt"),
                Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) result.getData();
        assertEqualsAtLeastOne(files);
    }

    @Test
    @Tag("level1")
    void testGlobToolWithEmptyPatternReturnsEverything() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "content");

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
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);
        Path nestedFile = subdir.resolve("nested.txt");
        Files.writeString(nestedFile, "nested content");

        GlobTool tool = new GlobTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("path", tempDir.toString(), "pattern", ".txt"),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) result.getData();
        assertTrue(files.stream().anyMatch(path -> path.endsWith("nested.txt")));
    }

    @Test
    @Tag("level1")
    void testGlobToolDefaultsToCurrentDirectoryWhenPathMissing() throws IOException {
        Path workingDir = Path.of("").toAbsolutePath();
        Path probeFile = workingDir.resolve("glob-default-path-probe.py");
        Files.writeString(probeFile, "content");
        try {
            GlobTool tool = new GlobTool(sysOp);
            ToolOutput result = (ToolOutput) tool.invoke(
                    Map.of("pattern", ".py"),
                    Map.of()
            );

            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) result.getData();
            assertTrue(files.stream().anyMatch(path -> path.endsWith("glob-default-path-probe.py")));
        } finally {
            Files.deleteIfExists(probeFile);
        }
    }

    @Test
    @Tag("level0")
    void testGlobConfigExists() {
        assertTrue(GlobTool.class.getDeclaredMethods().length > 0);
    }

    private void assertEqualsAtLeastOne(List<String> files) {
        assertFalse(files.isEmpty());
    }
}
