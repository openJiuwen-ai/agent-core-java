/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.testsupport.OsTestSupport;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Tests for LocalShellOperation.
 * Mirrors Python's test_shell_operation.py test cases.
 */
class LocalShellOperationTest {
    @TempDir
    Path workDir;

    private SysOperation sysOp;

    @BeforeEach
    void setUp() {
        LocalWorkConfig config = LocalWorkConfig.builder().workDir(workDir.toString()).shellAllowlist(null).build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_shell_op");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        sysOp = new SysOperation(card);
    }

    private BaseShellOperation shell() {
        return sysOp.shell();
    }

    private List<ExecuteCmdStreamResult> collectStreamResults(Iterator<ExecuteCmdStreamResult> it) {
        List<ExecuteCmdStreamResult> results = new ArrayList<>();
        while (it.hasNext()) {
            results.add(it.next());
        }
        return results;
    }

    // isPythonAvailable removed — timeout tests use platform-native long-running commands

    // ==================== executeCmd Test Cases ====================

    @Test
    @DisplayName("Basic shell echo command")
    void testShellBasicEcho() {
        ExecuteCmdResult res = shell().executeCmd("echo hello world", null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().trim().contains("hello world"));
        assertEquals(0, res.getData().getExitCode());
        assertEquals("echo hello world", res.getData().getCommand());
    }

    @Test
    @DisplayName("Platform specific list directory command")
    void testShellListDir() {
        String cmd = OsTestSupport.isWindows() ? "dir" : "ls -la";
        ExecuteCmdResult res = shell().executeCmd(cmd, null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().trim().length() > 0);
        assertEquals(0, res.getData().getExitCode());
    }

    @Test
    @DisplayName("Shell with environment variables")
    void testShellEnvironmentVariables() {
        Map<String, String> env = Map.of("TEST_VAR", "custom_value");
        String cmd = OsTestSupport.isWindows() ? "echo %TEST_VAR%" : "echo $TEST_VAR";
        ExecuteCmdResult res = shell().executeCmd(cmd, null, 300, env, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().trim().contains("custom_value"));
    }

    @Test
    @DisplayName("Shell with absolute cwd")
    void testShellCwdAbsolute() {
        File subdir = new File(workDir.toFile(), "subdir");
        subdir.mkdirs();

        String cmd = OsTestSupport.isWindows() ? "echo %CD%" : "pwd";
        ExecuteCmdResult res = shell().executeCmd(cmd, subdir.getAbsolutePath(), 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().trim().toLowerCase().contains("subdir"));
    }

    @Test
    @DisplayName("Shell with relative cwd")
    void testShellCwdRelative() {
        File subdir = new File(workDir.toFile(), "rel_subdir");
        subdir.mkdirs();

        String cmd = OsTestSupport.isWindows() ? "echo %CD%" : "pwd";
        ExecuteCmdResult res = shell().executeCmd(cmd, "rel_subdir", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().trim().toLowerCase().contains("rel_subdir"));
    }

    @Test
    @DisplayName("Shell defaults to workDir when no cwd provided")
    void testShellDefaultCwd() {
        String cmd = OsTestSupport.isWindows() ? "echo %CD%" : "pwd";
        ExecuteCmdResult res = shell().executeCmd(cmd, null, 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        String actualOut = res.getData().getStdout().trim().toLowerCase();
        String expected = workDir.toAbsolutePath().toString().toLowerCase();
        assertTrue(actualOut.contains(expected) || expected.contains(actualOut),
                "Expected cwd to be workDir but got: " + actualOut);
    }

    @Test
    @DisplayName("Shell command timeout")
    void testShellTimeout() throws Exception {
        // Use a separate cwd so @TempDir cleanup is not blocked by the timed-out process on Windows.
        Path separateDir = java.nio.file.Files.createTempDirectory("shell-timeout");
        try {
            LocalWorkConfig cfg =
                LocalWorkConfig.builder().workDir(separateDir.toString()).shellAllowlist(null).build();
            SysOperationCard card = new SysOperationCard();
            card.setId("shell_timeout_test");
            card.setMode(OperationMode.LOCAL);
            card.setWorkConfig(cfg);
            SysOperation localOp = new SysOperation(card);

            String cmd = OsTestSupport.isWindows() ? "ping -n 30 127.0.0.1" : "sleep 30";
            ExecuteCmdResult res = localOp.shell().executeCmd(cmd, null, 1, null, null);

            assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
            assertTrue(res.getMessage().toLowerCase().contains("timeout"));
            assertNotNull(res.getData());
        } finally {
            Thread.sleep(500);
            try {
                java.nio.file.Files.walk(separateDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        java.nio.file.Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Shell allowlist enforcement")
    void testShellAllowlist() {
        // Create operation with restricted allowlist
        LocalWorkConfig config = LocalWorkConfig.builder()
                .shellAllowlist(List.of("echo", OsTestSupport.cwdCommand())).workDir(workDir.toString()).build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_allowlist");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        SysOperation restrictedOp = new SysOperation(card);

        // Allowed command
        String allowedCmd = OsTestSupport.isWindows() ? "echo hello" : OsTestSupport.cwdCommand();
        ExecuteCmdResult allowedRes = restrictedOp.shell().executeCmd(allowedCmd, null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), allowedRes.getCode());

        // Denied command
        ExecuteCmdResult deniedRes = restrictedOp.shell().executeCmd("dir", null, 300, null, null);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), deniedRes.getCode());
        assertTrue(deniedRes.getMessage().contains("not allowed"));
    }

    @Test
    @DisplayName("Shell dangerous patterns are blocked")
    void testShellDangerousPatterns() {
        LocalWorkConfig config = LocalWorkConfig.builder().shellAllowlist(null)
                .dangerousPatterns(List.of("rm\\s+-rf", "shutdown")).workDir(workDir.toString()).build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_dangerous_patterns");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        SysOperation restrictedOp = new SysOperation(card);

        ExecuteCmdResult deniedRes = restrictedOp.shell().executeCmd("rm -rf /tmp/demo", null, 300, null, null);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), deniedRes.getCode());
        assertTrue(deniedRes.getMessage().contains("dangerous pattern"));
    }

    @Test
    @DisplayName("Shell restrictToSandbox denies cwd outside sandbox roots")
    void testShellRestrictToSandboxRejectsOutsideCwd() {
        Path sandboxRoot = workDir.resolve("sandbox");
        Path cwdInside = sandboxRoot.resolve("work");
        Path cwdOutside = workDir.resolve("outside");
        assertTrue(cwdInside.toFile().mkdirs());
        assertTrue(cwdOutside.toFile().mkdirs());

        LocalWorkConfig config = LocalWorkConfig.builder().workDir(cwdInside.toString())
                .sandboxRoot(List.of(sandboxRoot.toString())).restrictToSandbox(true).shellAllowlist(null).build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_shell_sandbox_root");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        SysOperation restrictedOp = new SysOperation(card);

        ExecuteCmdResult deniedRes = restrictedOp.shell().executeCmd(OsTestSupport.cwdCommand(), cwdOutside.toString(), 300, null, null);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), deniedRes.getCode());
        assertTrue(deniedRes.getMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("Shell list tools")
    void testShellListTools() {
        var tools = shell().listTools();
        assertEquals(3, tools.size());
        var toolNames = tools.stream().map(t -> t.getName()).toList();
        assertTrue(toolNames.contains("executeCmd"));
        assertTrue(toolNames.contains("executeCmdStream"));
        assertTrue(toolNames.contains("executeCmdBackground"));
    }

    @Test
    @DisplayName("Shell empty command returns error")
    void testShellEmptyCommand() {
        ExecuteCmdResult res = shell().executeCmd("", null, 300, null, null);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("command can not be empty"));
    }

    @Test
    @DisplayName("Shell background execution returns pid")
    void testShellBackgroundExecution() {
        String cmd = OsTestSupport.shortBackgroundWaitCommand();
        var result = shell().executeCmdBackground(cmd, null, null, 0, null);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getPid());
        assertTrue(result.getData().getPid() > 0);
        OsTestSupport.destroyProcessTree(result.getData().getPid());
    }

    @Test
    @DisplayName("Shell background execution rejects empty command")
    void testShellBackgroundRejectsEmptyCommand() {
        var result = shell().executeCmdBackground("", null, null, 0.1, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("command can not be empty"));
    }

    @Test
    @DisplayName("Shell options can request an explicit shell type")
    void testShellTypeOption() {
        String shellType = OsTestSupport.isWindows() ? "cmd" : "bash";
        ExecuteCmdResult res =
            shell().executeCmd("echo shell-ok", null, 300, null, Map.of("shell_type", shellType));

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(shellType, res.getData().getShellType());
        assertTrue(res.getData().getStdout().contains("shell-ok"));
    }

    // ==================== executeCmdStream Test Cases ====================

    @Test
    @DisplayName("Stream: basic streaming execution")
    void testStreamBasic() {
        String cmd;
        if (OsTestSupport.isWindows()) {
            cmd = "echo chunk1 && echo chunk2 && echo error_chunk 1>&2";
        } else {
            cmd = "echo chunk1; sleep 0.01; echo chunk2; sleep 0.01; echo error_chunk 1>&2";
        }

        List<ExecuteCmdStreamResult> results =
            collectStreamResults(shell().executeCmdStream(cmd, null, 10, null, null));

        assertTrue(results.size() > 0, "At least one result");

        // Collect stdout
        String stdoutContent =
            results.stream().filter(r -> r.getData() != null && "stdout".equals(r.getData().getType()))
                    .map(r -> r.getData().getText()).reduce("", String::concat);
        assertTrue(stdoutContent.contains("chunk1"));
        assertTrue(stdoutContent.contains("chunk2"));

        // Collect stderr
        String stderrContent =
            results.stream().filter(r -> r.getData() != null && "stderr".equals(r.getData().getType()))
                    .map(r -> r.getData().getText()).reduce("", String::concat);
        assertTrue(stderrContent.contains("error_chunk"));

        // Validate exit event
        ExecuteCmdStreamResult exitResult =
            results.stream().filter(r -> r.getData() != null && r.getData().getExitCode() != null).reduce((a, b) -> b) // last
                                                                                                                       // one
                                                                                                                       // with
                                                                                                                       // exitCode
                    .orElse(null);
        assertNotNull(exitResult, "Exit chunk should exist");
        assertEquals(0, exitResult.getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: timeout")
    void testStreamTimeout() throws Exception {
        // Use a separate working directory to avoid TempDir cleanup issues
        // when the timed-out process still holds file handles on Windows
        Path separateDir = java.nio.file.Files.createTempDirectory("shell-stream-timeout");
        try {
            LocalWorkConfig cfg =
                LocalWorkConfig.builder().workDir(separateDir.toString()).shellAllowlist(null).build();
            SysOperationCard card = new SysOperationCard();
            card.setId("stream_timeout_test");
            card.setMode(OperationMode.LOCAL);
            card.setWorkConfig(cfg);
            SysOperation localOp = new SysOperation(card);

            String cmd = OsTestSupport.isWindows() ? "ping -n 10 127.0.0.1" : "sleep 10";
            List<ExecuteCmdStreamResult> results =
                collectStreamResults(localOp.shell().executeCmdStream(cmd, null, 1, null, null));

            boolean hasTimeout =
                results.stream().anyMatch(r -> r.getCode() == StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
            assertTrue(hasTimeout, "Should have timeout error");
            ExecuteCmdStreamResult errorResult =
                results.stream().filter(r -> r.getCode() == StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode())
                        .findFirst().orElse(null);
            assertNotNull(errorResult);
            assertTrue(errorResult.getMessage().toLowerCase().contains("timeout"));
        } finally {
            // Give the OS time to release file handles after process kill
            Thread.sleep(1000);
            try {
                java.nio.file.Files.walk(separateDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        java.nio.file.Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Stream: empty command returns error")
    void testStreamEmptyCommand() {
        List<ExecuteCmdStreamResult> results =
            collectStreamResults(shell().executeCmdStream("", null, 300, null, null));
        assertEquals(1, results.size());
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), results.get(0).getCode());
        assertTrue(results.get(0).getMessage().contains("command can not be empty"));
        assertEquals(0, results.get(0).getData().getChunkIndex());
        assertEquals(-1, results.get(0).getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: allowlist blocked command")
    void testStreamAllowlist() {
        LocalWorkConfig config =
            LocalWorkConfig.builder().shellAllowlist(List.of("echo")).workDir(workDir.toString()).build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_stream_allowlist");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        SysOperation restrictedOp = new SysOperation(card);

        // Allowed
        List<ExecuteCmdStreamResult> allowedResults =
            collectStreamResults(restrictedOp.shell().executeCmdStream("echo allowed", null, 10, null, null));
        boolean hasAllowed = allowedResults.stream().anyMatch(r -> r.getData() != null
                && "stdout".equals(r.getData().getType()) && r.getData().getText().contains("allowed"));
        assertTrue(hasAllowed);

        // Denied
        String denyCmd = OsTestSupport.isWindows() ? "dir" : "ls";
        List<ExecuteCmdStreamResult> denyResults =
            collectStreamResults(restrictedOp.shell().executeCmdStream(denyCmd, null, 300, null, null));
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), denyResults.get(0).getCode());
        assertTrue(denyResults.get(0).getMessage().contains("not allowed by allowlist"));
    }

    @Test
    @DisplayName("Stream: continuous output (ping)")
    void testStreamContinuousOutput() {
        String cmd = OsTestSupport.isWindows() ? "ping -n 3 127.0.0.1" : "ping -c 3 127.0.0.1";
        List<ExecuteCmdStreamResult> results =
            collectStreamResults(shell().executeCmdStream(cmd, null, 15, null, null));

        List<ExecuteCmdStreamResult> stdoutChunks =
            results.stream().filter(r -> r.getData() != null && "stdout".equals(r.getData().getType())).toList();
        assertTrue(stdoutChunks.size() >= 1);

        String combined = stdoutChunks.stream().map(r -> r.getData().getText()).reduce("", String::concat);
        assertTrue(combined.contains("127.0.0.1"));

        // Validate exit code
        ExecuteCmdStreamResult exitChunk = results.stream()
                .filter(r -> r.getData() != null && r.getData().getExitCode() != null).reduce((a, b) -> b).orElse(null);
        assertNotNull(exitChunk);
        assertEquals(0, exitChunk.getData().getExitCode());
    }

    @Test
    @DisplayName("Stream: hasNext is idempotent before next (lazy iterator)")
    void testStreamHasNextIsIdempotentBeforeNext() {
        // 修复后 executeCmdStream 返回真正惰性的迭代器：重复调用 hasNext() 不应消费事件，
        // 也不应阻塞。EXIT 事件产出后 hasNext() 必须立即返回 false，避免下游死循环。
        String cmd = OsTestSupport.isWindows() ? "echo hello" : "echo hello";
        Iterator<ExecuteCmdStreamResult> it =
                shell().executeCmdStream(cmd, null, 10, null, null);

        // 多次 hasNext() 不消费
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        ExecuteCmdStreamResult first = it.next();
        assertNotNull(first);

        // 消费完剩余事件
        List<ExecuteCmdStreamResult> rest = new ArrayList<>();
        while (it.hasNext()) {
            rest.add(it.next());
        }

        // 终止后再次 hasNext() 必须返回 false（不抛异常、不阻塞）
        assertFalse(it.hasNext(), "hasNext must return false after iterator exhausted");
        assertFalse(it.hasNext(), "repeated hasNext on exhausted iterator stays false");

        // 至少产出一个 EXIT 事件
        List<ExecuteCmdStreamResult> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        assertTrue(all.stream().anyMatch(r -> r.getData() != null && r.getData().getExitCode() != null),
                "should contain an EXIT event");
    }

    @Test
    @DisplayName("Stream: empty command iterator terminates after single ERROR chunk")
    void testStreamEmptyCommandIteratorTerminatesAfterError() {
        // 参数校验失败时返回单元素迭代器：取完一个 ERROR chunk 后 hasNext() 必须返回 false，
        // 防止下游 while(it.hasNext()) 死循环（对应 advanceIfNeeded catch 块的 hasNext=false 修复）
        Iterator<ExecuteCmdStreamResult> it =
                shell().executeCmdStream("", null, 300, null, null);

        assertTrue(it.hasNext());
        ExecuteCmdStreamResult err = it.next();
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), err.getCode());
        assertTrue(err.getMessage().contains("command can not be empty"));

        // 取完 ERROR 后必须终止
        assertFalse(it.hasNext(), "iterator must terminate after the single ERROR chunk");
        assertFalse(it.hasNext());
    }
}
