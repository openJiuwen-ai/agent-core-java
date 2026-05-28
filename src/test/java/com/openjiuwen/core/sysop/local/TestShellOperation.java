/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test shell operations.
 * <p>
 * Mirrors Python's {@code test_shell_operation.py} in
 * {@code tests/unit_tests/core/sys_operation/local/test_shell_operation.py}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestShellOperation {

    @TempDir
    Path tempDir;

    private static final String CARD_ID = "test_shell_op";
    private SysOperationCard card;
    private SysOperation sysOp;
    private ResourceMgr rm;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        rm = Runner.resourceMgr();

        card = SysOperationCard.builder()
                .id(CARD_ID)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder()
                        .shellAllowlist(null)
                        .workDir(tempDir.toString())
                        .build())
                .build();

        var addRes = rm.addSysOperation(card, null);
        assertTrue(addRes.isOk(), "Failed to add sys operation");

        Object result = rm.getSysOperation(CARD_ID, null, TagMatchStrategy.ALL);
        sysOp = extractSysOperation(result);
        assertNotNull(sysOp, "SysOperation should be retrieved");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rm != null && card != null) {
            rm.removeSysOperation(CARD_ID, null, TagMatchStrategy.ALL, true);
        }
        Runner.stop();
    }

    @Test
    @Order(1)
    void testShellBasicExecution() {
        BaseShellOperation shell = sysOp.shell();

        ExecuteCmdResult res = shell.executeCmd("echo hello world", null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertNotNull(res.getData());
        assertTrue(res.getData().getStdout().strip().contains("hello world"));
        assertEquals(0, res.getData().getExitCode());
        assertEquals("echo hello world", res.getData().getCommand());

        String cmd = isWindows() ? "dir" : "ls -la";
        ExecuteCmdResult res2 = shell.executeCmd(cmd, null, 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res2.getCode());
        assertNotNull(res2.getData());
        assertTrue(res2.getData().getStdout().strip().length() > 0);
        assertEquals(0, res2.getData().getExitCode());
    }

    @Test
    @Order(2)
    void testShellEnvironmentVariables() {
        BaseShellOperation shell = sysOp.shell();

        Map<String, String> env = new HashMap<>();
        env.put("TEST_VAR", "custom_value");

        String cmd = isWindows() ? "echo %TEST_VAR%" : "echo $TEST_VAR";
        ExecuteCmdResult res = shell.executeCmd(cmd, null, 300, env, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().strip().contains("custom_value"));
    }

    @Test
    @Order(3)
    void testShellCwd() throws Exception {
        BaseShellOperation shell = sysOp.shell();

        Path subdir = tempDir.resolve("subdir");
        Files.createDirectories(subdir);

        String cmd = isWindows() ? "echo %CD%" : "pwd";

        ExecuteCmdResult res = shell.executeCmd(cmd, subdir.toString(), 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().strip().contains("subdir"));

        ExecuteCmdResult res2 = shell.executeCmd(cmd, "subdir", 300, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res2.getCode());
        assertTrue(res2.getData().getStdout().strip().contains("subdir"));
    }

    @Test
    @Order(4)
    void testShellDefaultCwd() {
        BaseShellOperation shell = sysOp.shell();

        String cmd = isWindows() ? "echo %CD%" : "pwd";
        ExecuteCmdResult res = shell.executeCmd(cmd, null, 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        String actualOut = res.getData().getStdout().strip().toLowerCase();
        String expected = tempDir.toString().toLowerCase();
        assertTrue(expected.contains(actualOut) || actualOut.contains(expected));
    }

    @Test
    @Order(5)
    void testShellRelativeCwd() throws Exception {
        BaseShellOperation shell = sysOp.shell();

        String subdirName = "rel_subdir";
        Path subdirPath = tempDir.resolve(subdirName);
        Files.createDirectories(subdirPath);

        String cmd = isWindows() ? "echo %CD%" : "pwd";
        ExecuteCmdResult res = shell.executeCmd(cmd, subdirName, 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getStdout().strip().toLowerCase().contains(subdirName.toLowerCase()));
    }

    @Test
    @Order(6)
    void testShellTimeout() {
        BaseShellOperation shell = sysOp.shell();

        String cmdSleep = "python -c \"import time; time.sleep(5)\"";
        ExecuteCmdResult res = shell.executeCmd(cmdSleep, null, 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
    }

    @Test
    @Order(7)
    void testShellPingTimeout() {
        BaseShellOperation shell = sysOp.shell();

        String cmdPing = "ping 127.0.0.1";
        ExecuteCmdResult res = shell.executeCmd(cmdPing, null, 1, null, null);

        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().toLowerCase().contains("timeout"));
        assertNotNull(res.getData());
        assertTrue(
                (res.getData().getStdout().contains("127.0.0.1")) ||
                (res.getData().getExitCode() != 0)
        );
    }

    @Test
    @Order(8)
    void testShellAllowlist() throws Exception {
        Runner.start();
        ResourceMgr rm = Runner.resourceMgr();

        try {
            String cardId = "test_allowlist";
            List<String> allowlist = Arrays.asList("echo", "pwd");
            SysOperationCard allowlistCard = SysOperationCard.builder()
                    .id(cardId)
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder()
                            .shellAllowlist(allowlist)
                            .workDir(tempDir.toString())
                            .build())
                    .build();

            var addRes = rm.addSysOperation(allowlistCard, null);
            assertTrue(addRes.isOk());

            Object result = rm.getSysOperation(cardId, null, TagMatchStrategy.ALL);
            SysOperation op = extractSysOperation(result);

            String cmd = isWindows() ? "echo %CD%" : "pwd";
            ExecuteCmdResult res = op.shell().executeCmd(cmd, null, 300, null, null);
            assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

            ExecuteCmdResult resDeny = op.shell().executeCmd("dir", null, 300, null, null);
            assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), resDeny.getCode());
            assertTrue(resDeny.getMessage().toLowerCase().contains("not allowed"));

            rm.removeSysOperation(cardId, null, TagMatchStrategy.ALL, true);
        } finally {
            Runner.stop();
        }
    }

    @Test
    @Order(9)
    void testShellListTools() {
        BaseShellOperation shell = sysOp.shell();
        var tools = shell.listTools();

        assertEquals(2, tools.size());

        List<String> toolNames = new ArrayList<>();
        for (var tool : tools) {
            toolNames.add(tool.getName());
        }
        assertTrue(toolNames.contains("executeCmd"));
        assertTrue(toolNames.contains("executeCmdStream"));

        var execTool = tools.stream()
                .filter(t -> "executeCmd".equals(t.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(execTool);

        Map<String, Object> props = execTool.getInputParams();
        Map<String, Object> properties = (Map<String, Object>) props.get("properties");
        assertTrue(properties.containsKey("command"));

        List<String> required = (List<String>) props.get("required");
        assertTrue(required.contains("command"));
    }

    @Test
    @Order(10)
    void testExecuteCmdStreamBasic() {
        BaseShellOperation shell = sysOp.shell();

        String cmd = isWindows()
                ? "echo chunk1 && echo chunk2 && echo error_chunk 1>&2"
                : "echo chunk1; sleep 0.01; echo chunk2; sleep 0.01; echo error_chunk 1>&2";

        List<ExecuteCmdStreamResult> streamResults = new ArrayList<>();
        Iterator<ExecuteCmdStreamResult> iterator = shell.executeCmdStream(cmd, null, 300, null, null);
        while (iterator.hasNext()) {
            streamResults.add(iterator.next());
        }

        assertTrue(streamResults.size() > 0, "At least one streaming result should be returned");

        StringBuilder stdoutContent = new StringBuilder();
        for (ExecuteCmdStreamResult r : streamResults) {
            if (r.getData() != null && "stdout".equals(r.getData().getType())) {
                stdoutContent.append(r.getData().getText());
            }
        }
        assertTrue(stdoutContent.toString().contains("chunk1"),
                "'chunk1' not found in stdout: " + stdoutContent);
        assertTrue(stdoutContent.toString().contains("chunk2"),
                "'chunk2' not found in stdout: " + stdoutContent);

        StringBuilder stderrContent = new StringBuilder();
        for (ExecuteCmdStreamResult r : streamResults) {
            if (r.getData() != null && "stderr".equals(r.getData().getType())) {
                stderrContent.append(r.getData().getText());
            }
        }
        assertTrue(stderrContent.toString().contains("error_chunk"),
                "'error_chunk' not found in stderr: " + stderrContent);

        ExecuteCmdChunkData exitChunk = null;
        for (ExecuteCmdStreamResult r : streamResults) {
            if (r.getData() != null && r.getData().getExitCode() != null) {
                exitChunk = r.getData();
            }
        }
        assertNotNull(exitChunk, "Exit chunk not found");
        assertEquals(0, exitChunk.getExitCode());
    }

    @Test
    @Order(11)
    void testExecuteCmdStreamTimeout() {
        BaseShellOperation shell = sysOp.shell();

        String cmd = isWindows() ? "ping -n 10 127.0.0.1" : "sleep 10";

        List<ExecuteCmdStreamResult> streamResults = new ArrayList<>();
        Iterator<ExecuteCmdStreamResult> iterator = shell.executeCmdStream(cmd, null, 1, null, null);
        while (iterator.hasNext()) {
            streamResults.add(iterator.next());
        }

        ExecuteCmdStreamResult errorResult = null;
        for (ExecuteCmdStreamResult r : streamResults) {
            if (r.getCode() == StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode()) {
                errorResult = r;
                break;
            }
        }
        assertNotNull(errorResult, "Timeout error result should be returned");
        assertTrue(errorResult.getMessage().toLowerCase().contains("timeout"));
        assertEquals(-1, errorResult.getData().getExitCode());
    }

    @Test
    @Order(12)
    void testExecuteCmdStreamEmptyCommand() {
        BaseShellOperation shell = sysOp.shell();

        List<ExecuteCmdStreamResult> streamResults = new ArrayList<>();
        Iterator<ExecuteCmdStreamResult> iterator = shell.executeCmdStream("", null, 300, null, null);
        while (iterator.hasNext()) {
            streamResults.add(iterator.next());
        }

        assertEquals(1, streamResults.size());
        ExecuteCmdStreamResult errorResult = streamResults.get(0);
        assertEquals(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode(), errorResult.getCode());
        assertTrue(errorResult.getMessage().toLowerCase().contains("command can not be"));
        assertEquals(0, errorResult.getData().getChunkIndex());
        assertEquals(-1, errorResult.getData().getExitCode());
    }

    @Test
    @Order(13)
    void testExecuteCmdStreamContinuousOutput() {
        BaseShellOperation shell = sysOp.shell();

        String cmd = isWindows() ? "ping -n 3 127.0.0.1" : "ping -c 3 127.0.0.1";

        List<ExecuteCmdStreamResult> streamResults = new ArrayList<>();
        Iterator<ExecuteCmdStreamResult> iterator = shell.executeCmdStream(cmd, null, 10, null, null);
        while (iterator.hasNext()) {
            streamResults.add(iterator.next());
        }

        List<ExecuteCmdStreamResult> stdoutChunks = new ArrayList<>();
        for (ExecuteCmdStreamResult r : streamResults) {
            if (r.getData() != null && "stdout".equals(r.getData().getType())) {
                stdoutChunks.add(r);
            }
        }
        assertTrue(stdoutChunks.size() >= 1);

        StringBuilder combinedStdout = new StringBuilder();
        for (ExecuteCmdStreamResult r : stdoutChunks) {
            combinedStdout.append(r.getData().getText());
        }
        assertTrue(combinedStdout.toString().contains("127.0.0.1"));

        ExecuteCmdChunkData exitChunk = null;
        for (ExecuteCmdStreamResult r : streamResults) {
            if (r.getData() != null && r.getData().getExitCode() != null) {
                exitChunk = r.getData();
            }
        }
        assertNotNull(exitChunk);
        assertEquals(0, exitChunk.getExitCode());
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private SysOperation extractSysOperation(Object result) {
        if (result instanceof SysOperation op) {
            return op;
        } else if (result instanceof List<?> list && !list.isEmpty()) {
            return (SysOperation) list.get(0);
        }
        return null;
    }
}