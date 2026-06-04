/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.test_bash;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.harness.tools.BashTool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-style unit tests for the enhanced BashTool.
 *
 * <p>Mirrors Python's {@code test_bash_tool.py} in
 * {@code tests.unit_tests.harness.tools.test_bash}.
 */
class TestBashTool {

    private SysOperation sysOperation;
    private BaseShellOperation shell;

    @BeforeEach
    void setUp() {
        sysOperation = mock(SysOperation.class);
        shell = mock(BaseShellOperation.class);
        when(sysOperation.shell()).thenReturn(shell);
    }

    @Test
    void testEcho() {
        whenExecute("echo hello", result("hello\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "echo hello"));

        assertTrue(output.isSuccess());
        assertTrue(data(output).get("stdout").toString().contains("hello"));
        assertEquals(0, data(output).get("exit_code"));
        assertNull(output.getError());
    }

    @Test
    void testExit1IsError() {
        whenExecute("echo fail && exit 1", result("fail\n", "boom", 1));

        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "echo fail && exit 1"));

        assertFalse(output.isSuccess());
        assertEquals(1, data(output).get("exit_code"));
    }

    @Test
    void testGrepNoMatchIsNotError() {
        whenExecute("echo hello | grep nonexistent_pattern_xyz", result("", "", 1));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "echo hello | grep nonexistent_pattern_xyz"));

        assertTrue(output.isSuccess());
        assertEquals(1, data(output).get("exit_code"));
        assertEquals("No matches found", data(output).get("return_code_interpretation"));
    }

    @Test
    void testGrepMatchSuccess() {
        whenExecute("echo hello | grep hello", result("hello\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "echo hello | grep hello"));

        assertTrue(output.isSuccess());
        assertEquals(0, data(output).get("exit_code"));
        assertTrue(data(output).get("stdout").toString().contains("hello"));
    }

    @Test
    void testSilentFlag() {
        whenExecute("mkdir -p /tmp/sub", result("", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "mkdir -p /tmp/sub"));

        assertTrue(output.isSuccess());
        assertEquals(true, data(output).get("no_output_expected"));
    }

    @Test
    void testDestructiveWarningPresent() {
        whenExecute("git commit --amend -m test", result("", "not a git repo", 1));

        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "git commit --amend -m test"));

        assertNotNull(data(output).get("destructive_warning"));
        assertTrue(data(output).get("destructive_warning").toString().toLowerCase().contains("rewrite"));
    }

    @Test
    void testInjectionBacktickBlocked() {
        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "echo `whoami`"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().toLowerCase().contains("injection"));
        verify(sysOperation, never()).shell();
    }

    @Test
    void testInjectionDollarParenBlocked() {
        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "echo $(id)"));

        assertFalse(output.isSuccess());
        verify(sysOperation, never()).shell();
    }

    @Test
    void testWorkdirNonexistentDirFails() {
        when(shell.executeCmd(anyString(), anyString(), anyInt(), anyMap(), anyMap()))
                .thenReturn(ExecuteCmdResult.failure("unexpected error: missing cwd"));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "echo hi", "workdir", "/definitely/does/not/exist"));

        assertFalse(output.isSuccess());
        assertNotNull(output.getError());
    }

    @Test
    void testSmartTruncation() {
        whenExecute("python -c print", result("x".repeat(500), "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "python -c print", "max_output_chars", 250));

        assertTrue(output.isSuccess());
        assertTrue(data(output).get("stdout").toString().contains("lines omitted"));
    }

    @Test
    void testNoTruncationWithinLimit() {
        whenExecute("echo hello", result("hello\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "echo hello", "max_output_chars", 8000));

        assertTrue(output.isSuccess());
        assertFalse(data(output).get("stdout").toString().contains("omitted"));
    }

    @Test
    void testBackgroundPid() {
        when(shell.executeCmdBackground("sleep 5", "", "bash"))
                .thenReturn(ExecuteCmdBackgroundResult.success(ExecuteCmdBackgroundData.builder()
                        .command("sleep 5")
                        .cwd(".")
                        .pid(1234L)
                        .build()));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "sleep 5", "run_in_background", true));

        assertTrue(output.isSuccess());
        assertEquals(1234, data(output).get("pid"));
    }

    @Test
    void testDescriptionAccepted() {
        whenExecute("echo ok", result("ok\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "echo ok", "description", "Check connectivity"));

        assertTrue(output.isSuccess());
    }

    @Test
    void testReadOnlyModeAllowsRead() {
        whenExecute("ls -la", result("total 0\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation, "read_only"), Map.of("command", "ls -la"));

        assertTrue(output.isSuccess());
    }

    @Test
    void testReadOnlyModeBlocksWrite() {
        ToolOutput output = invoke(new BashTool(sysOperation, "read_only"),
                Map.of("command", "touch /tmp/test_file"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("Read-only"));
        verify(sysOperation, never()).shell();
    }

    @Test
    void testAcceptEditsModeAllowsFileOps() {
        whenExecute("mkdir -p /tmp/sub", result("", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation, "accept_edits"),
                Map.of("command", "mkdir -p /tmp/sub"));

        assertTrue(output.isSuccess());
    }

    @Test
    void testDenyPatterns() {
        ToolOutput output = invoke(new BashTool(sysOperation, "auto", List.of("\\bsudo\\b"), null),
                Map.of("command", "sudo echo hi"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().toLowerCase().contains("denied"));
    }

    @Test
    void testAllowPatternsOverride() {
        whenExecute("echo ok && mkdir -p /tmp/_test_perm_override", result("ok\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation, "read_only", null, List.of("^echo\\s.*&&\\s*mkdir")),
                Map.of("command", "echo ok && mkdir -p /tmp/_test_perm_override"));

        assertTrue(output.isSuccess());
        assertFalse(String.valueOf(output.getError()).contains("Read-only"));
    }

    @Test
    void testLargeOutputPersisted(@TempDir Path tempDir) throws Exception {
        whenExecute("python -c big", result("x".repeat(50000), "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation),
                Map.of("command", "python -c big", "max_output_chars", 1000));

        assertTrue(output.isSuccess());
        assertNotNull(data(output).get("persisted_output_path"));
        assertTrue((Integer) data(output).get("persisted_output_size") > 0);
        assertTrue(Files.isRegularFile(Path.of(data(output).get("persisted_output_path").toString())));
    }

    @Test
    void testSmallOutputNotPersisted() {
        whenExecute("echo hello", result("hello\n", "", 0));

        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", "echo hello"));

        assertTrue(output.isSuccess());
        assertNull(data(output).get("persisted_output_path"));
        assertNull(data(output).get("persisted_output_size"));
    }

    @Test
    void testEmptyCommand() {
        ToolOutput output = invoke(new BashTool(sysOperation), Map.of("command", ""));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("empty"));
    }

    private void whenExecute(String command, ExecuteCmdResult result) {
        when(shell.executeCmd(command, "", 300, Map.of(), Map.of())).thenReturn(result);
    }

    private ExecuteCmdResult result(String stdout, String stderr, int exitCode) {
        return new ExecuteCmdResult(StatusCode.SUCCESS.getCode(), "ok", ExecuteCmdData.builder()
                .command("cmd")
                .cwd(".")
                .stdout(stdout)
                .stderr(stderr)
                .exitCode(exitCode)
                .build());
    }

    private ToolOutput invoke(BashTool tool, Map<String, Object> inputs) {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }
}
