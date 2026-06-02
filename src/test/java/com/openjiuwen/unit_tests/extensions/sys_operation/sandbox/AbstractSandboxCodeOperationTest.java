/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.local.StreamEventType;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractSandboxCodeOperationTest extends SandboxExtensionTestSupport {

    protected abstract SysOperation createSysOp();

    private List<ExecuteCodeStreamResult> collectStreamResults(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options
    ) {
        return collect(createSysOp().code().executeCodeStream(code, language, timeout, environment, options));
    }

    @Test
    void testExecutePythonCodeSuccess() {
        String code = "print('Hello, Python!'); x = 1 + 2; print(x)";

        ExecuteCodeResult result = createSysOp().code().executeCode(code, "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertEquals(code, result.getData().getCodeContent());
        assertEquals("python", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertEquals("Hello, Python!\n3", result.getData().getStdout().trim());
        assertEquals("", result.getData().getStderr());
    }

    @Test
    void testExecuteJavascriptCodeSuccess() {
        String code = "console.log('Hello, JavaScript!'); const x = 3 * 4; console.log(x)";

        ExecuteCodeResult result = createSysOp().code().executeCode(code, "javascript", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertEquals(code, result.getData().getCodeContent());
        assertEquals("javascript", result.getData().getLanguage());
        assertEquals(0, result.getData().getExitCode());
        assertEquals("Hello, JavaScript!\n12", result.getData().getStdout().trim());
        assertEquals("", result.getData().getStderr());
    }

    @Test
    void testExecuteCodeWithEnvironmentVars() {
        String code = """
                import os
                print(os.getenv('TEST_ENV'))
                print(os.getenv('COUNT'))
                """;

        ExecuteCodeResult result = createSysOp().code().executeCode(
                code,
                "python",
                300,
                Map.of("TEST_ENV", "pytest_test", "COUNT", "5"),
                null
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(0, result.getData().getExitCode());
        assertEquals("pytest_test\n5", result.getData().getStdout().trim());
    }

    @Test
    void testExecuteCodeWithCustomTimeout() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                "import time; time.sleep(1); print('Timeout test pass')",
                "python",
                2,
                null,
                null
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("Timeout test pass"));
    }

    @Test
    void testExecuteEmptyCode() {
        for (String code : List.of("", "   ", "\n", "\t")) {
            ExecuteCodeResult result = createSysOp().code().executeCode(code, null, 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("code can not be empty"));
            assertNotNull(result.getData());
            assertEquals(code, result.getData().getCodeContent());
            assertEquals("python", result.getData().getLanguage());
        }
    }

    @Test
    void testExecuteUnsupportedLanguage() {
        for (String language : List.of("java", "c++", "ruby", "go")) {
            ExecuteCodeResult result = createSysOp().code().executeCode("print('test')", language, 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains(language + " is not supported"));
            assertEquals(language, result.getData().getLanguage());
        }
    }

    @Test
    void testExecutePythonCodeWithSyntaxError() {
        ExecuteCodeResult result = createSysOp().code().executeCode("print('missing quote", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getStderr());
        assertTrue(result.getData().getExitCode() != 0);
    }

    @Test
    void testExecuteCodeTimeout() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                "import time; time.sleep(3)",
                "python",
                1,
                null,
                null
        );

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("timeout"));
        assertTrue(result.getData().getExitCode() != 0);
    }

    @Test
    void testExecuteLongRunningValidCode() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                "import time; time.sleep(2); print('Long run success')",
                "python",
                3,
                null,
                null
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains("Long run success"));
    }

    @Test
    void testExecuteCodeWithLargeOutput() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                "print('\\n'.join([f'Line {i}' for i in range(1000)]))",
                "python",
                300,
                null,
                null
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1000, result.getData().getStdout().split("\\R").length);
        assertEquals("", result.getData().getStderr());
    }

    @Test
    void testExecuteCodeWithSpecialCharacters() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                """
                print("Unicode test: 中文")
                print("Special symbols: !@#$%^&*()_+-=[]{}|;:,.<>?")
                """,
                "python",
                300,
                null,
                null
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains("中文"));
        assertTrue(result.getData().getStdout().contains("!@#$%^&*()"));
    }

    @Test
    void testSysOpFixtureReusability() {
        SysOperation sysOp = createSysOp();

        ExecuteCodeResult result1 = sysOp.code().executeCode("print(1)", "python", 300, null, null);
        ExecuteCodeResult result2 = sysOp.code().executeCode("print(2)", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result1.getCode());
        assertEquals(StatusCode.SUCCESS.getCode(), result2.getCode());
        assertTrue(result2.getData().getStdout().contains("2"));
    }

    @Test
    void testExecuteCodeForceFileTrueViaOptions() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                """
                print(f"Python Exec Mode: Temp File")
                a, b = 50, 60
                print(f"50 + 60 = {a + b}")
                """,
                "python",
                300,
                null,
                Map.of("force_file", true, "encoding", "utf-8")
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("50 + 60 = 110"));
    }

    @Test
    void testExecuteCodeForceFileTrueJavascript() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                """
                console.log("JS Exec Mode: Temp File");
                const num1 = 15, num2 = 25;
                console.log(`15 * 25 = ${num1 * num2}`);
                """,
                "javascript",
                300,
                null,
                Map.of("force_file", true, "encoding", "utf-8")
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(0, result.getData().getExitCode());
        assertTrue(result.getData().getStdout().contains("JS Exec Mode: Temp File"));
        assertTrue(result.getData().getStdout().contains("15 * 25 = 375"));
    }

    @Test
    void testExecuteCodeForceFileTrueWithError() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                "print(undefined_variable_999)",
                "python",
                300,
                null,
                Map.of("force_file", true)
        );

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Code executed successfully", result.getMessage());
        assertTrue(result.getData().getExitCode() != 0);
    }

    @Test
    void testExecuteCodeForceFileTrueTimeout() {
        ExecuteCodeResult result = createSysOp().code().executeCode(
                """
                import time
                time.sleep(3)
                print("This line should not be printed")
                """,
                "python",
                1,
                null,
                Map.of("force_file", true)
        );

        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("timeout"));
        assertTrue(result.getData().getExitCode() != 0);
    }

    @Test
    void testExecuteCodeStreamEmptyCode() {
        List<ExecuteCodeStreamResult> emptyCodeResults = collectStreamResults("", null, 300, null, null);
        assertEquals(1, emptyCodeResults.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), emptyCodeResults.getFirst().getCode());
        assertTrue(emptyCodeResults.getFirst().getMessage().contains("code can not be empty"));
        assertEquals(-1, emptyCodeResults.getFirst().getData().getExitCode());

        List<ExecuteCodeStreamResult> blankCodeResults = collectStreamResults("   \n\t", null, 300, null, null);
        assertEquals(1, blankCodeResults.size());
        assertTrue(blankCodeResults.getFirst().getMessage().contains("code can not be empty"));
    }

    @Test
    void testExecuteCodeStreamUnsupportedLanguage() {
        List<ExecuteCodeStreamResult> results = collectStreamResults("print(1)", "java", 300, null, null);

        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), results.getFirst().getCode());
        assertTrue(results.getFirst().getMessage().contains("java is not supported"));
        assertEquals(-1, results.getFirst().getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamPythonNormal() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                """
                print("hello python")
                print("stream test for python")
                """,
                "python",
                10,
                null,
                null
        );

        assertTrue(results.size() >= 2);
        assertTrue(results.stream().anyMatch(res ->
                StreamEventType.STDOUT.getValue().equals(res.getData().getType())
                        && res.getData().getText().contains("hello python")));
        assertTrue(results.stream().anyMatch(res ->
                StreamEventType.STDOUT.getValue().equals(res.getData().getType())
                        && res.getData().getText().contains("stream test for python")));
        assertEquals(0, results.getLast().getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamPythonStderr() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                "print(undefined_variable)",
                "python",
                10,
                null,
                null
        );

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(res ->
                StreamEventType.STDERR.getValue().equals(res.getData().getType())
                        || (res.getData().getExitCode() != null && res.getData().getExitCode() != 0)));
        assertTrue(results.getLast().getData().getExitCode() != 0);
    }

    @Test
    void testExecuteCodeStreamJavascriptNormal() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                """
                console.log("hello javascript");
                console.log("stream test for js");
                """,
                "javascript",
                10,
                null,
                null
        );

        assertTrue(results.size() >= 2);
        assertTrue(results.stream().anyMatch(res ->
                StreamEventType.STDOUT.getValue().equals(res.getData().getType())
                        && res.getData().getText().contains("hello javascript")));
        assertTrue(results.stream().anyMatch(res ->
                StreamEventType.STDOUT.getValue().equals(res.getData().getType())
                        && res.getData().getText().contains("stream test for js")));
        assertEquals(0, results.getLast().getData().getExitCode());
    }

    @Test
    void testExecuteCodeStreamCustomOptions() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                "print('a'*2048)",
                "python",
                10,
                null,
                Map.of("chunk_size", 512, "encoding", "utf-8")
        );

        assertTrue(results.size() >= 2);
        long count = results.stream()
                .map(res -> res.getData().getText())
                .filter(text -> text != null)
                .mapToLong(text -> text.chars().filter(ch -> ch == 'a').count())
                .sum();
        assertEquals(2048, count);
    }

    @Test
    void testExecuteCodeStreamCustomEnvironment() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                """
                import os
                print(os.getenv("TEST_ENV_KEY"))
                print(os.getenv("TEST_ENV_VALUE"))
                """,
                "python",
                10,
                Map.of("TEST_ENV_KEY", "python_test", "TEST_ENV_VALUE", "123456"),
                null
        );

        String stdoutText = results.stream()
                .map(res -> res.getData().getText())
                .filter(text -> text != null)
                .reduce("", String::concat);
        assertTrue(stdoutText.contains("python_test"));
        assertTrue(stdoutText.contains("123456"));
    }

    @Test
    void testExecuteCodeStreamTimeout() {
        List<ExecuteCodeStreamResult> results = collectStreamResults("while True: pass", "python", 2, null, null);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(res -> res.getMessage().toLowerCase().contains("timeout")));
    }

    @Test
    void testExecuteCodeStreamDefaultParams() {
        List<ExecuteCodeStreamResult> results = collectStreamResults(
                "print('default parameter test success')",
                null,
                300,
                null,
                null
        );

        assertTrue(results.size() >= 2);
        String stdoutText = results.stream()
                .map(res -> res.getData().getText())
                .filter(text -> text != null)
                .reduce("", String::concat);
        assertTrue(stdoutText.contains("default parameter test success"));
        assertEquals("Code executed successfully", results.getLast().getMessage());
        assertEquals(0, results.getLast().getData().getExitCode());
    }
}
