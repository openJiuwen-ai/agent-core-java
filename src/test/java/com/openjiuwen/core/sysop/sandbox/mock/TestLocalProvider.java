/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.mock;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for local providers (sandbox_type="local").
 * <p>
 * Mirrors Python's {@code test_local_provider.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/mock/test_local_provider.py}.
 *
 * <p>Note: These tests verify the SandboxRegistry provider registration and routing
 * mechanism. In Java, sandbox infrastructure is currently a stub.
 * Tests are disabled until sandbox mode is fully implemented.
 */
@Disabled("Sandbox mode is not fully implemented in Java - tests will be enabled when ready")
class TestLocalProvider extends BaseLocalProviderTest {

    @TempDir
    Path tmpPath;

    @Test
    void testLocalFsReadFile() {
        /** Test fs.read_file through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("test.txt", "hello local fs", "text", false, false, true, null, "utf-8", null);

        ReadFileResult res = fs.readFile("test.txt", "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("hello local fs", res.getData().getContentAsString());
    }

    @Test
    void testLocalFsWriteFile() {
        /** Test fs.write_file through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        WriteFileResult res = fs.writeFile("write_test.txt", "hello", "text", false, false, true, null, "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        ReadFileResult readRes = fs.readFile("write_test.txt", "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), readRes.getCode());
        assertEquals("hello", readRes.getData().getContentAsString());
    }

    @Test
    void testLocalFsReadFileStream() {
        /** Test fs.read_file_stream through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("stream_test.txt", "line1\nline2", "text", false, false, true, null, "utf-8", null);

        List<ReadFileStreamResult> chunks = new ArrayList<>();
        Iterator<ReadFileStreamResult> iter = fs.readFileStream("stream_test.txt", "text", null, null, null, "utf-8", 16, null);
        while (iter.hasNext()) {
            chunks.add(iter.next());
        }

        assertTrue(chunks.size() >= 1);
        StringBuilder content = new StringBuilder();
        for (ReadFileStreamResult c : chunks) {
            content.append(c.getData().getChunkContentAsString());
        }
        assertEquals("line1\nline2", content.toString());
    }

    @Test
    void testLocalFsListFiles() {
        /** Test fs.list_files through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("file1.txt", "1", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("dir1/file2.txt", "2", "text", false, false, true, null, "utf-8", null);

        ListFilesResult res = fs.listFiles(".", true, null, null, false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        Set<String> names = new HashSet<>();
        for (FileSystemItem item : res.getData().getListItems()) {
            names.add(item.getName());
        }
        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.txt"));
    }

    @Test
    void testLocalFsListDirectories() {
        /** Test fs.list_directories through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("dir1/file.txt", "1", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("dir1/subdir/file2.txt", "2", "text", false, false, true, null, "utf-8", null);

        ListDirsResult res = fs.listDirectories(".", true, null, null, false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        Set<String> names = new HashSet<>();
        for (FileSystemItem item : res.getData().getListItems()) {
            names.add(item.getName());
            assertTrue(item.isDirectory());
        }
        assertTrue(names.contains("dir1"));
        assertTrue(names.contains("subdir"));
    }

    @Test
    void testLocalFsSearchFiles() {
        /** Test fs.search_files through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("matched.txt", "match", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("nested/other.txt", "nested", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("ignored.csv", "csv", "text", false, false, true, null, "utf-8", null);

        SearchFilesResult res = fs.searchFiles(".", "*.txt", null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        Set<String> names = new HashSet<>();
        for (FileSystemItem item : res.getData().getMatchingFiles()) {
            names.add(item.getName());
        }
        assertTrue(names.contains("matched.txt"));
        assertTrue(names.contains("other.txt"));
    }

    @Test
    void testLocalFsUploadFile() throws Exception {
        /** Test fs.upload_file through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        Path localFile = tmpPath.resolve("upload.txt");
        Files.writeString(localFile, "upload content");

        UploadFileResult res = fs.uploadFile(localFile.toString(), "uploaded.txt", true, true, false, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        ReadFileResult readRes = fs.readFile("uploaded.txt", "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), readRes.getCode());
        assertEquals("upload content", readRes.getData().getContentAsString());
    }

    @Test
    void testLocalFsDownloadFileStream() throws Exception {
        /** Test fs.download_file_stream through the local provider. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("source.txt", "download me", "text", false, false, true, null, "utf-8", null);

        Path localDst = tmpPath.resolve("dl_stream.txt");
        List<DownloadFileStreamResult> chunks = new ArrayList<>();
        Iterator<DownloadFileStreamResult> iter = fs.downloadFileStream("source.txt", localDst.toString(), true, true, false, 16, null);
        while (iter.hasNext()) {
            chunks.add(iter.next());
        }

        assertTrue(chunks.size() > 0);
        assertEquals("download me", Files.readString(localDst));
    }

    @Test
    void testLocalShellExecuteCmd() {
        /** Test shell.execute_cmd through the local provider. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();
        ExecuteCmdResult res = shell.executeCmd("echo hello", null, 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().contains("hello"));
    }

    @Test
    void testLocalShellExecuteCmdStream() {
        /** Test shell.execute_cmd_stream through the local provider. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();
        List<ExecuteCmdStreamResult> chunks = new ArrayList<>();
        Iterator<ExecuteCmdStreamResult> iter = shell.executeCmdStream("echo stream", null, 300, null, null);
        while (iter.hasNext()) {
            chunks.add(iter.next());
        }

        assertTrue(chunks.size() > 0);
        StringBuilder content = new StringBuilder();
        for (ExecuteCmdStreamResult c : chunks) {
            if (c.getData() != null && c.getData().getText() != null) {
                content.append(c.getData().getText());
            }
        }
        assertTrue(content.toString().contains("stream"));
        assertEquals(0, chunks.get(chunks.size() - 1).getData().getExitCode());
    }

    @Test
    void testLocalCodeExecute() {
        /** Test code.execute_code through the local provider. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        ExecuteCodeResult res = code.executeCode("print(\"hello_local\")", "python", null, null, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().contains("hello_local"));
    }

    @Test
    void testLocalCodeExecuteStream() {
        /** Test code.execute_code_stream through the local provider. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        List<ExecuteCodeStreamResult> chunks = new ArrayList<>();
        Iterator<ExecuteCodeStreamResult> iter = code.executeCodeStream("print(\"line1\")\nprint(\"line2\")", "python", null, null, null, null);
        while (iter.hasNext()) {
            chunks.add(iter.next());
        }

        assertTrue(chunks.size() >= 1);
        StringBuilder content = new StringBuilder();
        for (ExecuteCodeStreamResult c : chunks) {
            if (c.getData() != null && c.getData().getText() != null) {
                content.append(c.getData().getText());
            }
        }
        assertTrue(content.toString().contains("line1"));
        assertTrue(content.toString().contains("line2"));
        assertEquals(0, chunks.get(chunks.size() - 1).getData().getExitCode());
    }

    // Helper method

    private void assumeSandboxImplemented() {
        Assumptions.assumeTrue(sysOp != null, "Sandbox mode is not fully implemented");
    }
}