// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysoperation.SysOperation;
import com.openjiuwen.core.sysoperation.SysOperationCard;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdResult;
import com.openjiuwen.core.sysoperation.shell.BaseShellOperation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalShellOperation.
 * 
 * <p>对应 Python: tests/unit_tests/core/sys_operation/local/test_shell_operation.py
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@DisplayName("LocalShellOperation Tests")
class LocalShellOperationTest {

    @TempDir
    Path tempDir;

    private SysOperation sysOp;

    @BeforeAll
    static void initRegistry() {
        // Trigger static initializer to register operations
        try {
            Class.forName("com.openjiuwen.core.sysoperation.local.LocalShellOperation");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load LocalShellOperation", e);
        }
    }

    @BeforeEach
    void setUp() {
        LocalWorkConfig config = LocalWorkConfig.builder()
            .workDir(tempDir.toString())
            .shellAllowlist(null)  // Allow all commands
            .build();
        SysOperationCard card = SysOperationCard.builder()
            .id("test_shell_op")
            .mode(OperationMode.LOCAL)
            .workConfig(config)
            .build();
        sysOp = new SysOperation(card);
    }

    @Nested
    @DisplayName("Basic Shell Execution Tests")
    class BasicExecutionTests {

        @Test
        @DisplayName("test_shell_basic_execution_echo")
        void testShellBasicExecutionEcho() throws ExecutionException, InterruptedException {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("echo hello world").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertNotNull(result.getData());
            assertTrue(result.getData().getStdout().contains("hello world"));
            assertEquals(0, result.getData().getExitCode());
            assertEquals("echo hello world", result.getData().getCommand());
        }

        @Test
        @DisplayName("test_shell_basic_execution_ls")
        @DisabledOnOs(OS.WINDOWS)
        void testShellBasicExecutionLs() throws ExecutionException, InterruptedException {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("ls -la").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertNotNull(result.getData());
            assertEquals(0, result.getData().getExitCode());
        }

        @Test
        @DisplayName("test_shell_basic_execution_dir")
        @EnabledOnOs(OS.WINDOWS)
        void testShellBasicExecutionDir() throws ExecutionException, InterruptedException {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("dir").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertNotNull(result.getData());
            assertEquals(0, result.getData().getExitCode());
        }
    }

    @Nested
    @DisplayName("Environment Variable Tests")
    class EnvironmentVariableTests {

        @Test
        @DisplayName("test_shell_environment_variables")
        @DisabledOnOs(OS.WINDOWS)
        void testShellEnvironmentVariablesUnix() throws ExecutionException, InterruptedException {
            Map<String, String> env = new HashMap<>();
            env.put("TEST_VAR", "custom_value");

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(
                "echo $TEST_VAR", null, 300, env, null
            ).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("custom_value"));
        }

        @Test
        @DisplayName("test_shell_environment_variables_windows")
        @EnabledOnOs(OS.WINDOWS)
        void testShellEnvironmentVariablesWindows() throws ExecutionException, InterruptedException {
            Map<String, String> env = new HashMap<>();
            env.put("TEST_VAR", "custom_value");

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(
                "echo %TEST_VAR%", null, 300, env, null
            ).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("custom_value"));
        }

        @Test
        @DisplayName("test_shell_environment_custom_overrides_system")
        @DisabledOnOs(OS.WINDOWS)
        void testShellEnvironmentCustomOverridesSystemUnix() throws ExecutionException, InterruptedException {
            String customPathValue = "/custom/test/path";
            Map<String, String> env = new HashMap<>();
            env.put("PATH", customPathValue);

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(
                "echo $PATH", null, 300, env, null
            ).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains(customPathValue));
        }
    }

    @Nested
    @DisplayName("Working Directory Tests")
    class WorkingDirectoryTests {

        @Test
        @DisplayName("test_shell_cwd_absolute")
        @DisabledOnOs(OS.WINDOWS)
        void testShellCwdAbsoluteUnix() throws ExecutionException, InterruptedException, IOException {
            Path subdir = tempDir.resolve("subdir");
            Files.createDirectories(subdir);

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("pwd", subdir.toString()).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("subdir"));
        }

        @Test
        @DisplayName("test_shell_cwd_relative")
        @DisabledOnOs(OS.WINDOWS)
        void testShellCwdRelativeUnix() throws ExecutionException, InterruptedException, IOException {
            Path subdir = tempDir.resolve("subdir");
            Files.createDirectories(subdir);

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("pwd", "subdir").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("subdir"));
        }

        @Test
        @DisplayName("test_shell_default_cwd")
        @DisabledOnOs(OS.WINDOWS)
        void testShellDefaultCwdUnix() throws ExecutionException, InterruptedException, IOException {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("pwd").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            // Should resolve to work_dir (temp dir)
            String actualOut = result.getData().getStdout().trim().toLowerCase();
            String expected = tempDir.toRealPath().toString().toLowerCase();
            assertTrue(actualOut.contains(expected) || expected.contains(actualOut));
        }
    }

    @Nested
    @DisplayName("Timeout Tests")
    class TimeoutTests {

        private Path timeoutTempDir;
        private SysOperation timeoutSysOp;

        @BeforeEach
        void setUpTimeout() throws IOException {
            // Use a separate temp directory for timeout tests to avoid cleanup issues
            timeoutTempDir = Files.createTempDirectory("shell_timeout_test");
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(timeoutTempDir.toString())
                .shellAllowlist(null)
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_timeout_op")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            timeoutSysOp = new SysOperation(card);
        }

        @AfterEach
        void tearDownTimeout() throws InterruptedException {
            // Wait for processes to fully release on Windows
            Thread.sleep(500);
            // Try to clean up, but don't fail if it can't be deleted immediately
            try {
                Files.walk(timeoutTempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
            } catch (IOException ignored) {}
        }

        @Test
        @DisplayName("test_shell_timeout")
        void testShellTimeout() throws ExecutionException, InterruptedException {
            String cmdSleep = "python -c \"import time; time.sleep(5)\"";

            BaseShellOperation shellOp = (BaseShellOperation) timeoutSysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(cmdSleep, null, 1, null, null).get();

            assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("timed out"));
        }
    }

    @Nested
    @DisplayName("Allowlist Tests")
    class AllowlistTests {

        @Test
        @DisplayName("test_shell_allowlist_allowed")
        void testShellAllowlistAllowed() throws ExecutionException, InterruptedException {
            // Create SysOperation with allowlist
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(tempDir.toString())
                .shellAllowlist(Arrays.asList("echo", "pwd"))
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_allowlist")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation op = new SysOperation(card);

            BaseShellOperation shellOp = (BaseShellOperation) op.shell();
            ExecuteCmdResult result = shellOp.executeCmd("echo test").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        }

        @Test
        @DisplayName("test_shell_allowlist_denied")
        void testShellAllowlistDenied() throws ExecutionException, InterruptedException {
            // Create SysOperation with allowlist
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(tempDir.toString())
                .shellAllowlist(Arrays.asList("echo", "pwd"))
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_allowlist_deny")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation op = new SysOperation(card);

            BaseShellOperation shellOp = (BaseShellOperation) op.shell();
            ExecuteCmdResult result = shellOp.executeCmd("dir").get();  // 'dir' not in allowlist

            assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("not allowed"));
        }

        @Test
        @DisplayName("test_shell_allowlist_empty_command")
        void testShellAllowlistEmptyCommand() throws ExecutionException, InterruptedException {
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(tempDir.toString())
                .shellAllowlist(Arrays.asList("echo"))
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_allowlist_empty")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation op = new SysOperation(card);

            BaseShellOperation shellOp = (BaseShellOperation) op.shell();
            
            // Empty command should be denied
            ExecuteCmdResult result = shellOp.executeCmd("").get();
            assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("not allowed"));
        }

        @Test
        @DisplayName("test_shell_allowlist_path_command")
        @DisabledOnOs(OS.WINDOWS)
        void testShellAllowlistPathCommand() throws ExecutionException, InterruptedException, IOException {
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(tempDir.toString())
                .shellAllowlist(Arrays.asList("echo"))
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_allowlist_path")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation op = new SysOperation(card);

            BaseShellOperation shellOp = (BaseShellOperation) op.shell();
            
            // /bin/echo should match 'echo' in allowlist
            ExecuteCmdResult result = shellOp.executeCmd("/bin/echo hello").get();
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("hello"));
        }
    }

    @Nested
    @DisplayName("Output Stream Tests")
    class OutputStreamTests {

        @Test
        @DisplayName("test_shell_stderr_output")
        void testShellStderrOutput() throws ExecutionException, InterruptedException {
            String cmd = "python -c \"import sys; print('error_message', file=sys.stderr)\"";

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(cmd).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals("", result.getData().getStdout());
            assertTrue(result.getData().getStderr().contains("error_message"));
            assertEquals(0, result.getData().getExitCode());
        }

        @Test
        @DisplayName("test_shell_stdout_and_stderr_concurrent")
        void testShellStdoutAndStderrConcurrent() throws ExecutionException, InterruptedException {
            String cmd = "python -c \"import sys; print('stdout_msg'); print('stderr_msg', file=sys.stderr)\"";

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(cmd).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getStdout().contains("stdout_msg"));
            assertTrue(result.getData().getStderr().contains("stderr_msg"));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("test_shell_command_not_found")
        void testShellCommandNotFound() throws ExecutionException, InterruptedException {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd("nonexistent_command_xyz123_test").get();

            // Command not found typically returns non-zero exit code
            assertNotEquals(0, result.getData().getExitCode());
        }

        @Test
        @DisplayName("test_shell_cwd_nonexistent_directory")
        void testShellCwdNonexistentDirectory() throws ExecutionException, InterruptedException {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(
                "echo test",
                "/nonexistent/path/that/does/not/exist",
                300, null, null
            ).get();

            assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
        }

        @Test
        @DisplayName("test_shell_exit_code_nonzero_success")
        void testShellExitCodeNonzeroSuccess() throws ExecutionException, InterruptedException {
            // Command that exits with code 1
            String cmd = "python -c \"import sys; sys.exit(1)\"";

            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            ExecuteCmdResult result = shellOp.executeCmd(cmd).get();

            // The operation itself succeeds, but the command failed
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals(1, result.getData().getExitCode());
        }
    }

    @Nested
    @DisplayName("Stream Method Tests")
    class StreamMethodTests {

        @Test
        @DisplayName("test_execute_cmd_stream_method_exists")
        void testExecuteCmdStreamMethodExists() {
            BaseShellOperation shellOp = (BaseShellOperation) sysOp.shell();
            
            assertNotNull(shellOp);
            // The method exists but throws UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class, () -> {
                shellOp.executeCmdStream("echo test");
            });
        }
    }

    @Nested
    @DisplayName("No WorkDir Configuration Tests")
    class NoWorkDirTests {

        @Test
        @DisplayName("test_shell_resolve_cwd_without_work_dir")
        @DisabledOnOs(OS.WINDOWS)
        void testShellResolveCwdWithoutWorkDirUnix() throws ExecutionException, InterruptedException {
            // No work_dir configured
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(null)
                .shellAllowlist(null)
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_no_workdir")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation op = new SysOperation(card);

            BaseShellOperation shellOp = (BaseShellOperation) op.shell();
            ExecuteCmdResult result = shellOp.executeCmd("pwd").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            // Should use current process working directory
            String currentCwd = System.getProperty("user.dir").toLowerCase();
            String actualCwd = result.getData().getStdout().trim().toLowerCase();
            // On macOS, /var is a symlink to /private/var
            assertTrue(currentCwd.endsWith(actualCwd) || actualCwd.endsWith(currentCwd) 
                || currentCwd.contains(actualCwd) || actualCwd.contains(currentCwd));
        }
    }
}

