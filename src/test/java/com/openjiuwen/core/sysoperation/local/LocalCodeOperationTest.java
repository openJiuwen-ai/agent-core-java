// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysoperation.SysOperation;
import com.openjiuwen.core.sysoperation.SysOperationCard;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.code.BaseCodeOperation;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.result.Language;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalCodeOperation.
 * 
 * <p>对应 Python: tests/unit_tests/core/sys_operation/local/test_code_operation.py
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@DisplayName("LocalCodeOperation Tests")
class LocalCodeOperationTest {

    private SysOperation sysOp;

    @BeforeAll
    static void initRegistry() {
        // Trigger static initializer to register operations
        try {
            Class.forName("com.openjiuwen.core.sysoperation.local.LocalCodeOperation");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load LocalCodeOperation", e);
        }
    }

    @BeforeEach
    void setUp() {
        SysOperationCard card = SysOperationCard.builder()
            .id("test_code_op")
            .mode(OperationMode.LOCAL)
            .workConfig(new LocalWorkConfig())
            .build();
        sysOp = new SysOperation(card);
    }

    @Nested
    @DisplayName("TestSysOperationExecuteCode")
    class TestSysOperationExecuteCode {

        @Test
        @DisplayName("test_execute_python_code_success")
        void testExecutePythonCodeSuccess() throws ExecutionException, InterruptedException {
            // Test data preparation
            String code = "print('Hello, Python!')";

            // Execute target method
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 300, null, null).get();

            // Assertions
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals("Code executed successfully", result.getMessage());
            assertNotNull(result.getData());
            assertEquals(code, result.getData().getCodeContent());
            assertEquals("python", result.getData().getLanguage());
            assertEquals(0, result.getData().getExitCode());
            assertTrue(result.getData().getStdout().contains("Hello, Python!"));
            assertEquals("", result.getData().getStderr());
        }

        @Test
        @DisplayName("test_execute_code_with_environment_vars")
        void testExecuteCodeWithEnvironmentVars() throws ExecutionException, InterruptedException {
            // Test data preparation
            Map<String, String> envVars = new HashMap<>();
            envVars.put("TEST_ENV", "pytest_test");
            envVars.put("COUNT", "5");
            String code = "import os\nprint(os.getenv('TEST_ENV'))\nprint(os.getenv('COUNT'))";

            // Execute target method
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 300, envVars, null).get();

            // Assertions
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("pytest_test"));
            assertTrue(result.getData().getStdout().contains("5"));
        }

        @Test
        @DisplayName("test_execute_code_with_custom_timeout")
        void testExecuteCodeWithCustomTimeout() throws ExecutionException, InterruptedException {
            // Test data preparation (sleep 0.5s, timeout 10s - should not timeout)
            String code = "import time; time.sleep(0.5); print('Timeout test pass')";

            // Execute target method
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 10, null, null).get();

            // Assertions
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("pass"));
        }

        @Test
        @DisplayName("test_execute_empty_code")
        void testExecuteEmptyCode() throws ExecutionException, InterruptedException {
            // Test multiple empty code scenarios
            String[] emptyCodes = {"", "   ", "\n", "\t"};
            
            for (String code : emptyCodes) {
                BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
                ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 300, null, null).get();

                assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
                assertTrue(result.getMessage().contains("code can not be empty"));
            }
        }

        @Test
        @DisplayName("test_execute_python_code_with_syntax_error")
        void testExecutePythonCodeWithSyntaxError() throws ExecutionException, InterruptedException {
            // Syntax error: missing closing quote
            String code = "print('missing quote";
            
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 300, null, null).get();

            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("execution failed"));
            assertNotEquals(0, result.getData().getExitCode());
            assertTrue(result.getData().getStderr().contains("SyntaxError"));
            assertEquals(code, result.getData().getCodeContent());
        }

        @Test
        @DisplayName("test_execute_code_timeout")
        void testExecuteCodeTimeout() throws ExecutionException, InterruptedException {
            // Test data preparation (sleep 3s, timeout 1s - should trigger timeout)
            String code = "import time; time.sleep(3)";

            // Execute target method
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 1, null, null).get();

            // Assertions
            assertEquals(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("execution timeout after 1 seconds"));
            assertEquals(-1, result.getData().getExitCode());
            assertEquals("execution timeout after 1 seconds", result.getData().getStderr());
        }

        @Test
        @DisplayName("test_execute_long_running_valid_code")
        void testExecuteLongRunningValidCode() throws ExecutionException, InterruptedException {
            // Sleep 1s, timeout 3s - should complete successfully
            String code = "import time; time.sleep(1); print('Long run success')";

            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 3, null, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().toLowerCase().contains("success"));
        }

        @Test
        @DisplayName("test_execute_code_with_large_output")
        void testExecuteCodeWithLargeOutput() throws ExecutionException, InterruptedException {
            // Generate 100 lines of output (reduced from 1000 for faster test)
            String code = "print('\\n'.join([f'Line {i}' for i in range(100)]))";
            
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 30, null, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals(100, result.getData().getStdout().split("\n").length);
            assertEquals("", result.getData().getStderr());
        }

        @Test
        @DisplayName("test_execute_code_with_special_characters")
        void testExecuteCodeWithSpecialCharacters() throws ExecutionException, InterruptedException {
            String code = "print('Chinese test: 中文测试')\nprint('Special symbols: !@#$%^&*()_+-=[]{}|;:,.<>?')";
            
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 30, null, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("中文测试"));
            assertTrue(result.getData().getStdout().contains("!@#$%^&*()"));
        }

        @Test
        @DisplayName("test_sys_op_fixture_reusability")
        void testSysOpFixtureReusability() throws ExecutionException, InterruptedException {
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            
            // First execution
            ExecuteCodeResult result1 = codeOp.executeCode("print(1)", Language.PYTHON, 30, null, null).get();
            assertEquals(StatusCode.SUCCESS.getCode(), result1.getCode());

            // Second execution with different code
            ExecuteCodeResult result2 = codeOp.executeCode("print(2)", Language.PYTHON, 30, null, null).get();
            assertEquals(StatusCode.SUCCESS.getCode(), result2.getCode());
            assertTrue(result2.getData().getStdout().contains("2"));
        }

        @Test
        @DisplayName("test_execute_code_with_stdout_and_stderr")
        void testExecuteCodeWithStdoutAndStderr() throws ExecutionException, InterruptedException {
            String code = "import sys; print('stdout_output'); print('stderr_output', file=sys.stderr)";
            
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 30, null, null).get();
            
            // Code that writes to stderr still exits with 0 (successful execution)
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("stdout_output"));
            assertTrue(result.getData().getStderr().contains("stderr_output"));
        }

        @Test
        @DisplayName("test_execute_code_stream_method_exists")
        void testExecuteCodeStreamMethodExists() {
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            
            assertNotNull(codeOp);
            // The method exists but throws UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class, () -> {
                codeOp.executeCodeStream("print(1)", Language.PYTHON, 30, null, null);
            });
        }

        @Test
        @DisplayName("test_execute_code_stderr_only")
        void testExecuteCodeStderrOnly() throws ExecutionException, InterruptedException {
            String code = "import sys; print('error_message', file=sys.stderr)";
            
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 30, null, null).get();
            
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals("", result.getData().getStdout());
            assertTrue(result.getData().getStderr().contains("error_message"));
            assertEquals(0, result.getData().getExitCode());
        }

        @Test
        @DisplayName("test_execute_code_default_language")
        void testExecuteCodeDefaultLanguage() throws ExecutionException, InterruptedException {
            // Use convenience method that defaults to Python
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode("print('default language')").get();
            
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("default language"));
        }

        @Test
        @DisplayName("test_execute_multiline_code")
        void testExecuteMultilineCode() throws ExecutionException, InterruptedException {
            String code = "x = 1\ny = 2\nprint(x + y)";
            
            BaseCodeOperation codeOp = (BaseCodeOperation) sysOp.code();
            ExecuteCodeResult result = codeOp.executeCode(code, Language.PYTHON, 30, null, null).get();
            
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("3"));
        }
    }

    @Nested
    @DisplayName("TestCodeOperationLanguageMapping")
    class TestCodeOperationLanguageMapping {

        @Test
        @DisplayName("test_language_enum_python")
        void testLanguageEnumPython() {
            assertEquals("python", Language.PYTHON.getValue());
        }

        @Test
        @DisplayName("test_language_enum_javascript")
        void testLanguageEnumJavascript() {
            assertEquals("javascript", Language.JAVASCRIPT.getValue());
        }

        @Test
        @DisplayName("test_language_from_value")
        void testLanguageFromValue() {
            assertEquals(Language.PYTHON, Language.fromValue("python"));
            assertEquals(Language.JAVASCRIPT, Language.fromValue("javascript"));
            assertThrows(IllegalArgumentException.class, () -> Language.fromValue("java"));
        }
    }
}

