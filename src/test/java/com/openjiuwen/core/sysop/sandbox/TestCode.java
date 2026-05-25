/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test code operations through sandbox routing.
 * <p>
 * Mirrors Python's {@code test_code.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_code.py}.
 *
 * <p>Note: Sandbox mode is stubbed in Java - tests are disabled until implemented.
 */
@Disabled("Sandbox mode is not fully implemented in Java")
class TestCode extends BaseSandboxTest {

    @Test
    void testExecutePythonCodeSuccess() {
        /** Test successful execution of valid Python code. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        String sourceCode = "print(\"Hello, Python!\")\nprint(\"3\")";

        ExecuteCodeResult result = code.executeCode(sourceCode, "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(sourceCode, result.getData().getCodeContent());
        assertEquals("python", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Hello, Python!"));
        assertTrue(result.getData().getStdout().contains("3"));
    }

    @Test
    void testExecuteJavascriptCodeSuccess() {
        /** Test successful execution of valid JavaScript code. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        String sourceCode = "print(\"Hello, JavaScript!\")\nprint(\"12\")";

        ExecuteCodeResult result = code.executeCode(sourceCode, "javascript", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("javascript", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Hello, JavaScript!"));
    }

    @Test
    void testExecuteCodeWithEnvironmentVars() {
        /** Test environment variable propagation for code execution. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        String sourceCode = """
import os
print(os.getenv("TEST_ENV"))
print(os.getenv("COUNT"))
""";

        Map<String, String> env = new HashMap<>();
        env.put("TEST_ENV", "pytest_test");
        env.put("COUNT", "5");

        ExecuteCodeResult result = code.executeCode(sourceCode, "python", 300, env, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("pytest_test"));
    }

    @Test
    void testExecuteCodeWithTimeout() {
        /** Test code execution timeout behavior. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        String sourceCode = "import time\nprint('start')\ntime.sleep(5)\nprint('end')";

        ExecuteCodeResult result = code.executeCode(sourceCode, "python", 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(result.getData());
        assertEquals(-1, result.getData().getExitCode());
    }

    @Test
    void testExecuteCodeStream() {
        /** Test streaming code execution. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        String sourceCode = "print(\"line1\")\nprint(\"line2\")";

        List<ExecuteCodeStreamResult> chunks = new ArrayList<>();
        Iterator<ExecuteCodeStreamResult> iter = code.executeCodeStream(sourceCode, "python", 300, null, null);
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

    @Test
    void testExecuteCodeSyntaxError() {
        /** Test handling of syntax error in code. */
        assumeSandboxImplemented();

        BaseCodeOperation code = sysOp.code();
        String sourceCode = "print('hello'";  // Missing closing quote

        ExecuteCodeResult result = code.executeCode(sourceCode, "python", 300, null, null);

        // Should return error but not crash
        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().getExitCode() != 0);
        assertTrue(result.getData().getStderr().length() > 0);
    }

    private void assumeSandboxImplemented() {
        Assumptions.assumeTrue(sysOp != null, "Sandbox mode is not fully implemented");
    }
}