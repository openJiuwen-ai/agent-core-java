/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.testsupport.OsTestSupport;
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

    private static boolean isWindows() {
        return OsTestSupport.isWindows();
    }

    private SandboxGatewayConfig sandboxConfig() {
        List<String> allowlist = isWindows()
                ? List.of("cd", "echo", "python", "python3", "ping", "timeout", "cmd")
                : List.of("pwd", "echo", "python3", "python", "sleep");
        return SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy")
                        .baseUrl("http://local-provider:9999").sandboxType("local").build())
                .params(Map.of("root_path", tempDir.toString(), "shell_allowlist", allowlist))
                .build();
    }

    @Test
    @DisplayName("SandboxCodeOperation.executeCode runs with sandbox root as cwd")
    void testSandboxCodeExecutes() {
        OsTestSupport.assumePythonAvailable();
        SandboxTestLocalProviders.ensureRegistered();
        SandboxCodeOperation op = new SandboxCodeOperation(sandboxConfig());
        var result = op.executeCode("import os\nprint(os.getcwd())", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertTrue(OsTestSupport.pathContains(result.getData().getStdout(), tempDir));
    }

    @Test
    @DisplayName("SandboxCodeOperation.executeCodeStream yields output chunks")
    void testSandboxCodeStreamExecutes() {
        OsTestSupport.assumePythonAvailable();
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
        String cmd = OsTestSupport.cwdCommand();
        var result = op.executeCmd(cmd, ".", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(OsTestSupport.pathContains(result.getData().getStdout(), tempDir),
                () -> "stdout=" + result.getData().getStdout());
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmdBackground returns pid inside root")
    void testSandboxShellBackgroundExecutesInsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        String cmd = OsTestSupport.shortBackgroundWaitCommand();
        ExecuteCmdBackgroundResult result = op.executeCmdBackground(cmd, ".", null, 0, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getPid());
        OsTestSupport.destroyProcessTree(result.getData().getPid());
    }

    @Test
    @DisplayName("SandboxShellOperation rejects cwd outside root")
    void testSandboxShellRejectsOutsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        String outsideCwd = tempDir.getParent().resolve("outside-cwd-" + System.nanoTime()).toAbsolutePath().toString();
        var result = op.executeCmd(OsTestSupport.cwdCommand(), outsideCwd, 300, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmdStream returns structured error for outside cwd")
    void testSandboxShellStreamRejectsOutsideRoot() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxShellOperation op = new SandboxShellOperation(sandboxConfig());
        String outsideCwd = tempDir.getParent().resolve("outside-stream-" + System.nanoTime()).toAbsolutePath().toString();
        Iterator<ExecuteCmdStreamResult> results =
                op.executeCmdStream(OsTestSupport.cwdCommand(), outsideCwd, 300, null, null);

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
        Files.writeString(tempDir.resolve("out.txt"), "");

        var read = op.readFile("note.txt", "text", null, null, null, "utf-8", 0, null);
        var write = op.writeFile("out.txt", "payload", "text", false, false, false, null, "utf-8", null);

        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode(), read.getMessage());
        assertEquals("hello sandbox", read.getData().getContentAsString());
        assertEquals(StatusCode.SUCCESS.getCode(), write.getCode(), write.getMessage());
        assertTrue(Files.exists(tempDir.resolve("out.txt")));
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
