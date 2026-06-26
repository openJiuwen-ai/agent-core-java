/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.resourcemanager.Result;
import com.openjiuwen.core.sys_operation.BaseFsOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.result.WriteFileResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/core/sys_operation/local/test_operation_as_tool.py}.
 */
class OperationAsToolMissingTest {

    private static final String CARD_ID = "test_op";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanup() {
        Runner.resourceMgr.removeSysOperation(CARD_ID);
        Runner.resourceMgr.removeSysOperation("batch_op_1");
        Runner.resourceMgr.removeSysOperation("batch_op_2");
        Runner.resourceMgr.removeSysOperation("batch_op_3");
        Cwd.clear();
    }

    @Test
    void fsListToolsWithDictConversion() {
        SysOperation sysOperation = newSysOperation(CARD_ID);

        List<ToolCard> tools = sysOperation.fs().listTools();
        Map<String, ToolCard> byName = toToolMap(tools);

        assertThat(tools).hasSize(10);
        assertThat(byName).hasSize(10);
        assertThat(byName.keySet()).containsExactlyInAnyOrder(
                "read_file",
                "read_file_stream",
                "write_file",
                "upload_file",
                "upload_file_stream",
                "download_file",
                "download_file_stream",
                "list_files",
                "list_directories",
                "search_files"
        );

        ToolCard writeFileTool = byName.get("write_file");
        assertThat(writeFileTool.getDescription()).isNotNull();
        Map<String, Object> writeProperties = properties(writeFileTool);
        assertThat(writeProperties).containsKeys("path", "content");
        assertThat(required(writeFileTool)).contains("path", "content");
        assertThat(writeProperties.get("content").toString())
                .contains("string")
                .containsAnyOf("binary", "byte", "content");

        ToolCard readFileTool = byName.get("read_file");
        Map<String, Object> readProperties = properties(readFileTool);
        assertThat(readProperties).containsKeys("path", "mode");
        assertThat(readProperties.get("mode").toString()).containsAnyOf("text", "TEXT").containsAnyOf("bytes", "BYTES");
        assertThat(required(readFileTool)).contains("path");
    }

    @Test
    void fsResourceMgrReadWriteText() throws Exception {
        Fixture fixture = startFixture(CARD_ID);
        Tool readFileTool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "read_file"));
        Tool writeFileTool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "write_file"));

        assertThat(readFileTool).isNotNull();
        assertThat(readFileTool.getCard().getName()).isEqualTo("read_file");
        assertThat(writeFileTool).isNotNull();
        assertThat(writeFileTool.getCard().getName()).isEqualTo("write_file");

        String testFile = "integration_test.txt";
        String content = "resource mgr integration\nline 2\nline 3";
        fixture.sysOperation().fs().writeFile(
                testFile,
                content,
                BaseFsOperation.FileMode.TEXT,
                false,
                false,
                false,
                true,
                null,
                null,
                null).get(10, TimeUnit.SECONDS);

        ReadFileResult requiredOnly = (ReadFileResult) readFileTool.invoke(Map.of("path", testFile));
        assertThat(requiredOnly.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(requiredOnly.getData().getContent()).isEqualTo(content);

        ReadFileResult withMode = (ReadFileResult) readFileTool.invoke(Map.of(
                "path", testFile,
                "mode", "text"));
        assertThat(withMode.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(withMode.getData().getContent()).isEqualTo(content);

        ReadFileResult withHead = (ReadFileResult) readFileTool.invoke(Map.of(
                "path", testFile,
                "mode", "text",
                "head", 2));
        assertThat(withHead.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(String.valueOf(withHead.getData().getContent())).contains("line 2").doesNotContain("line 3");

        WriteFileResult writeResult = (WriteFileResult) writeFileTool.invoke(Map.of(
                "path", "write_test.txt",
                "content", "test write content",
                "mode", "text",
                "createIfNotExist", true));
        assertThat(writeResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        ReadFileResult verifyResult = (ReadFileResult) readFileTool.invoke(Map.of("path", "write_test.txt"));
        assertThat(verifyResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(String.valueOf(verifyResult.getData().getContent())).contains("test write content");
    }

    @Test
    void fsResourceMgrReadWriteBinary() throws Exception {
        Fixture fixture = startFixture(CARD_ID);
        byte[] binaryContent = new byte[] {0, 1, 2, 3, 4, 5, (byte) 0xFF, (byte) 0xFE};

        WriteFileResult writeResult = fixture.sysOperation().fs().writeFile(
                "binary_test.bin",
                binaryContent,
                BaseFsOperation.FileMode.BYTES,
                false,
                false,
                false,
                true,
                null,
                null,
                null).get(10, TimeUnit.SECONDS);
        assertThat(writeResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        Tool readFileTool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "read_file"));
        ReadFileResult readResult = (ReadFileResult) readFileTool.invoke(Map.of(
                "path", "binary_test.bin",
                "mode", "bytes"));
        assertThat(readResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(readResult.getData().getContent()).isEqualTo(binaryContent);
        assertThat(readResult.getData().getMode()).isEqualTo("bytes");

        Tool streamTool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "read_file_stream"));
        @SuppressWarnings("unchecked")
        Iterator<Object> chunks = streamTool.stream(Map.of(
                "path", "binary_test.bin",
                "mode", "bytes",
                "chunkSize", 2));
        List<byte[]> chunkContents = new ArrayList<>();
        while (chunks.hasNext()) {
            ReadFileStreamResult chunk = (ReadFileStreamResult) chunks.next();
            assertThat(chunk.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
            chunkContents.add((byte[]) chunk.getData().getChunkContent());
        }
        assertThat(concat(chunkContents)).isEqualTo(binaryContent);
        assertThat(chunkContents).hasSize(4);
    }

    @Test
    void fsResourceMgrOtherMethods() throws Exception {
        Fixture fixture = startFixture(CARD_ID);
        fixture.sysOperation().fs().writeFile(
                "test_file1.txt",
                "test content 1",
                BaseFsOperation.FileMode.TEXT,
                false,
                false,
                false,
                true,
                null,
                null,
                null).get(10, TimeUnit.SECONDS);
        fixture.sysOperation().fs().writeFile(
                "test_file2.txt",
                "test content 2",
                BaseFsOperation.FileMode.TEXT,
                false,
                false,
                false,
                true,
                null,
                null,
                null).get(10, TimeUnit.SECONDS);

        Tool listFilesTool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "list_files"));
        assertThat(listFilesTool).isNotNull();
        assertThat(listFilesTool.getCard().getName()).isEqualTo("list_files");

        ListFilesResult requiredOnly = (ListFilesResult) listFilesTool.invoke(Map.of("path", "."));
        assertThat(requiredOnly.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(requiredOnly.getData().getListItems()).isNotEmpty();

        ListFilesResult withRecursive = (ListFilesResult) listFilesTool.invoke(Map.of(
                "path", ".",
                "recursive", true));
        assertThat(withRecursive.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        ListFilesResult withMultiple = (ListFilesResult) listFilesTool.invoke(Map.of(
                "path", ".",
                "recursive", true,
                "maxDepth", 2,
                "sortBy", "name"));
        assertThat(withMultiple.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        Tool searchFilesTool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "fs", "search_files"));
        assertThat(searchFilesTool).isNotNull();
        assertThat(searchFilesTool.getCard().getName()).isEqualTo("search_files");
        SearchFilesResult searchResult = (SearchFilesResult) searchFilesTool.invoke(Map.of(
                "path", ".",
                "pattern", "*.txt"));
        assertThat(searchResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
    }

    @Test
    void shellResourceMgrIntegration() throws Exception {
        startFixture(CARD_ID);
        Tool tool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "shell", "execute_cmd"));

        assertThat(tool).isNotNull();
        assertThat(tool.getCard().getName()).isEqualTo("execute_cmd");

        ExecuteCmdResult result = (ExecuteCmdResult) tool.invoke(Map.of(
                "command", "echo hello_integration",
                "options", Map.of("encoding", "utf-8")));

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getStdout()).contains("hello_integration");
    }

    @Test
    void codeResourceMgrIntegration() throws Exception {
        startFixture(CARD_ID);
        Tool tool = Runner.resourceMgr.getTool(SysOperationCard.generateToolId(CARD_ID, "code", "execute_code"));

        assertThat(tool).isNotNull();
        assertThat(tool.getCard().getName()).isEqualTo("execute_code");

        ExecuteCodeResult result = (ExecuteCodeResult) tool.invoke(Map.of(
                "code", "print('hello_integration')",
                "language", "python",
                "options", Map.of("encoding", "utf-8")));

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getStdout()).contains("hello_integration");
    }

    @Test
    void batchSysOperationLifecycle() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        Runner.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
        ResourceMgr resourceMgr = Runner.resourceMgr;

        SysOperationCard card1 = card("batch_op_1");
        SysOperationCard card2 = card("batch_op_2");
        SysOperationCard card3 = card("batch_op_3");

        List<Result<?, ?>> addResults = resourceMgr.addSysOperations(List.of(card1, card2, card3), null);
        assertThat(addResults).hasSize(3).allSatisfy(result -> assertThat(result.isOk()).isTrue());

        List<SysOperation> operations = List.of(
                resourceMgr.getSysOperation("batch_op_1"),
                resourceMgr.getSysOperation("batch_op_2"),
                resourceMgr.getSysOperation("batch_op_3"));
        assertThat(operations).hasSize(3).allSatisfy(operation -> assertThat(operation).isNotNull());

        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card1.getId(), "fs", "read_file"))).isNotNull();
        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card2.getId(), "shell", "execute_cmd"))).isNotNull();
        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card3.getId(), "code", "execute_code"))).isNotNull();

        List<Result<?, ?>> removeResults = List.of(
                resourceMgr.removeSysOperation("batch_op_1"),
                resourceMgr.removeSysOperation("batch_op_2"));
        assertThat(removeResults).hasSize(2).allSatisfy(result -> assertThat(result.isOk()).isTrue());

        assertThat(resourceMgr.getSysOperation("batch_op_1")).isNull();
        assertThat(resourceMgr.getSysOperation("batch_op_2")).isNull();
        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card1.getId(), "fs", "read_file"))).isNull();
        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card2.getId(), "shell", "execute_cmd"))).isNull();

        assertThat(resourceMgr.getSysOperation("batch_op_3")).isNotNull();
        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card3.getId(), "code", "execute_code"))).isNotNull();

        resourceMgr.removeSysOperation("batch_op_3");
        assertThat(resourceMgr.getSysOperation("batch_op_3")).isNull();
        assertThat(resourceMgr.getTool(SysOperationCard.generateToolId(card3.getId(), "code", "execute_code"))).isNull();
    }

    private Fixture startFixture(String cardId) throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        Runner.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
        SysOperationCard card = card(cardId);
        Result<?, ?> addResult = Runner.resourceMgr.addSysOperation(card);
        assertThat(addResult.isOk()).isTrue();
        SysOperation sysOperation = Runner.resourceMgr.getSysOperation(cardId);
        assertThat(sysOperation).isNotNull();
        return new Fixture(card, sysOperation);
    }

    private SysOperation newSysOperation(String cardId) {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        return new SysOperation(card(cardId));
    }

    private SysOperationCard card(String cardId) {
        return new SysOperationCard(cardId, OperationMode.LOCAL, new LocalWorkConfig());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(ToolCard toolCard) {
        return (Map<String, Object>) toolCard.getInputParams().get("properties");
    }

    @SuppressWarnings("unchecked")
    private List<String> required(ToolCard toolCard) {
        Object required = toolCard.getInputParams().get("required");
        return required instanceof List<?> values ? (List<String>) values : List.of();
    }

    private Map<String, ToolCard> toToolMap(List<ToolCard> tools) {
        Map<String, ToolCard> byName = new LinkedHashMap<>();
        for (ToolCard tool : tools) {
            byName.put(tool.getName(), tool);
        }
        return byName;
    }

    private byte[] concat(List<byte[]> chunks) {
        int size = chunks.stream().mapToInt(chunk -> chunk.length).sum();
        byte[] joined = new byte[size];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, joined, offset, chunk.length);
            offset += chunk.length;
        }
        return joined;
    }

    private record Fixture(SysOperationCard card, SysOperation sysOperation) {
    }
}
