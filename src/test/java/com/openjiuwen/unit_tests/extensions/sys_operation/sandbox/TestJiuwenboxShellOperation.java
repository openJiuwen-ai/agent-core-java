/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/sys_operation/sandbox/test_jiuwenbox_shell_operation.py}.
 */
class TestJiuwenboxShellOperation extends SandboxExtensionTestSupport {

    private SysOperation newSysOp() {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_JIUWENBOX_TEST")),
                "Requires running Jiuwenbox sandbox"
        );
        return newJiuwenboxSysOp();
    }

    @Test
    void testShellBasicExecution() {
        SysOperation sysOp = newSysOp();

        var echo = sysOp.shell().executeCmd("echo hello world", null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), echo.getCode());
        assertNotNull(echo.getData());
        assertTrue(echo.getData().getStdout().contains("hello world"));
        assertEquals(0, echo.getData().getExitCode());
        assertEquals("echo hello world", echo.getData().getCommand());

        var ls = sysOp.shell().executeCmd("ls -la /", null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), ls.getCode());
        assertNotNull(ls.getData());
        assertFalse(ls.getData().getStdout().isBlank());
        assertEquals(0, ls.getData().getExitCode());
    }

    @Test
    void testShellEnvironmentVariables() {
        var res = newSysOp().shell().executeCmd("echo $TEST_VAR", null, 300, java.util.Map.of("TEST_VAR", "custom_value"), null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().contains("custom_value"));
    }

    @Test
    void testShellCwd() {
        SysOperation sysOp = newSysOp();
        sysOp.shell().executeCmd("mkdir -p /tmp/jiuwenbox_shell_cwd/subdir", null, 300, null, null);
        var res = sysOp.shell().executeCmd("pwd", "/tmp/jiuwenbox_shell_cwd/subdir", 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("/tmp/jiuwenbox_shell_cwd/subdir", res.getData().getStdout().trim());
    }

    @Test
    void testShellTimeout() {
        var res = newSysOp().shell().executeCmd("python -c \"import time; time.sleep(5)\"", null, 1, null, null);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
    }

    @Test
    void testShellPingTimeout() {
        var res = newSysOp().shell().executeCmd(
                "for i in 1 2 3 4 5 6 7 8 9 10; do echo 127.0.0.1; sleep 1; done",
                null,
                1,
                null,
                null
        );
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().contains("127.0.0.1"));
    }

    @Test
    void testShellListTools() {
        var tools = newSysOp().shell().listTools();
        assertEquals(3, tools.size());
        var toolNames = tools.stream().map(tool -> tool.getName()).toList();
        assertTrue(toolNames.contains("executeCmd"));
        assertTrue(toolNames.contains("executeCmdStream"));
        assertTrue(toolNames.contains("executeCmdBackground"));

        var execTool = tools.stream().filter(tool -> "executeCmd".equals(tool.getName())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) execTool.getInputParams().get("properties");
        assertTrue(properties.containsKey("command"));
        assertEquals(List.of("command"), execTool.getInputParams().get("required"));
    }

    @Test
    void testExecuteCmdStreamBasic() {
        List<ExecuteCmdStreamResult> results = collect(newSysOp().shell().executeCmdStream(
                "echo chunk1; sleep 0.01; echo chunk2; sleep 0.01; echo error_chunk 1>&2",
                null,
                300,
                null,
                null
        ));

        assertFalse(results.isEmpty());
        String stdout = results.stream()
                .filter(result -> "stdout".equals(result.getData().getType()))
                .map(result -> result.getData().getText())
                .reduce("", String::concat);
        String stderr = results.stream()
                .filter(result -> "stderr".equals(result.getData().getType()))
                .map(result -> result.getData().getText())
                .reduce("", String::concat);
        assertTrue(stdout.contains("chunk1"));
        assertTrue(stdout.contains("chunk2"));
        assertTrue(stderr.contains("error_chunk"));
        assertEquals(0, results.getLast().getData().getExitCode());
        assertEquals(results.size() - 1, results.getLast().getData().getChunkIndex());
    }

    @Test
    void testExecuteCmdStreamTimeout() {
        List<ExecuteCmdStreamResult> results = collect(
                newSysOp().shell().executeCmdStream("sleep 10", null, 1, null, null)
        );

        ExecuteCmdStreamResult error = results.stream()
                .filter(result -> result.getCode() == StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode())
                .findFirst()
                .orElse(null);
        assertNotNull(error);
        assertTrue(error.getMessage().toLowerCase().contains("timeout"));
        assertEquals(-1, error.getData().getExitCode());
    }

    @Test
    void testExecuteCmdStreamEmptyCommand() {
        List<ExecuteCmdStreamResult> results = collect(
                newSysOp().shell().executeCmdStream("", null, 300, null, null)
        );

        assertEquals(1, results.size());
        ExecuteCmdStreamResult error = results.getFirst();
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("command can not be empty"));
        assertEquals(0, error.getData().getChunkIndex());
        assertEquals(-1, error.getData().getExitCode());
    }

    @Test
    void testExecuteCmdStreamContinuousOutput() {
        List<ExecuteCmdStreamResult> results = collect(
                newSysOp().shell().executeCmdStream(
                        "for i in 1 2 3; do echo 127.0.0.1; sleep 0.1; done",
                        null,
                        10,
                        null,
                        null
                )
        );

        String stdout = results.stream()
                .filter(result -> "stdout".equals(result.getData().getType()))
                .map(result -> result.getData().getText())
                .reduce("", String::concat);
        assertTrue(stdout.contains("127.0.0.1"));
        assertEquals(0, results.getLast().getData().getExitCode());
    }
}
