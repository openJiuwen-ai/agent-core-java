/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.mock;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.local.LocalShellOperation;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.sandbox.SandboxFsOperation;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for local providers under sandbox_type="local".
 *
 * <p>Mirrors Python's {@code test_local_provider.py}. Java routes the same behaviors
 * through the repository's local operation implementations and validates the
 * {@link SandboxRegistry} registration path explicitly.</p>
 */
@DisplayName("Local provider sandbox mock tests")
class TestLocalProvider {

    @TempDir
    Path workDir;

    private SysOperation localOp;

    @BeforeEach
    void setUp() {
        SysOperationCard card = new SysOperationCard();
        card.setId("local_sandbox_test");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(localConfig());
        localOp = new SysOperation(card);
    }

    @AfterEach
    void tearDown() {
        SandboxRegistry.unregisterProvider("local", "fs");
        SandboxRegistry.unregisterProvider("local", "shell");
        SandboxRegistry.unregisterProvider("local", "code");
        SandboxRegistry.unregisterProvider("aio", "fs");
    }

    @Test
    @DisplayName("local fs read file")
    void testLocalFsReadFile() {
        localOp.fs().writeFile("test.txt", "hello local fs", "text", false, false, true, null, "utf-8", null);

        var res = localOp.fs().readFile("test.txt", "text", null, null, null, "utf-8", 0, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("hello local fs", res.getData().getContent());
    }

    @Test
    @DisplayName("local fs write file")
    void testLocalFsWriteFile() {
        var write = localOp.fs().writeFile("write_test.txt", "hello", "text", false, false, true, null, "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), write.getCode());

        var read = localOp.fs().readFile("write_test.txt", "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertEquals("hello", read.getData().getContent());
    }

    @Test
    @DisplayName("local fs read file stream")
    void testLocalFsReadFileStream() {
        localOp.fs().writeFile("stream_test.txt", "line1\nline2", "text", false, false, true, null, "utf-8", null);

        List<ReadFileStreamResult> chunks = collect(localOp.fs().readFileStream(
                "stream_test.txt", "text", null, null, null, "utf-8", 16, null));

        assertEquals(2, chunks.size());
        assertEquals("line1\nline2", chunks.stream()
                .map(chunk -> chunk.getData().getChunkContentAsString())
                .reduce("", String::concat));
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
    }

    @Test
    @DisplayName("local fs list files")
    void testLocalFsListFiles() {
        localOp.fs().writeFile("file1.txt", "1", "text", false, false, true, null, "utf-8", null);
        localOp.fs().writeFile("dir1/file2.txt", "2", "text", false, false, true, null, "utf-8", null);

        var res = localOp.fs().listFiles(".", true, null, "name", false, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        var names = res.getData().getListItems().stream().map(item -> item.getName()).toList();
        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.txt"));
    }

    @Test
    @DisplayName("local fs list directories")
    void testLocalFsListDirectories() {
        localOp.fs().writeFile("dir1/file.txt", "1", "text", false, false, true, null, "utf-8", null);
        localOp.fs().writeFile("dir1/subdir/file2.txt", "2", "text", false, false, true, null, "utf-8", null);

        var res = localOp.fs().listDirectories(".", true, null, "name", false, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        var names = res.getData().getListItems().stream().map(item -> item.getName()).toList();
        assertTrue(names.contains("dir1"));
        assertTrue(names.contains("subdir"));
        assertTrue(res.getData().getListItems().stream().allMatch(item -> item.isDirectory()));
    }

    @Test
    @DisplayName("local fs search files")
    void testLocalFsSearchFiles() {
        localOp.fs().writeFile("matched.txt", "match", "text", false, false, true, null, "utf-8", null);
        localOp.fs().writeFile("nested/other.txt", "nested", "text", false, false, true, null, "utf-8", null);
        localOp.fs().writeFile("ignored.csv", "csv", "text", false, false, true, null, "utf-8", null);

        var res = localOp.fs().searchFiles(".", "*.txt", null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        var names = res.getData().getMatchingFiles().stream().map(item -> item.getName()).toList();
        assertTrue(names.contains("matched.txt"));
        assertTrue(names.contains("other.txt"));
    }

    @Test
    @DisplayName("local fs upload file")
    void testLocalFsUploadFile() throws Exception {
        Path localFile = Files.writeString(workDir.resolve("upload.txt"), "upload content");

        var upload = localOp.fs().uploadFile(localFile.toString(), "uploaded.txt", true, true, false, 0, null);
        var read = localOp.fs().readFile("uploaded.txt", "text", null, null, null, "utf-8", 0, null);

        assertEquals(StatusCode.SUCCESS.getCode(), upload.getCode());
        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertEquals("upload content", read.getData().getContent());
    }

    @Test
    @DisplayName("local fs download file stream")
    void testLocalFsDownloadFileStream() throws Exception {
        localOp.fs().writeFile("source.txt", "download me", "text", false, false, true, null, "utf-8", null);
        Path destination = workDir.resolve("dl_stream.txt");

        List<DownloadFileStreamResult> chunks = collect(localOp.fs().downloadFileStream(
                "source.txt", destination.toString(), true, true, false, 16, null));

        assertTrue(chunks.size() > 0);
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
        assertEquals("download me", Files.readString(destination));
    }

    @Test
    @DisplayName("local shell execute cmd")
    void testLocalShellExecuteCmd() {
        var res = localOp.shell().executeCmd("echo hello", null, 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertEquals("hello", res.getData().getStdout().trim());
    }

    @Test
    @DisplayName("local shell execute cmd stream")
    void testLocalShellExecuteCmdStream() {
        List<ExecuteCmdStreamResult> chunks = collect(localOp.shell().executeCmdStream("echo stream", null, 300, null, null));

        assertTrue(chunks.size() > 0);
        assertTrue(chunks.stream().map(chunk -> chunk.getData().getText()).filter(text -> text != null)
                .reduce("", String::concat).contains("stream"));
        assertEquals(0, chunks.get(chunks.size() - 1).getData().getExitCode());
    }

    @Test
    @DisplayName("local code execute")
    void testLocalCodeExecute() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");

        var res = localOp.code().executeCode("print(\"hello_local\")", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().contains("hello_local"));
    }

    @Test
    @DisplayName("local code execute stream")
    void testLocalCodeExecuteStream() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");

        List<ExecuteCodeStreamResult> chunks = collect(localOp.code().executeCodeStream(
                "print(\"line1\")\nprint(\"line2\")", "python", 300, null, null));

        assertTrue(chunks.size() >= 1);
        String text = chunks.stream().map(chunk -> chunk.getData().getText()).filter(value -> value != null)
                .reduce("", String::concat);
        assertTrue(text.contains("line1"));
        assertTrue(text.contains("line2"));
        assertEquals(0, chunks.get(chunks.size() - 1).getData().getExitCode());
    }

    @Test
    @DisplayName("local sandbox discovery")
    void testLocalSandboxDiscovery() {
        registerLocalProviders();

        assertEquals("LocalFsOperation", SandboxRegistry.getProviderSupplier("local", "fs").get().getClass().getSimpleName());
        assertEquals("LocalShellOperation", SandboxRegistry.getProviderSupplier("local", "shell").get().getClass().getSimpleName());
        assertEquals("LocalCodeOperation", SandboxRegistry.getProviderSupplier("local", "code").get().getClass().getSimpleName());
    }

    @Test
    @DisplayName("local and aio providers coexist")
    void testLocalAndAioProvidersCoexist() {
        registerLocalProviders();
        SandboxRegistry.registerProvider("aio", "fs", () -> new SandboxFsOperation(new SandboxGatewayConfig()));

        Supplier<Object> localFs = SandboxRegistry.getProviderSupplier("local", "fs");
        Supplier<Object> aioFs = SandboxRegistry.getProviderSupplier("aio", "fs");

        assertNotNull(localFs);
        assertNotNull(aioFs);
        assertNotSame(localFs.get().getClass(), aioFs.get().getClass());
        assertEquals("LocalFsOperation", localFs.get().getClass().getSimpleName());
        assertEquals("SandboxFsOperation", aioFs.get().getClass().getSimpleName());
    }

    private void registerLocalProviders() {
        SandboxRegistry.registerProvider("local", "fs", () -> new LocalFsOperation(localConfig()));
        SandboxRegistry.registerProvider("local", "shell", () -> new LocalShellOperation(localConfig()));
        SandboxRegistry.registerProvider("local", "code", () -> new LocalCodeOperation(localConfig()));
    }

    private LocalWorkConfig localConfig() {
        return LocalWorkConfig.builder()
                .workDir(workDir.toString())
                .shellAllowlist(null)
                .build();
    }

    private static <T> List<T> collect(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static boolean isPythonAvailable() {
        Process process = null;
        try {
            process = new ProcessBuilder("python", "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
