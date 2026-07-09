/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tests for sandbox fallback operations.
 */
class SandboxOperationTest {
    @TempDir
    Path tempDir;

    private SandboxGatewayConfig sandboxConfig() {
        return SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy")
                        .baseUrl("http://local-provider:9999").sandboxType("local").build())
                .params(Map.of("root_path", tempDir.toString(), "shell_allowlist",
                        List.of("pwd", "echo", "python3", "python", "sleep")))
                .build();
    }

    @Test
    @DisplayName("SandboxCodeOperation.executeCode runs with sandbox root as cwd")
    void testSandboxCodeExecutes() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxCodeOperation op = new SandboxCodeOperation(sandboxConfig());
        var result = op.executeCode("import os\nprint(os.getcwd())", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().getStdout().contains(tempDir.toString()));
    }

    @Test
    @DisplayName("SandboxCodeOperation.executeCodeStream yields output chunks")
    void testSandboxCodeStreamExecutes() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxCodeOperation op = new SandboxCodeOperation(sandboxConfig());
        Iterator<?> stream = op.executeCodeStream("print('sandbox')", "python", 300, null, null);

        assertTrue(stream.hasNext());
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmd enforces root cwd")
    void testSandboxShellExecutesInsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        var result = op.executeCmd("pwd", ".", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains(tempDir.toString()));
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmdBackground returns pid inside root")
    void testSandboxShellBackgroundExecutesInsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        ExecuteCmdBackgroundResult result = op.executeCmdBackground("sleep 2", ".", null, 0.1, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getPid());
        ProcessHandle.of(result.getData().getPid()).ifPresent(handle -> {
            handle.destroy();
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
    }

    @Test
    @DisplayName("SandboxShellOperation rejects cwd outside root")
    void testSandboxShellRejectsOutsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        var result = op.executeCmd("pwd", "/tmp", 300, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmdStream returns structured error for outside cwd")
    void testSandboxShellStreamRejectsOutsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        Iterator<ExecuteCmdStreamResult> results = op.executeCmdStream("pwd", "/tmp", 300, null, null);

        assertTrue(results.hasNext());
        ExecuteCmdStreamResult item = results.next();
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), item.getCode());
    }

    @Test
    @DisplayName("SandboxFsOperation reads and writes within sandbox root")
    void testSandboxFsReadWrite() throws Exception {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxFsOperation op = new SandboxFsOperation(sandboxConfig());
        Files.writeString(tempDir.resolve("note.txt"), "hello sandbox");

        var read = op.readFile("note.txt", "text", null, null, null, "utf-8", 0, null);
        var write = op.writeFile("nested/out.txt", "payload", "text", false, false, true, "644", "utf-8", null);

        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertEquals("hello sandbox", read.getData().getContentAsString());
        assertEquals(StatusCode.SUCCESS.getCode(), write.getCode());
        assertTrue(Files.exists(tempDir.resolve("nested/out.txt")));
    }

    @Test
    @DisplayName("SandboxFsOperation blocks traversal outside sandbox root")
    void testSandboxFsRejectsTraversal() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxFsOperation op = new SandboxFsOperation(sandboxConfig());
        var result = op.readFile("../escape.txt", "text", null, null, null, "utf-8", 0, null);

        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("SandboxFsOperation stream and search delegate to local implementation")
    void testSandboxFsStreamAndSearch() throws Exception {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxFsOperation op = new SandboxFsOperation(sandboxConfig());
        Files.writeString(tempDir.resolve("search.txt"), "sandbox search");
        Iterator<ReadFileStreamResult> stream =
            op.readFileStream("search.txt", "text", null, null, null, "utf-8", 4, null);
        var search = op.searchFiles(".", "search*", null);

        assertTrue(stream.hasNext());
        assertEquals(StatusCode.SUCCESS.getCode(), search.getCode());
        assertFalse(search.getData().getMatchingFiles().isEmpty());
    }
}
