/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test shell operations through sandbox routing.
 * <p>
 * Mirrors Python's {@code test_shell.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_shell.py}.
 */
class TestShell extends BaseSandboxTest {

    @Test
    void testShellBasicExecution() {
        var res = sysOp.shell().executeCmd("echo hello world", null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().strip().contains("hello world"));
        assertEquals(0, res.getData().getExitCode());
        assertEquals("echo hello world", res.getData().getCommand());

        var list = sysOp.shell().executeCmd("ls -la", null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), list.getCode());
        assertNotNull(list.getData());
        assertTrue(list.getData().getStdout().contains("file1.txt"));
        assertEquals(0, list.getData().getExitCode());
    }

    @Test
    void testShellEnvironmentVariables() {
        var res = sysOp.shell().executeCmd(
                "echo $TEST_VAR", null, 300, Map.of("TEST_VAR", "custom_value"), null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().strip().contains("custom_value"));
    }

    @Test
    void testShellCwd() {
        var res = sysOp.shell().executeCmd("pwd", "/tmp/subdir", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertEquals("/tmp/subdir", res.getData().getCwd());
        assertTrue(res.getData().getStdout().strip().contains("/tmp/subdir"));
    }

    @Test
    void testShellTimeout() {
        var res = sysOp.shell().executeCmd("python -c \"import time; time.sleep(5)\"", null, 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(res.getData());
        assertEquals(-1, res.getData().getExitCode());
    }

    @Test
    void testShellPingTimeout() {
        var res = sysOp.shell().executeCmd("ping 127.0.0.1", null, 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().contains("127.0.0.1"));
        assertEquals(-1, res.getData().getExitCode());
    }

    @Test
    void testShellListTools() {
        var tools = sysOp.shell().listTools();

        assertEquals(3, tools.size());
        List<String> toolNames = tools.stream().map(tool -> tool.getName()).toList();
        assertTrue(toolNames.contains("executeCmd"));
        assertTrue(toolNames.contains("executeCmdStream"));
        assertTrue(toolNames.contains("executeCmdBackground"));

        var execTool = tools.stream().filter(tool -> "executeCmd".equals(tool.getName())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) execTool.getInputParams().get("properties");
        assertTrue(properties.containsKey("command"));
        assertEquals(List.of("command"), execTool.getInputParams().get("required"));
    }

    @Test
    void testExecuteCmdStreamBasic() {
        List<ExecuteCmdStreamResult> streamResults = collect(
                sysOp.shell().executeCmdStream("echo chunk1; echo chunk2; echo error_chunk 1>&2", null, 300, null, null));

        assertTrue(streamResults.size() > 0);
        assertTrue(streamResults.stream().allMatch(result -> result instanceof ExecuteCmdStreamResult));

        List<ExecuteCmdChunkData> stdoutChunks = streamResults.stream()
                .map(ExecuteCmdStreamResult::getData)
                .filter(data -> "stdout".equals(data.getType()) && data.getExitCode() == null)
                .toList();
        List<ExecuteCmdChunkData> stderrChunks = streamResults.stream()
                .map(ExecuteCmdStreamResult::getData)
                .filter(data -> "stderr".equals(data.getType()))
                .toList();
        ExecuteCmdChunkData exitChunk = streamResults.stream()
                .map(ExecuteCmdStreamResult::getData)
                .filter(data -> data.getExitCode() != null)
                .findFirst()
                .orElse(null);

        String stdout = stdoutChunks.stream().map(ExecuteCmdChunkData::getText).reduce("", String::concat);
        assertTrue(stdout.contains("chunk1"));
        assertTrue(stdout.contains("chunk2"));
        assertTrue(stderrChunks.size() >= 1);
        assertTrue(stderrChunks.get(0).getText().contains("error_chunk"));
        assertNotNull(exitChunk);
        assertEquals(0, exitChunk.getExitCode());
        assertEquals(streamResults.size() - 1, exitChunk.getChunkIndex());
    }

    @Test
    void testExecuteCmdStreamTimeout() {
        List<ExecuteCmdStreamResult> streamResults = collect(
                sysOp.shell().executeCmdStream("sleep 10", null, 1, null, null));

        assertEquals(1, streamResults.size());
        ExecuteCmdStreamResult errorResult = streamResults.get(0);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), errorResult.getCode());
        assertTrue(errorResult.getMessage().toLowerCase().contains("timeout"));
        assertEquals(-1, errorResult.getData().getExitCode());
    }

    @Test
    void testExecuteCmdStreamEmptyCommand() {
        List<ExecuteCmdStreamResult> streamResults = collect(
                sysOp.shell().executeCmdStream("", null, 300, null, null));

        assertEquals(1, streamResults.size());
        ExecuteCmdStreamResult errorResult = streamResults.get(0);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), errorResult.getCode());
        assertTrue(errorResult.getMessage().contains("command can not be empty"));
        assertEquals(0, errorResult.getData().getChunkIndex());
        assertEquals(-1, errorResult.getData().getExitCode());
    }

    @Test
    void testExecuteCmdStreamContinuousOutput() {
        List<ExecuteCmdStreamResult> streamResults = collect(
                sysOp.shell().executeCmdStream("ping -c 3 127.0.0.1", null, 10, null, null));

        List<ExecuteCmdStreamResult> stdoutChunks = streamResults.stream()
                .filter(result -> result.getData() != null
                        && "stdout".equals(result.getData().getType())
                        && result.getData().getExitCode() == null)
                .toList();
        assertTrue(stdoutChunks.size() >= 1);
        String stdout = stdoutChunks.stream()
                .map(result -> result.getData().getText())
                .reduce("", String::concat);
        assertTrue(stdout.contains("127.0.0.1"));

        ExecuteCmdStreamResult exitChunk = streamResults.stream()
                .filter(result -> result.getData() != null && result.getData().getExitCode() != null)
                .findFirst()
                .orElse(null);
        assertNotNull(exitChunk);
        assertEquals(0, exitChunk.getData().getExitCode());
    }

    private static List<ExecuteCmdStreamResult> collect(Iterator<ExecuteCmdStreamResult> iterator) {
        List<ExecuteCmdStreamResult> results = new ArrayList<>();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }
        return results;
    }
}
