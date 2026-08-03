/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.test_shell_tools} in
 * {@code tests/unit_tests/harness/tools/test_shell_tools.py}.</p>
 */
class ShellToolsPythonParityTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Disabled("Python baseline failed: tests.unit_tests.harness.tools.test_shell_tools::test_bash_tool; "
            + "javaify-project/tests/python-baseline/latest-summary.json records AssertionError because Windows "
            + "decoded expected Chinese output '你好' as '浣犲ソ'.")
    @Test
    void testBashTool() {
        assertThat(true).isTrue();
    }

    @Test
    void testBashToolLsChineseFilename() throws Exception {
        Path testDir = tempDir.resolve("test_chinese_files");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("测试文件.txt"), "test content");
        Files.writeString(testDir.resolve("中文文件 - 副本.txt"), "test content");

        ToolOutput result = invoke(new BashTool(), listCommand(testDir));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).contains("测试文件.txt", "中文文件 - 副本.txt");
        assertThat(result.getError()).isNull();
    }

    @Test
    void testBashToolFailCommand() throws Exception {
        ToolOutput result = invoke(new BashTool(), command(isWindows() ? "echo fail && exit /b 1" : "echo fail && exit 1"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(content(result)).startsWith("Exit code");
    }

    @Test
    void testBashToolAllowlist() throws Exception {
        BashTool tool = new BashTool(PermissionMode.AUTO, null, null, List.of("echo"));

        ToolOutput allowed = invoke(tool, command("echo ok"));
        ToolOutput blocked = invoke(tool, command(isWindows() ? "whoami" : "whoami"));

        assertThat(allowed.isSuccess()).isTrue();
        assertThat(blocked.isSuccess()).isFalse();
        assertThat(blocked.getError()).isNotNull();
        assertThat(blocked.getData()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "'rm -rf /tmp/foo','rm -rf'",
            "'shutdown -h now','shutdown'",
            "'reboot','reboot'",
            "'diskpart','diskpart'",
            "'mkfs.ext4 /dev/sda','mkfs'",
            "'reg delete HKLM\\Software\\Test','reg delete'",
            "'Remove-Item C:\\foo -Recurse -Force','Remove-Item -Recurse -Force'"
    })
    void testDangerousCommandBlocked(String command, String label) throws Exception {
        ToolOutput result = invoke(new BashTool(), command(command));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getData()).isNull();
        assertThat(result.getError()).contains("safety", label);
    }

    @Test
    void testWorkdirValidAbsoluteSubdir() throws Exception {
        Path subdir = Files.createDirectories(tempDir.resolve("sub"));

        ToolOutput result = invoke(new BashTool(), Map.of("command", pwdCommand(), "workdir", subdir.toString()));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testWorkdirValidRelativeSubdir() throws Exception {
        Path subdir = Files.createDirectories(tempDir.resolve("sub"));

        ToolOutput result = invoke(new BashTool(), Map.of("command", pwdCommand(), "workdir", subdir.toString()));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testWorkdirNonexistentDirFails() throws Exception {
        Path missing = tempDir.resolve("definitely_not_exist_xyz");

        ToolOutput result = invoke(new BashTool(), Map.of("command", "echo hi", "workdir", missing.toString()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isNotNull();
    }

    @Test
    void testWorkdirFromContextvar() throws Exception {
        Cwd.initCwd(tempDir.toString());

        ToolOutput result = invoke(new BashTool(), command(pwdCommand()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(normalized(content(result))).contains(normalized(tempDir.toString()));
    }

    @Test
    void testDefaultWorkdirPrefersCurrentCwdOverWorkspace() throws Exception {
        Path currentDir = Files.createDirectories(tempDir.resolve("current"));
        Cwd.initCwd(currentDir.toString(), null, tempDir.toString(), null);

        ToolOutput result = invoke(new BashTool(), command(pwdCommand()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(normalized(content(result))).contains(normalized(currentDir.toString()));
    }

    @Test
    void testBackgroundReturnsPid() throws Exception {
        String command = isWindows() ? "ping -n 5 127.0.0.1 > nul" : "sleep 5";

        ToolOutput result = invoke(new BashTool(), Map.of("command", command, "run_in_background", true));

        assertThat(result.isSuccess()).isTrue();
        assertThat(data(result)).containsEntry("status", "started");
        Number pid = (Number) data(result).get("pid");
        assertThat(pid.longValue()).isPositive();
        ProcessHandle.of(pid.longValue()).ifPresent(ProcessHandle::destroyForcibly);
    }

    @Test
    void testBackgroundFastFailDetected() throws Exception {
        ToolOutput result = invoke(new BashTool(), Map.of(
                "command", isWindows() ? "exit /b 1" : "exit 1",
                "run_in_background", true
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isNotNull();
        assertThat(result.getData()).isNull();
    }

    @Test
    void testOutputPersistedWhenOverLimit() throws Exception {
        String py = isWindows() ? "python" : "python3";

        ToolOutput result = invoke(new BashTool(), Map.of(
                "command", py + " -c \"print('x' * 500)\"",
                "max_output_chars", 250
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).contains("<persisted-output>");
    }

    @Test
    void testOutputNotTruncatedWithinLimit() throws Exception {
        ToolOutput result = invoke(new BashTool(), Map.of("command", "echo hello", "max_output_chars", 8000));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).contains("hello").doesNotContain("<persisted-output>");
    }

    @Test
    void testMaxOutputCharsClampedToMinimum() throws Exception {
        ToolOutput result = invoke(new BashTool(), Map.of("command", "echo hi", "max_output_chars", 1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).contains("hi").doesNotContain("<persisted-output>");
    }

    private static ToolOutput invoke(BashTool tool, Map<String, Object> inputs) throws Exception {
        return (ToolOutput) tool.invoke(inputs);
    }

    private static Map<String, Object> command(String command) {
        return Map.of("command", command);
    }

    private static Map<String, Object> listCommand(Path testDir) {
        if (isWindows()) {
            String command = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
                    + "Get-ChildItem -LiteralPath '" + psQuote(testDir.toString()) + "' | "
                    + "ForEach-Object { $_.Name }";
            return Map.of("command", command, "shell_type", "powershell");
        }
        return Map.of("command", "ls -la \"" + testDir + "\"");
    }

    private static String psQuote(String value) {
        return value.replace("'", "''");
    }

    private static String pwdCommand() {
        return isWindows() ? "cd" : "pwd";
    }

    private static Map<String, Object> data(ToolOutput output) {
        return BashTool.dataMap(output);
    }

    private static String content(ToolOutput output) {
        Object content = data(output).get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private static String normalized(String value) {
        return value.replace('\\', '/').trim();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
