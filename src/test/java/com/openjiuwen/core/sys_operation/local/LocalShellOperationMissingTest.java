/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sys_operation.BaseShellOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/core/sys_operation/local/test_shell_operation.py}.
 */
class LocalShellOperationMissingTest {

    private static final String WINDOWS_POWERSHELL =
            "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
    private static final String GIT_BASH = "C:\\Program Files\\Git\\bin\\bash.exe";

    @TempDir
    private Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Test
    void windowsAutoUnwrapsNestedPowershellCommand() throws Exception {
        LocalShellOperation operation = operation(null);
        String command = "powershell -Command "
                + "\"Get-Item 'C:\\tmp\\voiceover_timeline.md' -ErrorAction SilentlyContinue "
                + "| Select-Object Name, Length\"";

        List<String> args = operation.resolveExecutionArgsForTest(
                command, BaseShellOperation.ShellType.AUTO, false, true, WINDOWS_POWERSHELL, GIT_BASH, null);

        assertThat(args).containsExactly(
                WINDOWS_POWERSHELL,
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "Get-Item 'C:\\tmp\\voiceover_timeline.md' -ErrorAction SilentlyContinue "
                        + "| Select-Object Name, Length");
    }

    @Test
    void windowsExplicitPowershellUnwrapsNestedCommand() throws Exception {
        LocalShellOperation operation = operation(null);
        String command = "pwsh -NoProfile -NonInteractive -Command \"Write-Output ok\"";

        List<String> args = operation.resolveExecutionArgsForTest(
                command, BaseShellOperation.ShellType.POWERSHELL, false, true, WINDOWS_POWERSHELL, GIT_BASH, null);

        assertThat(args).containsExactly(
                WINDOWS_POWERSHELL,
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "Write-Output ok");
    }

    @Test
    void windowsAutoRoutesPosixLsToGitBash() throws Exception {
        LocalShellOperation operation = operation(null);
        String command = "ls -la \".team/jiuwen_team_sess_abc/artifacts/\"";

        List<String> args = operation.resolveExecutionArgsForTest(
                command, BaseShellOperation.ShellType.AUTO, false, true, WINDOWS_POWERSHELL, GIT_BASH, null);

        assertThat(args).containsExactly(GIT_BASH, "-lc", command);
    }

    @Test
    void windowsAutoRoutesPosixLsGrepPipelineToGitBash() throws Exception {
        LocalShellOperation operation = operation(null);
        String command = "ls -la \"C:\\tmp\\artifacts\" | grep -i \"分镜\"";

        List<String> args = operation.resolveExecutionArgsForTest(
                command, BaseShellOperation.ShellType.AUTO, false, true, WINDOWS_POWERSHELL, GIT_BASH, null);

        assertThat(args).containsExactly(GIT_BASH, "-lc", "ls -la \"C:/tmp/artifacts\" | grep -i \"分镜\"");
    }

    @Test
    void shellBasicExecution() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        ExecuteCmdResult echo = execute(operation, "echo hello world");

        assertThat(echo.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(echo.getData()).isNotNull();
        assertThat(echo.getData().getStdout().strip()).contains("hello world");
        assertThat(echo.getData().getExitCode()).isZero();
        assertThat(echo.getData().getCommand()).isEqualTo("echo hello world");

        ExecuteCmdResult listDir = execute(operation, listDirectoryCommand());

        assertThat(listDir.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(listDir.getData()).isNotNull();
        assertThat(listDir.getData().getStdout().strip()).isNotEmpty();
        assertThat(listDir.getData().getExitCode()).isZero();
    }

    @Test
    void shellEnvironmentVariables() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        ExecuteCmdResult result = operation.executeCmd(
                environmentCommand(),
                null,
                5,
                Map.of("TEST_VAR", "custom_value"),
                null,
                BaseShellOperation.ShellType.AUTO).get(10, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getStdout().strip()).contains("custom_value");
    }

    @Test
    void shellCwd() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);
        Path subdir = Files.createDirectories(tempDir.resolve("subdir"));

        ExecuteCmdResult absolute = execute(operation, cwdCommand(), subdir.toString());
        assertThat(absolute.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(absolute.getData().getStdout().strip().toLowerCase()).contains("subdir");

        ExecuteCmdResult relative = execute(operation, cwdCommand(), "subdir");
        assertThat(relative.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(relative.getData().getStdout().strip().toLowerCase()).contains("subdir");

        ExecuteCmdResult defaultCwd = execute(operation, cwdCommand());
        assertThat(defaultCwd.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(defaultCwd.getData().getStdout().strip().toLowerCase())
                .contains(tempDir.toAbsolutePath().normalize().getFileName().toString().toLowerCase());
    }

    @Test
    void shellDefaultCwd() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        ExecuteCmdResult result = execute(operation, cwdCommand());

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        String actual = result.getData().getStdout().strip().toLowerCase();
        String expected = tempDir.toAbsolutePath().normalize().toString().toLowerCase();
        assertThat(actual.contains(expected) || expected.contains(actual)).isTrue();
    }

    @Test
    void shellRelativeCwd() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);
        Files.createDirectories(tempDir.resolve("rel_subdir"));

        ExecuteCmdResult result = execute(operation, cwdCommand(), "rel_subdir");

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getStdout().strip().toLowerCase()).contains("rel_subdir");
    }

    @Test
    void shellTimeout() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        ExecuteCmdResult result = operation.executeCmd(
                "python -c \"import time; time.sleep(5)\"",
                null,
                1,
                null,
                null,
                BaseShellOperation.ShellType.AUTO).get(10, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).containsIgnoringCase("timeout");
    }

    @Test
    void shellPingTimeout() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        ExecuteCmdResult result = operation.executeCmd(
                "ping 127.0.0.1",
                null,
                1,
                null,
                null,
                BaseShellOperation.ShellType.AUTO).get(10, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).containsIgnoringCase("timeout");
        assertThat(result.getData()).isNotNull();
        assertThat((result.getData().getStdout() != null && result.getData().getStdout().contains("127.0.0.1"))
                || result.getData().getExitCode() != 0).isTrue();
    }

    @Test
    void shellAllowlist() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(List.of("echo", "pwd"));

        ExecuteCmdResult allowed = execute(operation, isWindows() ? "echo %CD%" : "pwd");

        assertThat(allowed.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        ExecuteCmdResult denied = execute(operation, "dir");

        assertThat(denied.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(denied.getMessage()).contains("not allowed");
    }

    @Test
    void shellListTools() {
        LocalShellOperation operation = operation(null);

        List<ToolCard> tools = operation.listTools();

        assertThat(tools).hasSize(3);
        assertThat(tools.stream().map(ToolCard::getName).toList())
                .containsExactly("execute_cmd", "execute_cmd_stream", "execute_cmd_background");
        ToolCard executeTool = tools.stream()
                .filter(tool -> "execute_cmd".equals(tool.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(executeTool.getInputParams()).containsKey("properties");
        assertThat(String.valueOf(executeTool.getInputParams().get("required"))).contains("command");
    }

    @Test
    void executeCmdStreamBasic() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                streamChunkCommand(),
                null,
                5,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        assertThat(results).isNotEmpty();
        String stdout = joinedStreamText(results, "stdout");
        String stderr = joinedStreamText(results, "stderr");
        assertThat(stdout).contains("chunk1").contains("chunk2");
        assertThat(stderr).contains("error_chunk");
        ExecuteCmdStreamResult exit = exitResult(results);
        assertThat(exit.getData().getExitCode()).isZero();
        assertThat(exit.getData().getChunkIndex()).isEqualTo(results.size() - 1);
    }

    @Test
    void executeCmdStreamTimeout() throws Exception {
        Path stableCwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Cwd.initCwd(stableCwd.toString(), stableCwd.toString(), stableCwd.toString(), null);
        LocalShellOperation operation = operation(null);

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                isWindows() ? "ping -n 10 127.0.0.1" : "sleep 10",
                null,
                1,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        ExecuteCmdStreamResult error = results.stream()
                .filter(result -> result.getCode() == StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode())
                .findFirst()
                .orElseThrow();
        assertThat(error.getMessage()).containsIgnoringCase("timeout");
        assertThat(error.getData().getExitCode()).isEqualTo(-1);
    }

    @Test
    void executeCmdStreamEmptyCommand() throws Exception {
        LocalShellOperation operation = operation(null);

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                "",
                null,
                null,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        assertThat(results).hasSize(1);
        ExecuteCmdStreamResult error = results.get(0);
        assertThat(error.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(error.getMessage()).contains("command can not be empty");
        assertThat(error.getData().getChunkIndex()).isZero();
        assertThat(error.getData().getExitCode()).isEqualTo(-1);
    }

    @Test
    void executeCmdStreamAllowlist() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(List.of("echo"));

        List<ExecuteCmdStreamResult> allowed = collect(operation.executeCmdStream(
                "echo allowed",
                null,
                5,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        assertThat(allowed).anySatisfy(result -> {
            assertThat(result.getData().getType()).isEqualTo("stdout");
            assertThat(result.getData().getText()).contains("allowed");
        });

        List<ExecuteCmdStreamResult> denied = collect(operation.executeCmdStream(
                listDirectoryCommand(),
                null,
                5,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        ExecuteCmdStreamResult error = denied.get(0);
        assertThat(error.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(error.getMessage()).contains("not allowed by allowlist");
    }

    @Test
    void executeCmdStreamContinuousOutput() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation(null);

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                isWindows() ? "ping -n 3 127.0.0.1" : "ping -c 3 127.0.0.1",
                null,
                10,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        String stdout = joinedStreamText(results, "stdout");
        assertThat(stdout).contains("127.0.0.1");
        assertThat(exitResult(results).getData().getExitCode()).isZero();
    }

    private LocalShellOperation operation(List<String> allowlist) {
        LocalWorkConfig config = LocalWorkConfig.builder()
                .shellAllowlist(allowlist)
                .build();
        return new LocalShellOperation("shell", OperationMode.LOCAL, "local shell", config);
    }

    private ExecuteCmdResult execute(LocalShellOperation operation, String command) throws Exception {
        return execute(operation, command, null);
    }

    private ExecuteCmdResult execute(LocalShellOperation operation, String command, String cwd) throws Exception {
        return operation.executeCmd(
                command,
                cwd,
                5,
                null,
                null,
                BaseShellOperation.ShellType.AUTO).get(10, TimeUnit.SECONDS);
    }

    private String listDirectoryCommand() {
        return isWindows() ? "dir" : "ls -la";
    }

    private String cwdCommand() {
        return isWindows() ? "echo %CD%" : "pwd";
    }

    private String environmentCommand() {
        return isWindows() ? "echo %TEST_VAR%" : "echo $TEST_VAR";
    }

    private String streamChunkCommand() {
        if (isWindows()) {
            return "echo chunk1 && echo chunk2 && echo error_chunk 1>&2";
        }
        return "echo chunk1; sleep 0.01; echo chunk2; sleep 0.01; echo error_chunk 1>&2";
    }

    private String joinedStreamText(List<ExecuteCmdStreamResult> results, String type) {
        StringBuilder builder = new StringBuilder();
        for (ExecuteCmdStreamResult result : results) {
            if (result.getData() != null && type.equals(result.getData().getType())) {
                builder.append(result.getData().getText());
            }
        }
        return builder.toString();
    }

    private ExecuteCmdStreamResult exitResult(List<ExecuteCmdStreamResult> results) {
        return results.stream()
                .filter(result -> result.getData() != null && result.getData().getExitCode() != null)
                .findFirst()
                .orElseThrow();
    }

    private <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static final class CapturingSubscriber<T> implements Flow.Subscriber<T> {

        private final List<T> items = new ArrayList<>();
        private final CompletableFuture<List<T>> done = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            done.complete(List.copyOf(items));
        }

        private List<T> await() throws Exception {
            try {
                return done.get(20, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
