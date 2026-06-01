/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test SysOperation tools registered in ResourceMgr.
 * <p>
 * Mirrors Python's {@code test_operation_as_tool.py} in
 * {@code tests/unit_tests/core/sys_operation/local/test_operation_as_tool.py}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestOperationAsTool {

    @TempDir
    Path workDir;

    private static final String CARD_ID = "test_op";
    private ResourceMgr rm;
    private SysOperation sysOp;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();
        SysOperationCard card = SysOperationCard.builder()
                .id(CARD_ID)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder()
                        .workDir(workDir.toString())
                        .shellAllowlist(null)
                        .build())
                .build();

        var addRes = rm.addSysOperation(card, null);
        assertTrue(addRes.isOk());
        sysOp = extractSysOperation(rm.getSysOperation(CARD_ID, null, TagMatchStrategy.ALL));
        assertNotNull(sysOp);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rm != null) {
            rm.removeSysOperation(CARD_ID, null, TagMatchStrategy.ALL, true);
        }
        Runner.stop();
    }

    @Test
    @Order(1)
    void testFsListToolsWithDict() {
        var tools = sysOp.fs().listTools();
        Map<String, ?> toolsByName = tools.stream().collect(java.util.stream.Collectors.toMap(
                tool -> tool.getName(), tool -> tool));

        assertEquals(10, tools.size());
        assertEquals(10, toolsByName.size());

        List<String> expectedNames = List.of(
                "readFile", "readFileStream", "writeFile", "uploadFile",
                "uploadFileStream", "downloadFile", "downloadFileStream",
                "listFiles", "listDirectories", "searchFiles");
        for (String name : expectedNames) {
            assertTrue(toolsByName.containsKey(name), "Missing tool: " + name);
        }

        var writeFileTool = tools.stream()
                .filter(tool -> "writeFile".equals(tool.getName()))
                .findFirst()
                .orElseThrow();
        assertNotNull(writeFileTool.getDescription());
        @SuppressWarnings("unchecked")
        Map<String, Object> writeProperties = (Map<String, Object>) writeFileTool.getInputParams().get("properties");
        assertTrue(writeProperties.containsKey("path"));
        assertTrue(writeProperties.containsKey("content"));
        assertEquals(List.of("path", "content"), writeFileTool.getInputParams().get("required"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contentSchema = (Map<String, Object>) writeProperties.get("content");
        assertTrue(contentSchema.containsKey("anyOf"));
        assertEquals(2, ((List<?>) contentSchema.get("anyOf")).size());

        var readFileTool = tools.stream()
                .filter(tool -> "readFile".equals(tool.getName()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> readProperties = (Map<String, Object>) readFileTool.getInputParams().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> modeProperty = (Map<String, Object>) readProperties.get("mode");
        assertTrue(((List<?>) modeProperty.get("enum")).contains("text"));
        assertTrue(((List<?>) modeProperty.get("enum")).contains("bytes"));
        assertTrue(readProperties.containsKey("path"));
        assertEquals(List.of("path"), readFileTool.getInputParams().get("required"));
    }

    @Test
    @Order(2)
    void testFsResourceMgrReadWriteText() throws Exception {
        String testFile = "integration_test.txt";
        String content = "resource mgr integration\nline 2\nline 3";
        sysOp.fs().writeFile(testFile, content, "text", false, false, true, null, "utf-8", null);

        Tool readFileTool = getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "readFile"));
        ReadFileResult read = (ReadFileResult) readFileTool.invoke(Map.of("path", testFile));
        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertEquals(content, read.getData().getContentAsString());

        ReadFileResult head = (ReadFileResult) readFileTool.invoke(Map.of("path", testFile, "mode", "text", "head", 2));
        assertEquals(StatusCode.SUCCESS.getCode(), head.getCode());
        assertTrue(head.getData().getContentAsString().contains("line 2"));
        assertFalse(head.getData().getContentAsString().contains("line 3"));

        Tool writeFileTool = getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "writeFile"));
        WriteFileResult write = (WriteFileResult) writeFileTool.invoke(
                Map.of("path", "write_test.txt", "content", "test write content"));
        assertEquals(StatusCode.SUCCESS.getCode(), write.getCode());

        ReadFileResult verify = (ReadFileResult) readFileTool.invoke(Map.of("path", "write_test.txt"));
        assertEquals(StatusCode.SUCCESS.getCode(), verify.getCode());
        assertTrue(verify.getData().getContentAsString().contains("test write content"));
    }

    @Test
    @Order(3)
    void testFsResourceMgrReadWriteBinary() throws Exception {
        Tool writeFileTool = getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "writeFile"));
        byte[] binaryContent = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, (byte) 0xff, (byte) 0xfe};
        WriteFileResult write = (WriteFileResult) writeFileTool.invoke(
                Map.of("path", "binary_test.bin", "content", binaryContent, "mode", "bytes"));
        assertEquals(StatusCode.SUCCESS.getCode(), write.getCode());

        Tool readFileTool = getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "readFile"));
        ReadFileResult read = (ReadFileResult) readFileTool.invoke(
                Map.of("path", "binary_test.bin", "mode", "bytes"));
        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertArrayEquals(binaryContent, read.getData().getContentAsBytes());
        assertEquals("bytes", read.getData().getMode());
    }

    @Test
    @Order(4)
    void testFsResourceMgrOtherMethods() throws Exception {
        sysOp.fs().writeFile("test_file1.txt", "test content 1", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("test_file2.txt", "test content 2", "text", false, false, true, null, "utf-8", null);

        Tool listFilesTool = getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "listFiles"));
        ListFilesResult list = (ListFilesResult) listFilesTool.invoke(Map.of("path", "."));
        assertEquals(StatusCode.SUCCESS.getCode(), list.getCode());
        assertTrue(list.getData().getListItems().size() > 0);

        ListFilesResult recursive = (ListFilesResult) listFilesTool.invoke(Map.of(
                "path", ".",
                "recursive", true,
                "maxDepth", 2,
                "sortBy", "name"));
        assertEquals(StatusCode.SUCCESS.getCode(), recursive.getCode());

        Tool searchFilesTool = getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "searchFiles"));
        SearchFilesResult search = (SearchFilesResult) searchFilesTool.invoke(Map.of("path", ".", "pattern", "*.txt"));
        assertEquals(StatusCode.SUCCESS.getCode(), search.getCode());
        Set<String> names = new HashSet<>();
        search.getData().getMatchingFiles().forEach(item -> names.add(item.getName()));
        assertTrue(names.contains("test_file1.txt"));
        assertTrue(names.contains("test_file2.txt"));
    }

    @Test
    @Order(5)
    void testShellResourceMgrIntegration() throws Exception {
        Tool shellTool = getTool(SysOperationCard.generateToolId(CARD_ID, "shell", "executeCmd"));
        ExecuteCmdResult result = (ExecuteCmdResult) shellTool.invoke(Map.of("command", "echo hello"));
        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains("hello"));
    }

    @Test
    @Order(6)
    void testCodeResourceMgrIntegration() throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping code execution test");
        Tool codeTool = getTool(SysOperationCard.generateToolId(CARD_ID, "code", "executeCode"));
        ExecuteCodeResult result = (ExecuteCodeResult) codeTool.invoke(Map.of("code", "print('hello from code')"));
        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("hello from code"));
    }

    @Test
    @Order(7)
    void testBatchSysOperationLifecycle() throws Exception {
        String secondId = "test_op_2";
        Path secondWorkDir = Files.createDirectory(workDir.resolve("second"));
        SysOperationCard secondCard = SysOperationCard.builder()
                .id(secondId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(secondWorkDir.toString()).build())
                .build();

        var addRes = rm.addSysOperation(secondCard, null);
        assertTrue(addRes.isOk());
        assertNotNull(rm.getSysOperation(CARD_ID, null, TagMatchStrategy.ALL));
        assertNotNull(rm.getSysOperation(secondId, null, TagMatchStrategy.ALL));

        Object removeResult = rm.removeSysOperation(List.of(CARD_ID, secondId), null, TagMatchStrategy.ALL, true);
        assertNotNull(removeResult);
    }

    private Tool getTool(String toolId) {
        Object tool = rm.getTool(toolId);
        assertInstanceOf(Tool.class, tool);
        return (Tool) tool;
    }

    private SysOperation extractSysOperation(Object result) {
        if (result instanceof SysOperation op) {
            return op;
        }
        if (result instanceof List<?> list && !list.isEmpty()) {
            return (SysOperation) list.get(0);
        }
        return null;
    }

    private static boolean isPythonAvailable() {
        try {
            Process process = new ProcessBuilder("python", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
