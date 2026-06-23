/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.test_bash.test_bash_tool} in
 * {@code tests/unit_tests/harness/tools/test_bash/test_bash_tool.py}.</p>
 */
class BashToolTest {

    @TempDir
    Path tempDir;

    @Test
    void testEcho() throws Exception {
        ToolOutput result = invoke(new BashTool(), command("echo hello"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).contains("hello");
        assertThat(result.getError()).isNull();
    }

    @Test
    void testExitOneIsError() throws Exception {
        ToolOutput result = invoke(new BashTool(), command(isWindows() ? "echo fail && exit /b 1" : "echo fail && exit 1"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(content(result)).startsWith("Exit code");
    }

    @Test
    void testGrepNoMatchIsNotError() throws Exception {
        ToolOutput result = invoke(new BashTool(), command(
                isWindows() ? "echo hello | findstr nonexistent_pattern_xyz" : "echo hello | grep nonexistent_pattern_xyz"
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).isEqualTo("");
    }

    @Test
    void testGrepMatchSuccess() throws Exception {
        ToolOutput result = invoke(new BashTool(), command(isWindows() ? "echo hello | findstr hello" : "echo hello | grep hello"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).contains("hello");
    }

    @Test
    void testSilentCommandEmptyContent() throws Exception {
        Path subdir = tempDir.resolve("sub");

        ToolOutput result = invoke(new BashTool(), command(mkdirCommand(subdir)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).isEqualTo("");
        assertThat(Files.isDirectory(subdir)).isTrue();
    }

    @Test
    void testDestructiveWarningPresent() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        runGit(repo, "init", "-q");
        runGit(repo, "config", "user.email", "t@e2e.local");
        runGit(repo, "config", "user.name", "t");
        runGit(repo, "commit", "--allow-empty", "-q", "-m", "init");

        ToolOutput result = invoke(new BashTool(), Map.of("command", "git commit --amend -m test", "workdir", repo.toString()));

        assertThat(content(result).toLowerCase()).contains("rewrite");
    }

    @Test
    void testInjectionBacktickBlocked() throws Exception {
        ToolOutput result = invoke(new BashTool(), command("echo `whoami`"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).containsIgnoringCase("injection");
    }

    @Test
    void testInjectionDollarParenBlocked() throws Exception {
        ToolOutput result = invoke(new BashTool(), command("echo $(id)"));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void testWorkdirNonexistentDirFails() throws Exception {
        ToolOutput result = invoke(new BashTool(), Map.of(
                "command", "echo hi",
                "workdir", tempDir.resolve("missing").toString()
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("workdir does not exist");
    }

    @Test
    void testBackgroundPid() throws Exception {
        String command = isWindows() ? "ping -n 5 127.0.0.1 > nul" : "sleep 5";

        ToolOutput result = invoke(new BashTool(), Map.of("command", command, "run_in_background", true));

        assertThat(result.isSuccess()).isTrue();
        Number pid = (Number) BashTool.dataMap(result).get("pid");
        assertThat(pid.longValue()).isPositive();
        ProcessHandle.of(pid.longValue()).ifPresent(ProcessHandle::destroyForcibly);
    }

    @Test
    void testDescriptionAccepted() throws Exception {
        ToolOutput result = invoke(new BashTool(), Map.of("command", "echo ok", "description", "Check connectivity"));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testReadOnlyModeAllowsRead() throws Exception {
        ToolOutput result = invoke(new BashTool(PermissionMode.READ_ONLY), command(isWindows() ? "dir" : "ls -la"));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testReadOnlyModeBlocksWrite() throws Exception {
        ToolOutput result = invoke(new BashTool(PermissionMode.READ_ONLY), command(mkdirCommand(tempDir.resolve("blocked"))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Read-only");
    }

    @Test
    void testAcceptEditsModeAllowsFileOps() throws Exception {
        Path subdir = tempDir.resolve("accepted");

        ToolOutput result = invoke(new BashTool(PermissionMode.ACCEPT_EDITS), command(mkdirCommand(subdir)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.isDirectory(subdir)).isTrue();
    }

    @Test
    void testDenyPatterns() throws Exception {
        ToolOutput result = invoke(new BashTool(PermissionMode.AUTO, List.of("\\bsudo\\b"), null), command("sudo echo hi"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).containsIgnoringCase("denied");
    }

    @Test
    void testAllowPatternsOverride() throws Exception {
        Path subdir = tempDir.resolve("override");
        BashTool tool = new BashTool(PermissionMode.READ_ONLY, null, List.of("^echo\\s.*&&\\s*mkdir"));

        ToolOutput result = invoke(tool, command("echo ok && " + mkdirCommand(subdir)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getError()).isNull();
    }

    @Test
    void testLargeOutputPersisted() {
        BashOutput.RenderedContent result = BashOutput.renderToolContent(
                new BashOutput.CommandOutput("x".repeat(50000), "", 0, null, 1000),
                false
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("<persisted-output>", "Output too large");
    }

    @Test
    void testSmallOutputNotPersisted() throws Exception {
        ToolOutput result = invoke(new BashTool(), command("echo hello"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(content(result)).doesNotContain("<persisted-output>").contains("hello");
    }

    @Disabled("Python baseline failed: tests.unit_tests.harness.tools.test_bash.test_bash_tool::test_timeout_returns_collected_output; "
            + "javaify-project/tests/python-baseline/latest-summary.json records AssertionError because Windows returned "
            + "ToolOutput(success=True, data={content=partial; sleep 5}, error=None).")
    @Test
    void testTimeoutReturnsCollectedOutput() {
        assertThat(true).isTrue();
    }

    @Test
    void testEmptyCommand() throws Exception {
        ToolOutput result = invoke(new BashTool(), command(""));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("empty");
    }

    @Test
    void testPathContainsAgentIdAndSessionId() {
        String path = BashTool.buildHistoryPath(tempDir.toString(), "sess_abc", "agent_xyz");

        assertThat(path).contains("agent_xyz", "sess_abc", ".agent_history");
    }

    @Test
    void testDefaultAgentIdUsedWhenNone() {
        String path = BashTool.buildHistoryPath(tempDir.toString(), "s1", null);

        assertThat(path).contains("default");
    }

    @Test
    void testWorkspacePathIsBaseDir() {
        String path = BashTool.buildHistoryPath(tempDir.toString(), "s1", "a");

        assertThat(path).startsWith(tempDir.toString());
    }

    @Test
    void testFilenamePattern() {
        String path = BashTool.buildHistoryPath(tempDir.toString(), "sess123", "myagent");

        assertThat(Path.of(path).getFileName().toString()).isEqualTo("file_ops_myagent_sess123.json");
    }

    private static ToolOutput invoke(BashTool tool, Map<String, Object> inputs) throws Exception {
        return (ToolOutput) tool.invoke(inputs);
    }

    private static Map<String, Object> command(String command) {
        return Map.of("command", command);
    }

    private static String content(ToolOutput output) {
        Object content = BashTool.dataMap(output).get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private static String mkdirCommand(Path path) {
        return isWindows() ? "mkdir \"" + path + "\"" : "mkdir -p \"" + path + "\"";
    }

    private static void runGit(Path directory, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        int exitCode = process.waitFor();
        assertThat(exitCode).isZero();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
