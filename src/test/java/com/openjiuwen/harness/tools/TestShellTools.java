/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for shell tools.
 *
 * <p>Mirrors Python's {@code test_shell_tools.py} in
 * {@code tests/unit_tests/harness/tools/test_shell_tools.py}.
 */
class TestShellTools {

    private FakeSysOperation sysOp;
    private BashTool bashTool;

    @BeforeEach
    void setUp() {
        sysOp = new FakeSysOperation();
        bashTool = new BashTool(sysOp);
    }

    @Nested
    class TestBashTool {

        @Test
        void testBashTool() {
            sysOp.shell.reply("echo \\u4f60\\u597d", "\u4f60\u597d\n", "", 0);

            ToolOutput result = invoke(Map.of("command", "echo \\u4f60\\u597d"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(0, data(result).get("exit_code"));
            assertEquals("", data(result).get("stderr"));
            assertTrue(String.valueOf(data(result).get("stdout")).contains("\u4f60\u597d"));
            assertNull(result.getError());
        }

        @Test
        void testBashToolLsChineseFilename() throws Exception {
            Path workspace = Files.createTempDirectory("shell-tools-");
            Path testDir = workspace.resolve("test_chinese_files");
            Files.createDirectories(testDir);
            String stdout = "\u6d4b\u8bd5\u6587\u4ef6.txt\n\u4e2d\u6587\u6587\u4ef6 - \u526f\u672c.txt\n";
            String command = "ls -la \"" + testDir + "\"";
            sysOp.shell.reply(command, stdout, "", 0);

            ToolOutput result = invoke(Map.of("command", command));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(0, data(result).get("exit_code"));
            assertTrue(String.valueOf(data(result).get("stdout")).contains("\u6d4b\u8bd5\u6587\u4ef6.txt"));
            assertTrue(String.valueOf(data(result).get("stdout")).contains("\u4e2d\u6587\u6587\u4ef6 - \u526f\u672c.txt"));
            assertNull(result.getError());
        }

        @Test
        void testBashToolFailCommand() {
            sysOp.shell.reply("echo fail && exit 1", "fail\n", "failed\n", 1);

            ToolOutput result = invoke(Map.of("command", "echo fail && exit 1"));

            assertFalse(result.isSuccess());
            assertEquals(1, data(result).get("exit_code"));
        }

        @Test
        void testBashToolAllowlist() {
            sysOp.shell.reply("echo ok", "ok\n", "", 0);
            ToolOutput ok = invoke(Map.of("command", "echo ok"));

            sysOp.shell.fail("whoami", "command not allowed by allowlist");
            ToolOutput blocked = invoke(Map.of("command", "whoami"));

            assertTrue(ok.isSuccess(), ok.getError());
            assertFalse(blocked.isSuccess());
            assertNotNull(blocked.getError());
            assertNull(blocked.getData());
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "rm -rf /tmp/foo|rm -rf",
                "shutdown -h now|shutdown",
                "reboot|reboot",
                "diskpart|diskpart",
                "mkfs.ext4 /dev/sda|mkfs",
                "reg delete HKLM\\Software\\Test|reg delete",
                "Remove-Item C:\\foo -Recurse -Force|Remove-Item -Recurse -Force"
        })
        void testDangerousCommandBlocked(String command, String label) {
            ToolOutput result = invoke(Map.of("command", command));

            assertFalse(result.isSuccess());
            assertNull(result.getData());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("safety"));
            assertTrue(result.getError().contains(label));
            assertNull(sysOp.shell.lastCommand);
        }
    }

    @Nested
    class TestWorkdir {

        @Test
        void testWorkdirValidAbsoluteSubdir() throws Exception {
            Path workspace = Files.createTempDirectory("shell-workdir-");
            Path subdir = Files.createDirectories(workspace.resolve("sub"));
            sysOp.shell.reply("pwd", subdir.toString() + "\n", "", 0);

            ToolOutput result = invoke(Map.of("command", "pwd", "workdir", subdir.toString()));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(subdir.toString(), sysOp.shell.lastCwd);
        }

        @Test
        void testWorkdirValidRelativeSubdir() throws Exception {
            Path workspace = Files.createTempDirectory("shell-workdir-");
            Path subdir = Files.createDirectories(workspace.resolve("sub"));
            sysOp.shell.reply("pwd", subdir.toString() + "\n", "", 0);

            ToolOutput result = invoke(Map.of("command", "pwd", "workdir", subdir.toString()));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(subdir.toString(), sysOp.shell.lastCwd);
        }

        @Test
        void testWorkdirNonexistentDirFails() throws Exception {
            Path workspace = Files.createTempDirectory("shell-workdir-");
            Path missing = workspace.resolve("definitely_not_exist_xyz");
            sysOp.shell.fail("echo hi", "unexpected error: " + missing);

            ToolOutput result = invoke(Map.of("command", "echo hi", "workdir", missing.toString()));

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }

        @Test
        void testWorkdirFromContextvar() throws Exception {
            Path workspace = Files.createTempDirectory("shell-context-");
            sysOp.shell.reply("pwd", workspace.toString() + "\n", "", 0, workspace.toString());

            ToolOutput result = invoke(Map.of("command", "pwd"));

            assertTrue(result.isSuccess(), result.getError());
            assertTrue(String.valueOf(data(result).get("stdout")).contains(workspace.toString()));
            assertEquals(workspace.toString(), data(result).get("cwd"));
        }
    }

    @Nested
    class TestBackgroundExecution {

        @Test
        void testBackgroundReturnsPid() {
            sysOp.shell.background("sleep 5", 12345L);

            ToolOutput result = invoke(Map.of("command", "sleep 5", "run_in_background", true));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals("started", data(result).get("status"));
            assertEquals(12345, data(result).get("pid"));
        }

        @Test
        void testBackgroundFastFailDetected() {
            sysOp.shell.backgroundFail("exit 1", "background command failed: exit 1");

            ToolOutput result = invoke(Map.of("command", "exit 1", "run_in_background", true));

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertNull(result.getData());
        }
    }

    @Nested
    class TestOutputTruncation {

        @Test
        void testOutputTruncatedWhenOverLimit() {
            String stdout = "x".repeat(500) + "\n" + "tail";
            sysOp.shell.reply("python -c print-big", stdout, "", 0);

            ToolOutput result = invoke(Map.of("command", "python -c print-big", "max_output_chars", 250));

            assertTrue(result.isSuccess(), result.getError());
            assertTrue(String.valueOf(data(result).get("stdout")).contains("lines omitted"));
            assertNotNull(data(result).get("persisted_output_path"));
        }

        @Test
        void testOutputNotTruncatedWithinLimit() {
            sysOp.shell.reply("echo hello", "hello\n", "", 0);

            ToolOutput result = invoke(Map.of("command", "echo hello", "max_output_chars", 8000));

            assertTrue(result.isSuccess(), result.getError());
            assertFalse(String.valueOf(data(result).get("stdout")).contains("[truncated]"));
        }

        @Test
        void testMaxOutputCharsClampedToMinimum() {
            sysOp.shell.reply("echo hi", "hi\n", "", 0);

            ToolOutput result = invoke(Map.of("command", "echo hi", "max_output_chars", 1));

            assertTrue(result.isSuccess(), result.getError());
            assertTrue(String.valueOf(data(result).get("stdout")).contains("hi"));
        }
    }

    private ToolOutput invoke(Map<String, Object> inputs) {
        return (ToolOutput) bashTool.invoke(inputs, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private static final class FakeSysOperation extends SysOperation {
        private final FakeShellOperation shell = new FakeShellOperation();

        private FakeSysOperation() {
            super(SysOperationCard.builder()
                    .id("fake-shell-op")
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().shellAllowlist(List.of()).build())
                    .build());
        }

        @Override
        public FakeShellOperation shell() {
            return shell;
        }
    }

    private static final class FakeShellOperation extends BaseShellOperation {
        private final Map<String, ExecuteCmdResult> replies = new LinkedHashMap<>();
        private final Map<String, ExecuteCmdBackgroundResult> backgroundReplies = new LinkedHashMap<>();
        private String lastCommand;
        private String lastCwd;

        private FakeShellOperation() {
            super("shell", OperationMode.LOCAL, "fake shell operation", null);
        }

        private void reply(String command, String stdout, String stderr, int exitCode) {
            reply(command, stdout, stderr, exitCode, ".");
        }

        private void reply(String command, String stdout, String stderr, int exitCode, String cwd) {
            replies.put(command, ExecuteCmdResult.success(ExecuteCmdData.builder()
                    .command(command)
                    .cwd(cwd)
                    .exitCode(exitCode)
                    .stdout(stdout)
                    .stderr(stderr)
                    .build()));
        }

        private void fail(String command, String message) {
            replies.put(command, ExecuteCmdResult.failure(message));
        }

        private void background(String command, long pid) {
            backgroundReplies.put(command, ExecuteCmdBackgroundResult.success(ExecuteCmdBackgroundData.builder()
                    .command(command)
                    .cwd(".")
                    .pid(pid)
                    .build()));
        }

        private void backgroundFail(String command, String message) {
            backgroundReplies.put(command, ExecuteCmdBackgroundResult.failure(message));
        }

        @Override
        public ExecuteCmdResult executeCmd(String command, String cwd, int timeout,
                                           Map<String, String> environment, Map<String, Object> options) {
            lastCommand = command;
            lastCwd = cwd;
            return replies.getOrDefault(command, ExecuteCmdResult.success(ExecuteCmdData.builder()
                    .command(command)
                    .cwd(cwd == null || cwd.isBlank() ? "." : cwd)
                    .exitCode(0)
                    .stdout("")
                    .stderr("")
                    .build()));
        }

        @Override
        public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
                                                                 Map<String, String> environment,
                                                                 Map<String, Object> options) {
            return Collections.emptyIterator();
        }

        @Override
        public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd, String shellType) {
            lastCommand = command;
            lastCwd = cwd;
            return backgroundReplies.getOrDefault(command,
                    ExecuteCmdBackgroundResult.failure("background command failed: " + command));
        }
    }
}
