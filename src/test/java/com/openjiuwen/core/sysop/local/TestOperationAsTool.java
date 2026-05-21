/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Iterator;

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
    Path tempDir;

    private static final String CARD_ID = "test_op";
    private SysOperationCard card;
    private SysOperation sysOp;
    private ResourceMgr rm;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();

        card = SysOperationCard.builder()
                .id(CARD_ID)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder()
                        .workDir(tempDir.toString())
                        .build())
                .build();

        var addRes = rm.addSysOperation(card, null);
        assertTrue(addRes.isOk(), "Failed to add sys operation");

        Object result = rm.getSysOperation(CARD_ID, null, TagMatchStrategy.ALL);
        if (result instanceof SysOperation op) {
            sysOp = op;
        } else if (result instanceof List<?> list && !list.isEmpty()) {
            sysOp = (SysOperation) list.get(0);
        }
        assertNotNull(sysOp, "SysOperation should be retrieved");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rm != null && card != null) {
            rm.removeSysOperation(CARD_ID, null, TagMatchStrategy.ALL, true);
        }
        Runner.stop();
    }

    @Test
    @Order(1)
    void testFsListToolsWithDict() {
        /** Test list_tools for FS operation with dict conversion. */
        BaseFsOperation fs = sysOp.fs();
        List<ToolCard> tools = fs.listTools();

        Map<String, ToolCard> toolsDict = new HashMap<>();
        for (ToolCard tool : tools) {
            toolsDict.put(tool.getName(), tool);
        }

        assertEquals(10, tools.size());
        assertEquals(10, toolsDict.size());

        List<String> expectedNames = Arrays.asList(
                "readFile", "readFileStream", "writeFile",
                "uploadFile", "uploadFileStream",
                "downloadFile", "downloadFileStream",
                "listFiles", "listDirectories", "searchFiles"
        );
        for (String name : expectedNames) {
            assertTrue(toolsDict.containsKey(name), "Missing tool: " + name);
        }

        ToolCard writeFileTool = toolsDict.get("writeFile");
        assertNotNull(writeFileTool.getDescription());

        Map<String, Object> props = writeFileTool.getInputParams().get("properties");
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("content"));

        List<String> required = (List<String>) writeFileTool.getInputParams().get("required");
        assertTrue(required.contains("path"));
        assertTrue(required.contains("content"));

        Map<String, Object> contentSchema = (Map<String, Object>) props.get("content");
        assertTrue(contentSchema.containsKey("anyOf"));
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) contentSchema.get("anyOf");
        assertEquals(2, anyOf.size());

        Map<String, Object> stringSchema = anyOf.get(0);
        assertEquals("{\"type\": \"string\"}", stringSchema.toString());

        Map<String, Object> binarySchema = anyOf.get(1);
        assertTrue(binarySchema.containsKey("format"));
        assertEquals("binary", binarySchema.get("format"));

        ToolCard readFileTool = toolsDict.get("readFile");
        Map<String, Object> modeProperty = (Map<String, Object>) readFileTool.getInputParams().get("properties").get("mode");
        List<String> enumValues = (List<String>) modeProperty.get("enum");
        assertTrue(enumValues.contains("text"));
        assertTrue(enumValues.contains("bytes"));

        assertTrue(readFileTool.getInputParams().get("properties").containsKey("path"));
        List<String> readFileRequired = (List<String>) readFileTool.getInputParams().get("required");
        assertTrue(readFileRequired.contains("path"));
    }

    @Test
    @Order(2)
    void testFsResourceMgrReadWriteText() throws Exception {
        /** Test FS tools integration for text read/write operations. */
        String testFile = "integration_test.txt";
        String content = "resource mgr integration\nline 2\nline 3";

        sysOp.fs().writeFile(testFile, content, "text", false, false, true, "utf-8", null);

        String readToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "readFile");
        Tool readTool = getTool(readToolId);
        assertNotNull(readTool);
        assertEquals("readFile", readTool.getCard().getName());

        // Scenario 1: Only required parameter (path)
        Map<String, Object> inputs1 = new HashMap<>();
        inputs1.put("path", testFile);
        Object result1 = readTool.invoke(inputs1);
        if (result1 instanceof ReadFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertEquals(content, res.getData().getContentAsString());
        } else {
            fail("Expected ReadFileResult but got: " + result1.getClass());
        }

        // Scenario 2: Two parameters (path + mode)
        Map<String, Object> inputs2 = new HashMap<>();
        inputs2.put("path", testFile);
        inputs2.put("mode", "text");
        Object result2 = readTool.invoke(inputs2);
        if (result2 instanceof ReadFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertEquals(content, res.getData().getContentAsString());
        }

        // Scenario 3: Multiple parameters with head
        Map<String, Object> inputs3 = new HashMap<>();
        inputs3.put("path", testFile);
        inputs3.put("mode", "text");
        inputs3.put("head", 2);
        Object result3 = readTool.invoke(inputs3);
        if (result3 instanceof ReadFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            String resContent = res.getData().getContentAsString();
            assertTrue(resContent.contains("line 2"));
            assertFalse(resContent.contains("line 3"));
        }

        // Test write_file through resource_mgr
        String writeToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "writeFile");
        Tool writeTool = getTool(writeToolId);
        assertNotNull(writeTool);
        assertEquals("writeFile", writeTool.getCard().getName());

        String writeTestFile = "write_test.txt";
        String writeContent = "test write content";
        Map<String, Object> writeInputs = new HashMap<>();
        writeInputs.put("path", writeTestFile);
        writeInputs.put("content", writeContent);
        Object writeResult = writeTool.invoke(writeInputs);
        if (writeResult instanceof WriteFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        }

        // Verify file was written
        Map<String, Object> verifyInputs = new HashMap<>();
        verifyInputs.put("path", writeTestFile);
        Object verifyResult = readTool.invoke(verifyInputs);
        if (verifyResult instanceof ReadFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertTrue(res.getData().getContentAsString().contains(writeContent));
        }
    }

    @Test
    @Order(3)
    void testFsResourceMgrReadWriteBinary() throws Exception {
        /** Test FS tools integration for binary read/write operations. */
        String binaryTestFile = "binary_test.bin";
        byte[] binaryContent = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, (byte) 0xff, (byte) 0xfe};

        String writeToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "writeFile");
        Tool writeTool = getTool(writeToolId);
        assertNotNull(writeTool);

        Map<String, Object> writeInputs = new HashMap<>();
        writeInputs.put("path", binaryTestFile);
        writeInputs.put("content", binaryContent);
        writeInputs.put("mode", "bytes");
        Object writeResult = writeTool.invoke(writeInputs);
        if (writeResult instanceof WriteFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        }

        String readToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "readFile");
        Tool readTool = getTool(readToolId);
        assertNotNull(readTool);

        Map<String, Object> readInputs = new HashMap<>();
        readInputs.put("path", binaryTestFile);
        readInputs.put("mode", "bytes");
        Object readResult = readTool.invoke(readInputs);
        if (readResult instanceof ReadFileResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertArrayEquals(binaryContent, res.getData().getContentAsBytes());
            assertEquals("bytes", res.getData().getMode());
        }

        // Test read_file_stream with binary mode
        String streamToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "readFileStream");
        Tool streamTool = getTool(streamToolId);
        assertNotNull(streamTool);

        Map<String, Object> streamInputs = new HashMap<>();
        streamInputs.put("path", binaryTestFile);
        streamInputs.put("mode", "bytes");
        streamInputs.put("chunkSize", 2);
        Object streamResult = streamTool.invoke(streamInputs);

        List<byte[]> chunks = new ArrayList<>();
        if (streamResult instanceof Iterator<?> iter) {
            while (iter.hasNext()) {
                Object chunk = iter.next();
                if (chunk instanceof ReadFileStreamResult chunkRes) {
                    assertEquals(StatusCode.SUCCESS.getCode(), chunkRes.getCode());
                    chunks.add(chunkRes.getData().getChunkContentAsBytes());
                }
            }
        }

        // Verify concatenated chunks equal original content
        byte[] combined = new byte[chunks.stream().mapToInt(c -> c.length).sum()];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, combined, offset, chunk.length);
            offset += chunk.length;
        }
        assertArrayEquals(binaryContent, combined);
        assertEquals(4, chunks.size()); // 8 bytes / 2 bytes/chunk = 4 chunks
    }

    @Test
    @Order(4)
    void testFsResourceMgrOtherMethods() throws Exception {
        /** Test FS tools integration for other file system operations. */
        String testFile1 = "test_file1.txt";
        String testFile2 = "test_file2.txt";

        sysOp.fs().writeFile(testFile1, "test content 1", "text", false, false, true, "utf-8", null);
        sysOp.fs().writeFile(testFile2, "test content 2", "text", false, false, true, "utf-8", null);

        // Test list_files with different parameter counts
        String listFilesToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "listFiles");
        Tool listFilesTool = getTool(listFilesToolId);
        assertNotNull(listFilesTool);
        assertEquals("listFiles", listFilesTool.getCard().getName());

        // Scenario 1: Only required parameter (path)
        Map<String, Object> listInputs1 = new HashMap<>();
        listInputs1.put("path", ".");
        Object listResult1 = listFilesTool.invoke(listInputs1);
        if (listResult1 instanceof ListFilesResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertTrue(res.getData().getListItems().size() > 0);
        }

        // Scenario 2: Two parameters (path + recursive)
        Map<String, Object> listInputs2 = new HashMap<>();
        listInputs2.put("path", ".");
        listInputs2.put("recursive", true);
        Object listResult2 = listFilesTool.invoke(listInputs2);
        if (listResult2 instanceof ListFilesResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        }

        // Scenario 3: Multiple parameters
        Map<String, Object> listInputs3 = new HashMap<>();
        listInputs3.put("path", ".");
        listInputs3.put("recursive", true);
        listInputs3.put("maxDepth", 2);
        listInputs3.put("sortBy", "name");
        Object listResult3 = listFilesTool.invoke(listInputs3);
        if (listResult3 instanceof ListFilesResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        }

        // Test search_files with different parameter counts
        String searchFilesToolId = SysOperationCard.generateToolId(CARD_ID, "fs", "searchFiles");
        Tool searchFilesTool = getTool(searchFilesToolId);
        assertNotNull(searchFilesTool);
        assertEquals("searchFiles", searchFilesTool.getCard().getName());

        // Scenario: Required parameters only (path + pattern)
        Map<String, Object> searchInputs = new HashMap<>();
        searchInputs.put("path", ".");
        searchInputs.put("pattern", "*.txt");
        Object searchResult = searchFilesTool.invoke(searchInputs);
        if (searchResult instanceof SearchFilesResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        }
    }

    @Test
    @Order(5)
    void testShellResourceMgrIntegration() throws Exception {
        /** Test that Shell tools are automatically registered in ResourceMgr. */
        String toolId = SysOperationCard.generateToolId(CARD_ID, "shell", "executeCmd");
        Tool tool = getTool(toolId);
        assertNotNull(tool);
        assertEquals("executeCmd", tool.getCard().getName());

        // Verify tool execution through resource_mgr
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("command", "echo hello_integration");
        Map<String, Object> options = new HashMap<>();
        options.put("encoding", "utf-8");
        inputs.put("options", options);

        Object invokeResult = tool.invoke(inputs);
        if (invokeResult instanceof ExecuteCmdResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertTrue(res.getData().getStdout().contains("hello_integration"));
        }
    }

    @Test
    @Order(6)
    void testCodeResourceMgrIntegration() throws Exception {
        /** Test that Code tools are automatically registered in ResourceMgr. */
        String toolId = SysOperationCard.generateToolId(CARD_ID, "code", "executeCode");
        Tool tool = getTool(toolId);
        assertNotNull(tool);
        assertEquals("executeCode", tool.getCard().getName());

        // Verify tool execution through resource_mgr
        String code = "print('hello_integration')";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("code", code);
        inputs.put("language", "python");
        Map<String, Object> options = new HashMap<>();
        options.put("encoding", "utf-8");
        inputs.put("options", options);

        Object invokeResult = tool.invoke(inputs);
        if (invokeResult instanceof ExecuteCodeResult res) {
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
            assertTrue(res.getData().getStdout().contains("hello_integration"));
        }
    }

    @Test
    @Order(7)
    void testBatchSysOperationLifecycle() throws Exception {
        /** Test batch add, get and remove lifecycle for multiple sys operations. */
        ResourceMgr rm = Runner.resourceMgr();

        // 1. Create multiple sys operation cards
        SysOperationCard card1 = SysOperationCard.builder()
                .id("batch_op_1")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempDir.toString()).build())
                .build();
        SysOperationCard card2 = SysOperationCard.builder()
                .id("batch_op_2")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempDir.toString()).build())
                .build();
        SysOperationCard card3 = SysOperationCard.builder()
                .id("batch_op_3")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempDir.toString()).build())
                .build();

        // 2. Add multiple sys operations
        var res1 = rm.addSysOperation(card1, null);
        var res2 = rm.addSysOperation(card2, null);
        var res3 = rm.addSysOperation(card3, null);
        assertTrue(res1.isOk());
        assertTrue(res2.isOk());
        assertTrue(res3.isOk());

        // 3. Get multiple sys operations
        Object op1Result = rm.getSysOperation("batch_op_1", null, TagMatchStrategy.ALL);
        Object op2Result = rm.getSysOperation("batch_op_2", null, TagMatchStrategy.ALL);
        Object op3Result = rm.getSysOperation("batch_op_3", null, TagMatchStrategy.ALL);

        assertNotNull(extractSysOperation(op1Result));
        assertNotNull(extractSysOperation(op2Result));
        assertNotNull(extractSysOperation(op3Result));

        // Verify tools are registered for all
        assertNotNull(getTool(SysOperationCard.generateToolId(card1.getId(), "fs", "readFile")));
        assertNotNull(getTool(SysOperationCard.generateToolId(card2.getId(), "shell", "executeCmd")));
        assertNotNull(getTool(SysOperationCard.generateToolId(card3.getId(), "code", "executeCode")));

        // 4. Remove multiple sys operations
        rm.removeSysOperation("batch_op_1", null, TagMatchStrategy.ALL, true);
        rm.removeSysOperation("batch_op_2", null, TagMatchStrategy.ALL, true);

        // 5. Final verification
        // Operations 1 and 2 should be gone
        assertNull(extractSysOperation(rm.getSysOperation("batch_op_1", null, TagMatchStrategy.ALL)));
        assertNull(extractSysOperation(rm.getSysOperation("batch_op_2", null, TagMatchStrategy.ALL)));

        // Tools associated with removed operations should be gone
        assertNull(getTool(SysOperationCard.generateToolId(card1.getId(), "fs", "readFile")));
        assertNull(getTool(SysOperationCard.generateToolId(card2.getId(), "shell", "executeCmd")));

        // Operation 3 and its tools should still be there
        assertNotNull(extractSysOperation(rm.getSysOperation("batch_op_3", null, TagMatchStrategy.ALL)));
        assertNotNull(getTool(SysOperationCard.generateToolId(card3.getId(), "code", "executeCode")));

        // Cleanup remaining
        rm.removeSysOperation("batch_op_3", null, TagMatchStrategy.ALL, true);
        assertNull(extractSysOperation(rm.getSysOperation("batch_op_3", null, TagMatchStrategy.ALL)));
        assertNull(getTool(SysOperationCard.generateToolId(card3.getId(), "code", "executeCode")));
    }

    // Helper methods

    private Tool getTool(String toolId) {
        Object result = rm.getTool(toolId);
        if (result instanceof Tool tool) {
            return tool;
        } else if (result instanceof List<?> list && !list.isEmpty()) {
            return (Tool) list.get(0);
        }
        return null;
    }

    private SysOperation extractSysOperation(Object result) {
        if (result instanceof SysOperation op) {
            return op;
        } else if (result instanceof List<?> list && !list.isEmpty()) {
            return (SysOperation) list.get(0);
        }
        return null;
    }
}