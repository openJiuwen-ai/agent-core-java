/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.OperationRegistry;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test code operations through sandbox routing.
 * <p>
 * Mirrors Python's {@code test_code.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_code.py}.
 */
class TestCode extends BaseSandboxTest {

    @Test
    void testExecutePythonCodeSuccess() {
        String code = "print(\"Hello, Python!\")\nprint(\"3\")";
        ExecuteCodeResult result = sysOp.code().executeCode(code, "python", 300, null, null);

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
    void testExecuteJavascriptCodeSuccess() {
        String code = "print(\"Hello, JavaScript!\")\nprint(\"12\")";
        ExecuteCodeResult result = sysOp.code().executeCode(code, "javascript", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("javascript", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Hello, JavaScript!"));
        assertTrue(result.getData().getStdout().contains("12"));
    }

    @Test
    void testExecuteCodeWithEnvironmentVars() {
        String code = """
                import os
                print(os.getenv("TEST_ENV"))
                print(os.getenv("COUNT"))
                """;

        ExecuteCodeResult result = sysOp.code().executeCode(
                code,
                "python",
                300,
                Map.of("TEST_ENV", "pytest_test", "COUNT", "5"),
                null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertEquals(List.of("pytest_test", "5"), result.getData().getStdout().strip().lines().toList());
    }

    @Test
    void testExecuteCodeWithCustomTimeout() {
        ExecuteCodeResult result = sysOp.code().executeCode(
                "print(\"Timeout test pass\")", "python", 2, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Timeout test pass"));
    }

    @Test
    void testExecuteEmptyCode() {
        for (String code : List.of("", "   ", "\n", "\t")) {
            ExecuteCodeResult result = sysOp.code().executeCode(code, "python", 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("code can not be empty"));
            assertNotNull(result.getData());
            assertEquals(-1, result.getData().getExitCode());
        }
    }

    @Test
    void testExecuteUnsupportedLanguage() {
        for (String language : List.of("java", "c++", "ruby", "go")) {
            ExecuteCodeResult result = sysOp.code().executeCode("print('test')", language, 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains(language + " is not supported"));
            assertNotNull(result.getData());
            assertEquals(language, result.getData().getLanguage());
        }
    }

    @Test
    void testExecutePythonCodeWithSyntaxError() {
        ExecuteCodeResult result = sysOp.code().executeCode("print('missing quote", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertNotEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStderr().contains("SyntaxError"));
    }

    @Test
    void testExecuteCodeTimeout() {
        ExecuteCodeResult result = sysOp.code().executeCode("import time; time.sleep(3)", "python", 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("execution timeout after 1 seconds"));
        assertNotNull(result.getData());
        assertNotEquals(0, result.getData().getExitCode());
    }

    @Test
    void testExecuteLongRunningValidCode() {
        ExecuteCodeResult result = sysOp.code().executeCode("print(\"Long run success\")", "python", 3, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().getStdout().contains("Long run success"));
    }

    @Test
    void testExecuteCodeWithSpecialCharacters() {
        String code = """
                print("Chinese test: 中文测试")
                print("Special symbols: !@#$%^&*()_+-=[]{}|;:,.<>?")
                """;

        ExecuteCodeResult result = sysOp.code().executeCode(code, "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().getStdout().contains("Chinese test: 中文测试"));
        assertTrue(result.getData().getStdout().contains("!@#$%^&*()"));
    }

    @Test
    void testSysOpFixtureReusability() {
        ExecuteCodeResult result1 = sysOp.code().executeCode("print(\"1\")", "python", 300, null, null);
        ExecuteCodeResult result2 = sysOp.code().executeCode("print(\"2\")", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result1.getCode());
        assertEquals(StatusCode.SUCCESS.getCode(), result2.getCode());
        assertNotNull(result2.getData());
        assertTrue(result2.getData().getStdout().contains("2"));
    }

    @Test
    void testExecuteCodeForceFileTrueViaOptions() {
        String code = """
                print("Python Exec Mode: Temp File")
                print("50 + 60 = 110")
                """;
        ExecuteCodeResult result = sysOp.code().executeCode(
                code,
                "python",
                300,
                null,
                Map.of("force_file", true, "encoding", "utf-8"));

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Python Exec Mode: Temp File"));
        assertTrue(result.getData().getStdout().contains("50 + 60 = 110"));
        assertEquals("", result.getData().getStderr().strip());
    }

    @Test
    void testExecuteCodeForceFileTrueJavascript() {
        String code = """
                print("JS Exec Mode: Temp File")
                print("15 * 25 = 375")
                """;
        ExecuteCodeResult result = sysOp.code().executeCode(
                code,
                "javascript",
                300,
                null,
                Map.of("force_file", true, "encoding", "utf-8"));

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("JS Exec Mode: Temp File"));
        assertTrue(result.getData().getStdout().contains("15 * 25 = 375"));
        assertEquals("", result.getData().getStderr().strip());
    }

    @Test
    void testExecuteCodeForceFileTrueWithError() {
        ExecuteCodeResult result = sysOp.code().executeCode(
                "print(undefined_variable_999)", "python", 300, null, Map.of("force_file", true));

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertNotEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStderr().contains("undefined_variable_999"));
    }

    @Test
    void testExecuteCodeForceFileTrueTimeout() {
        String code = """
                import time
                time.sleep(3)
                print("This line should not be printed")
                """;
        ExecuteCodeResult result = sysOp.code().executeCode(
                code,
                "python",
                1,
                null,
                Map.of("force_file", true));

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("timeout after 1 seconds"));
        assertNotNull(result.getData());
        assertNotEquals(0, result.getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamEmptyCode() {
        List<ExecuteCodeStreamResult> results = collect(sysOp.code().executeCodeStream("", "python", 300, null, null));

        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), results.get(0).getCode());
        assertTrue(results.get(0).getMessage().contains("code can not be empty"));
        assertNotEquals(0, results.get(0).getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamUnsupportedLanguage() {
        List<ExecuteCodeStreamResult> results = collect(
                sysOp.code().executeCodeStream("print(1)", "java", 300, null, null));

        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), results.get(0).getCode());
        assertTrue(results.get(0).getMessage().contains("java is not supported"));
        assertNotEquals(0, results.get(0).getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamPythonNormal() {
        String code = """
                print("hello python")
                print("stream test for python")
                """;
        List<ExecuteCodeStreamResult> results = collect(
                sysOp.code().executeCodeStream(code, "python", 10, null, null));

        assertTrue(results.size() >= 3);
        assertTrue(results.stream().anyMatch(res -> isStdoutWith(res, "hello python")));
        assertTrue(results.stream().anyMatch(res -> isStdoutWith(res, "stream test for python")));
        assertEquals("Code executed successfully", results.get(results.size() - 1).getMessage());
        assertEquals(0, results.get(results.size() - 1).getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamPythonStderr() {
        List<ExecuteCodeStreamResult> results = collect(
                sysOp.code().executeCodeStream("print(undefined_variable)", "python", 10, null, null));

        assertTrue(results.size() >= 2);
        assertTrue(results.stream().anyMatch(res -> "stderr".equals(res.getData().getType())
                && res.getData().getText().contains("NameError")));
        assertEquals("Code executed successfully", results.get(results.size() - 1).getMessage());
        assertNotEquals(0, results.get(results.size() - 1).getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamJavascriptNormal() {
        String code = """
                print("hello javascript")
                print("stream test for js")
                """;
        List<ExecuteCodeStreamResult> results = collect(
                sysOp.code().executeCodeStream(code, "javascript", 10, null, null));

        assertTrue(results.size() >= 3);
        assertTrue(results.stream().anyMatch(res -> isStdoutWith(res, "hello javascript")));
        assertTrue(results.stream().anyMatch(res -> isStdoutWith(res, "stream test for js")));
        assertEquals("Code executed successfully", results.get(results.size() - 1).getMessage());
        assertEquals(0, results.get(results.size() - 1).getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamCustomOptions() {
        List<ExecuteCodeStreamResult> results = collect(sysOp.code().executeCodeStream(
                "print(\"chunk-size-option-test\")",
                "python",
                10,
                null,
                Map.of("chunk_size", 512, "encoding", "utf-8")));

        String stdout = joinText(results);
        assertTrue(stdout.contains("chunk-size-option-test"));
        assertEquals(0, results.get(results.size() - 1).getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamCustomEnvironment() {
        String code = """
                import os
                print(os.getenv("TEST_ENV_KEY"))
                print(os.getenv("TEST_ENV_VALUE"))
                """;
        List<ExecuteCodeStreamResult> results = collect(sysOp.code().executeCodeStream(
                code,
                "python",
                10,
                Map.of("TEST_ENV_KEY", "python_test", "TEST_ENV_VALUE", "123456"),
                null));

        String stdout = joinText(results);
        assertTrue(stdout.contains("python_test"));
        assertTrue(stdout.contains("123456"));
    }

    @Test
    void testExecuteCodeStreamTimeout() {
        List<ExecuteCodeStreamResult> results = collect(
                sysOp.code().executeCodeStream("while True: pass", "python", 2, null, null));

        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), results.get(0).getCode());
        assertTrue(results.get(0).getMessage().toLowerCase().contains("timeout"));
    }

    @Test
    void testExecuteCodeStreamDefaultParams() {
        List<ExecuteCodeStreamResult> results = collect(
                sysOp.code().executeCodeStream("print(\"default parameter test success\")", "python", 300, null, null));

        assertTrue(results.size() >= 2);
        assertTrue(joinText(results).contains("default parameter test success"));
        assertEquals("Code executed successfully", results.get(results.size() - 1).getMessage());
        assertEquals(0, results.get(results.size() - 1).getData().getExitCode());
    }

    @Test
    void testSandboxDiscovery() {
        var fsOp = OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX);
        assertTrue(fsOp.isPresent());
        assertEquals("fs", fsOp.get().getName());
        assertEquals(OperationMode.SANDBOX, fsOp.get().getMode());

        assertTrue(OperationRegistry.getOperationInfo("shell", OperationMode.SANDBOX).isPresent());
        assertTrue(OperationRegistry.getOperationInfo("code", OperationMode.SANDBOX).isPresent());
    }

    private static boolean isStdoutWith(ExecuteCodeStreamResult result, String text) {
        return result.getData() != null
                && "stdout".equals(result.getData().getType())
                && result.getData().getText() != null
                && result.getData().getText().contains(text);
    }

    private static String joinText(List<ExecuteCodeStreamResult> results) {
        StringBuilder builder = new StringBuilder();
        for (ExecuteCodeStreamResult result : results) {
            if (result.getData() != null && result.getData().getText() != null) {
                builder.append(result.getData().getText());
            }
        }
        return builder.toString();
    }

    private static List<ExecuteCodeStreamResult> collect(Iterator<ExecuteCodeStreamResult> iterator) {
        List<ExecuteCodeStreamResult> results = new ArrayList<>();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }
        return results;
    }
}
