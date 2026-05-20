/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;

/**
 * Tests for LocalCodeOperation.
 * Mirrors Python's test_code_operation.py test cases.
 */
class LocalCodeOperationTest {

    private SysOperation sysOp;

    @BeforeEach
    void setUp() {
        SysOperationCard card = new SysOperationCard();
        card.setId("test_code_op");
        card.setMode(OperationMode.LOCAL);
        sysOp = new SysOperation(card);
    }

    private BaseCodeOperation code() {
        return sysOp.code();
    }

    private List<ExecuteCodeStreamResult> collectStreamResults(Iterator<ExecuteCodeStreamResult> it) {
        List<ExecuteCodeStreamResult> results = new ArrayList<>();
        while (it.hasNext()) {
            results.add(it.next());
        }
        return results;
    }

    private static boolean isNodeAvailable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        String nodeExe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "node.exe" : "node";
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, nodeExe);
            if (f.exists() && f.isFile() && f.canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPythonAvailable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        String pythonExe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "python.exe" : "python";
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, pythonExe);
            if (f.exists() && f.isFile() && f.canExecute()) {
                return true;
            }
        }
        return false;
    }

    // ==================== executeCode Test Cases ====================

    @Test
    @DisplayName("Execute valid Python code successfully")
    void testExecutePythonCodeSuccess() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print('Hello, Python!'); x = 1 + 2; print(x)";
        ExecuteCodeResult result = code().executeCode(code, "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(code, result.getData().getCodeContent());
        assertEquals("python", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Hello, Python!"));
        assertTrue(result.getData().getStdout().contains("3"));
        assertEquals("", result.getData().getStderr());
    }

    @Test
    @DisplayName("Execute valid JavaScript code successfully (requires Node.js)")
    void testExecuteJavascriptCodeSuccess() {
        if (!isNodeAvailable()) {
            System.out.println("Node.js not found, skipping...");
            return;
        }
        String code = "console.log('Hello, JavaScript!'); const x = 3 * 4; console.log(x)";
        ExecuteCodeResult result = code().executeCode(code, "javascript", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertNotNull(result.getData());
        assertEquals("javascript", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Hello, JavaScript!"));
        assertTrue(result.getData().getStdout().contains("12"));
        assertEquals("", result.getData().getStderr());
    }

    @Test
    @DisplayName("Execute code with custom environment variables")
    void testExecuteCodeWithEnvironmentVars() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        Map<String, String> env = new HashMap<>();
        env.put("TEST_ENV", "pytest_test");
        env.put("COUNT", "5");
        String code = "import os\nprint(os.getenv('TEST_ENV'))\nprint(os.getenv('COUNT'))";

        ExecuteCodeResult result = code().executeCode(code, "python", 300, env, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("pytest_test"));
        assertTrue(result.getData().getStdout().contains("5"));
    }

    @Test
    @DisplayName("Execute code with custom timeout (no timeout triggered)")
    void testExecuteCodeWithCustomTimeout() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "import time; time.sleep(1); print('Timeout test pass')";
        ExecuteCodeResult result = code().executeCode(code, "python", 3, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("pass"));
    }

    @Test
    @DisplayName("Execute empty code returns error")
    void testExecuteEmptyCode() {
        String[] emptyCodes = {"", "   ", "\n", "\t"};
        for (String emptyCode : emptyCodes) {
            ExecuteCodeResult result = code().executeCode(emptyCode, "python", 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode(),
                    "Empty code '" + emptyCode.replace("\n", "\\n") + "' should return error");
            assertTrue(result.getMessage().contains("code can not be empty"),
                    "Message should contain 'code can not be empty'");
        }
    }

    @Test
    @DisplayName("Execute unsupported language returns error")
    void testExecuteUnsupportedLanguage() {
        String code = "print('test')";
        String[] unsupported = {"java", "c++", "ruby", "go"};
        for (String lang : unsupported) {
            ExecuteCodeResult result = code().executeCode(code, lang, 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains(lang + " is not supported"), 
                    "Message should contain '" + lang + " is not supported'");
            assertEquals(code, result.getData().getCodeContent());
            assertEquals(lang, result.getData().getLanguage());
        }
    }

    @Test
    @DisplayName("Execute Python code with syntax error")
    void testExecutePythonCodeWithSyntaxError() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print('missing quote";
        ExecuteCodeResult result = code().executeCode(code, "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(code, result.getData().getCodeContent());
        assertEquals("python", result.getData().getLanguage());
        assertNotEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStderr().contains("SyntaxError"));
    }

    @Test
    @DisplayName("Execute code timeout")
    void testExecuteCodeTimeout() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "import time; time.sleep(5)";
        ExecuteCodeResult result = code().executeCode(code, "python", 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("execution timeout after 1 seconds"));
        assertNotEquals(0, result.getData().getExitCode());
    }

    @Test
    @DisplayName("Execute long-running valid code (within timeout)")
    void testExecuteLongRunningValidCode() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "import time; time.sleep(2); print('Long run success')";
        ExecuteCodeResult result = code().executeCode(code, "python", 5, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().toLowerCase().contains("success"));
    }

    @Test
    @DisplayName("Execute code with large output")
    void testExecuteCodeWithLargeOutput() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print('\\n'.join([f'Line {i}' for i in range(1000)]))";
        ExecuteCodeResult result = code().executeCode(code, "python", 120, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1000, result.getData().getStdout().trim().split("\\R").length);
        assertEquals("", result.getData().getStderr());
    }

    @Test
    @DisplayName("Execute code with special characters (Chinese, symbols)")
    void testExecuteCodeWithSpecialCharacters() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print('Chinese test: 中文测试')\nprint('Special symbols: !@#$%^&*()_+-=[]{}|;:,.<>?')";
        ExecuteCodeResult result = code().executeCode(code, "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains("中文测试"));
        assertTrue(result.getData().getStdout().contains("!@#$%^&*()"));
    }

    @Test
    @DisplayName("Execute code with force_file=true via options")
    void testExecuteCodeForceFileTrue() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "a, b = 50, 60\nprint(f'50 + 60 = {a + b}')";
        Map<String, Object> opts = new HashMap<>();
        opts.put("force_file", true);
        opts.put("encoding", "utf-8");

        ExecuteCodeResult result = code().executeCode(code, "python", 300, null, opts);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("50 + 60 = 110"));
        assertEquals("", result.getData().getStderr().trim());
    }

    @Test
    @DisplayName("Execute code with force_file=true and runtime error")
    void testExecuteCodeForceFileTrueWithError() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print(undefined_variable_999)";
        Map<String, Object> opts = Map.of("force_file", true);

        ExecuteCodeResult result = code().executeCode(code, "python", 300, null, opts);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertNotEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStderr().contains("undefined_variable_999"));
    }

    @Test
    @DisplayName("Execute code with force_file=true and timeout")
    void testExecuteCodeForceFileTrueTimeout() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "import time\ntime.sleep(5)\nprint('should not print')";
        Map<String, Object> opts = Map.of("force_file", true);

        ExecuteCodeResult result = code().executeCode(code, "python", 1, null, opts);

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("timeout after 1 seconds"));
        assertNotEquals(0, result.getData().getExitCode());
    }

    @Test
    @DisplayName("Reusability of code operation instance")
    void testFixtureReusability() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        ExecuteCodeResult r1 = code().executeCode("print(1)", "python", 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), r1.getCode());

        ExecuteCodeResult r2 = code().executeCode("print(2)", "python", 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), r2.getCode());
        assertTrue(r2.getData().getStdout().contains("2"));
    }

    // ==================== executeCodeStream Test Cases ====================

    @Test
    @DisplayName("Stream: empty code returns error")
    void testStreamEmptyCode() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream("", "python", 300, null, null));
        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), results.get(0).getCode());
        assertTrue(results.get(0).getMessage().contains("code can not be empty"));

        // blank code
        List<ExecuteCodeStreamResult> blankResults = collectStreamResults(
                code().executeCodeStream("   \n\t", "python", 300, null, null));
        assertEquals(1, blankResults.size());
        assertTrue(blankResults.get(0).getMessage().contains("code can not be empty"));
    }

    @Test
    @DisplayName("Stream: unsupported language returns error")
    void testStreamUnsupportedLanguage() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream("print(1)", "java", 300, null, null));
        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), results.get(0).getCode());
        assertTrue(results.get(0).getMessage().contains("java is not supported"));
        assertNotEquals(0, results.get(0).getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: normal Python code execution")
    void testStreamPythonNormal() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print('hello python')\nprint('stream test for python')";
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream(code, "python", 10, null, null));

        assertTrue(results.size() >= 2, "Should have at least stdout + exit event");

        boolean hasHello = results.stream().anyMatch(r ->
                "stdout".equals(r.getData().getType()) && r.getData().getText().contains("hello python"));
        assertTrue(hasHello, "Should contain 'hello python' in stdout");

        boolean hasStream = results.stream().anyMatch(r ->
                "stdout".equals(r.getData().getType()) && r.getData().getText().contains("stream test for python"));
        assertTrue(hasStream, "Should contain 'stream test for python' in stdout");

        ExecuteCodeStreamResult lastResult = results.get(results.size() - 1);
        assertEquals("Code executed successfully", lastResult.getMessage());
        assertEquals(0, lastResult.getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: Python code with stderr")
    void testStreamPythonStderr() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print(undefined_variable)";
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream(code, "python", 10, null, null));

        assertTrue(results.size() >= 1);
        boolean hasStderr = results.stream().anyMatch(r ->
                "stderr".equals(r.getData().getType()) && r.getData().getText().contains("NameError"));
        assertTrue(hasStderr, "Should contain NameError in stderr");

        ExecuteCodeStreamResult lastResult = results.get(results.size() - 1);
        assertEquals("Code executed successfully", lastResult.getMessage());
        assertNotEquals(0, lastResult.getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: JavaScript normal execution (requires Node.js)")
    void testStreamJavascriptNormal() {
        if (!isNodeAvailable()) {
            System.out.println("Node.js not found, skipping...");
            return;
        }
        String code = "console.log('hello javascript');\nconsole.log('stream test for js');";
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream(code, "javascript", 10, null, null));

        assertTrue(results.size() >= 2);
        boolean hasHello = results.stream().anyMatch(r ->
                "stdout".equals(r.getData().getType()) && r.getData().getText().contains("hello javascript"));
        assertTrue(hasHello);

        ExecuteCodeStreamResult lastResult = results.get(results.size() - 1);
        assertEquals("Code executed successfully", lastResult.getMessage());
        assertEquals(0, lastResult.getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: custom environment variables")
    void testStreamCustomEnvironment() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "import os\nprint(os.getenv('TEST_ENV_KEY'))\nprint(os.getenv('TEST_ENV_VALUE'))";
        Map<String, String> env = Map.of("TEST_ENV_KEY", "python_test", "TEST_ENV_VALUE", "123456");

        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream(code, "python", 10, env, null));

        String stdoutText = results.stream()
                .filter(r -> r.getData().getText() != null)
                .map(r -> r.getData().getText())
                .reduce("", String::concat);
        assertTrue(stdoutText.contains("python_test"));
        assertTrue(stdoutText.contains("123456"));
    }

    @Test
    @DisplayName("Stream: timeout with infinite loop")
    void testStreamTimeout() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "while True: pass";
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream(code, "python", 2, null, null));

        assertTrue(results.size() >= 1);
        boolean hasTimeout = results.stream().anyMatch(r ->
                r.getMessage().toLowerCase().contains("timeout") ||
                r.getMessage().contains("execution receive error"));
        assertTrue(hasTimeout, "Should contain timeout error");
    }

    @Test
    @DisplayName("Stream: default parameters execution")
    void testStreamDefaultParams() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python not found, skipping test");
        String code = "print('default parameter test success')";
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                code().executeCodeStream(code, "python", 300, null, null));

        assertTrue(results.size() >= 2);
        String stdoutText = results.stream()
                .filter(r -> r.getData().getText() != null)
                .map(r -> r.getData().getText())
                .reduce("", String::concat);
        assertTrue(stdoutText.contains("default parameter test success"));

        ExecuteCodeStreamResult lastResult = results.get(results.size() - 1);
        assertEquals("Code executed successfully", lastResult.getMessage());
        assertEquals(0, lastResult.getData().getExitCode());
    }
}
