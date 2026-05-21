/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test shell operations through sandbox routing.
 * <p>
 * Mirrors Python's {@code test_shell.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_shell.py}.
 *
 * <p>Note: Sandbox mode is stubbed in Java - tests are disabled until implemented.
 */
@Disabled("Sandbox mode is not fully implemented in Java")
class TestShell extends BaseSandboxTest {

    @Test
    void testShellBasicExecution() {
        /** Test basic shell commands through sandbox routing. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();
        ExecuteCmdResult res = shell.executeCmd("echo hello world", null, 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().strip().contains("hello world"));
        assertEquals(0, res.getData().getExitCode());
        assertEquals("echo hello world", res.getData().getCommand());
    }

    @Test
    void testShellEnvironmentVariables() {
        /** Test environment variable injection for shell execution. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();

        Map<String, String> env = new HashMap<>();
        env.put("TEST_VAR", "custom_value");

        ExecuteCmdResult res = shell.executeCmd("echo $TEST_VAR", null, 300, env, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().strip().contains("custom_value"));
    }

    @Test
    void testShellCwd() {
        /** Test explicit cwd routing for shell execution. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();
        ExecuteCmdResult res = shell.executeCmd("pwd", "/tmp/subdir", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().strip().contains("/tmp/subdir"));
    }

    @Test
    void testShellTimeout() {
        /** Test command timeout behavior. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();
        ExecuteCmdResult res = shell.executeCmd("python -c \"import time; time.sleep(5)\"", null, 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(res.getData());
        assertEquals(-1, res.getData().getExitCode());
    }

    @Test
    void testShellPingTimeout() {
        /** Test timeout behavior for a continuous-output style command. */
        assumeSandboxImplemented();

        BaseShellOperation shell = sysOp.shell();
        ExecuteCmdResult res = shell.executeCmd("ping 127.0.0.1", null, 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().contains("127.0.0.1"));
    }

    @Test
    void testShellExecuteCmdStream() {
        /** Test streaming command execution. */
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

    private void assumeSandboxImplemented() {
        Assumptions.assumeTrue(sysOp != null, "Sandbox mode is not fully implemented");
    }
}