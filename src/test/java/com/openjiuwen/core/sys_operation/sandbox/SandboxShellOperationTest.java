/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sys_operation.BaseShellOperation;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sys_operation.sandbox.launchers.SandboxLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ShellOperation} behavior in
 * {@code openjiuwen/core/sys_operation/sandbox/shell_operation.py}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.sys_operation.sandbox.test_shell} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_shell.py}.</p>
 */
class SandboxShellOperationTest {

    @AfterEach
    void cleanup() throws Exception {
        SandboxRegistry.unregisterLauncher("shell-operation-test-launcher");
        SandboxRegistry.unregisterProvider("shell-operation-test-sandbox", "shell");
        Field field = com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxGateway.class
                .getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void executeCmdRoutesThroughSandboxGateway() {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        SandboxShellOperation operation = operation();

        ExecuteCmdResult result = operation.executeCmd(
                "echo ok",
                "/workspace",
                12,
                Map.of("A", "B"),
                Map.of("pty", true),
                BaseShellOperation.ShellType.BASH).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getCommand()).isEqualTo("echo ok");
        assertThat(result.getData().getCwd()).isEqualTo("/workspace");
        assertThat(result.getData().getStdout()).isEqualTo("bash:12:B:true");
        assertThat(TestShellProvider.lastEndpoint.baseUrl()).isEqualTo("http://sandbox-shell");
    }

    @Test
    void executeCmdStreamMapsGatewayPublisherItems() throws Exception {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        SandboxShellOperation operation = operation();

        List<ExecuteCmdStreamResult> chunks = collect(operation.executeCmdStream(
                "printf ok",
                null,
                30,
                null,
                null,
                BaseShellOperation.ShellType.SH));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(chunks.get(0).getData().getType()).isEqualTo("stdout");
        assertThat(chunks.get(0).getData().getText()).isEqualTo("sh:printf ok");
        assertThat(chunks.get(1).getData().getExitCode()).isZero();
    }

    @Test
    void executeCmdBackgroundDelegatesToGatewayProvider() {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        SandboxShellOperation operation = operation();

        ExecuteCmdBackgroundResult result = operation.executeCmdBackground(
                "sleep 10",
                "/workspace",
                Map.of("PATH", "/bin"),
                1.5d,
                BaseShellOperation.ShellType.BASH).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getCommand()).isEqualTo("sleep 10");
        assertThat(result.getData().getCwd()).isEqualTo("/workspace");
        assertThat(result.getData().getPid()).isEqualTo(1500);
    }

    @Test
    void shellBasicExecutionMatchesPythonLocalProvider() {
        SandboxShellOperation operation = registeredOperation();

        ExecuteCmdResult echo = operation.executeCmd(
                "echo hello world", null, null, null, null, BaseShellOperation.ShellType.AUTO).join();
        ExecuteCmdResult list = operation.executeCmd(
                "ls -la", null, null, null, null, BaseShellOperation.ShellType.AUTO).join();

        assertThat(echo.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(echo.getData().getStdout().trim()).contains("hello world");
        assertThat(echo.getData().getExitCode()).isZero();
        assertThat(echo.getData().getCommand()).isEqualTo("echo hello world");
        assertThat(list.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(list.getData().getStdout()).contains("file1.txt");
        assertThat(list.getData().getExitCode()).isZero();
    }

    @Test
    void shellEnvironmentVariablesMatchPythonLocalProvider() {
        SandboxShellOperation operation = registeredOperation();

        ExecuteCmdResult result = operation.executeCmd(
                "echo $TEST_VAR",
                null,
                null,
                Map.of("TEST_VAR", "custom_value"),
                null,
                BaseShellOperation.ShellType.AUTO).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getStdout().trim()).contains("custom_value");
    }

    @Test
    void shellCwdMatchesPythonLocalProvider() {
        SandboxShellOperation operation = registeredOperation();

        ExecuteCmdResult result = operation.executeCmd(
                "pwd", "/tmp/subdir", null, null, null, BaseShellOperation.ShellType.AUTO).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getCwd()).isEqualTo("/tmp/subdir");
        assertThat(result.getData().getStdout().trim()).contains("/tmp/subdir");
    }

    @Test
    void shellTimeoutMatchesPythonLocalProvider() {
        SandboxShellOperation operation = registeredOperation();

        ExecuteCmdResult result = operation.executeCmd(
                "python -c \"import time; time.sleep(5)\"",
                null,
                1,
                null,
                null,
                BaseShellOperation.ShellType.AUTO).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage().toLowerCase(java.util.Locale.ROOT)).contains("timeout");
        assertThat(result.getData().getExitCode()).isEqualTo(-1);
    }

    @Test
    void shellPingTimeoutPreservesPartialStdout() {
        SandboxShellOperation operation = registeredOperation();

        ExecuteCmdResult result = operation.executeCmd(
                "ping 127.0.0.1", null, 1, null, null, BaseShellOperation.ShellType.AUTO).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage().toLowerCase(java.util.Locale.ROOT)).contains("timeout");
        assertThat(result.getData().getStdout()).contains("127.0.0.1");
        assertThat(result.getData().getExitCode()).isEqualTo(-1);
    }

    @Test
    void shellListToolsMatchesPythonToolMetadata() {
        SandboxShellOperation operation = registeredOperation();

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
    void executeCmdStreamBasicMatchesPythonLocalProvider() throws Exception {
        SandboxShellOperation operation = registeredOperation();

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                "echo chunk1; echo chunk2; echo error_chunk 1>&2",
                null,
                null,
                null,
                null,
                BaseShellOperation.ShellType.AUTO));

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(ExecuteCmdStreamResult.class));
        assertThat(joinedStreamText(results, "stdout")).contains("chunk1").contains("chunk2");
        List<ExecuteCmdStreamResult> stderrChunks = streamChunks(results, "stderr");
        assertThat(stderrChunks).hasSizeGreaterThanOrEqualTo(1);
        assertThat(stderrChunks.get(0).getData().getText()).contains("error_chunk");
        ExecuteCmdChunkData exitChunk = exitChunk(results);
        assertThat(exitChunk.getExitCode()).isZero();
        assertThat(exitChunk.getChunkIndex()).isEqualTo(results.size() - 1);
    }

    @Test
    void executeCmdStreamTimeoutMatchesPythonLocalProvider() throws Exception {
        SandboxShellOperation operation = registeredOperation();

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                "sleep 10", null, 1, null, null, BaseShellOperation.ShellType.AUTO));

        assertThat(results).hasSize(1);
        ExecuteCmdStreamResult error = results.get(0);
        assertThat(error.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(error.getMessage().toLowerCase(java.util.Locale.ROOT)).contains("timeout");
        assertThat(error.getData().getExitCode()).isEqualTo(-1);
    }

    @Test
    void executeCmdStreamEmptyCommandMatchesPythonLocalProvider() throws Exception {
        SandboxShellOperation operation = registeredOperation();

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                "", null, null, null, null, BaseShellOperation.ShellType.AUTO));

        assertThat(results).hasSize(1);
        ExecuteCmdStreamResult error = results.get(0);
        assertThat(error.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(error.getMessage()).contains("command can not be empty");
        assertThat(error.getData().getChunkIndex()).isZero();
        assertThat(error.getData().getExitCode()).isEqualTo(-1);
    }

    @Test
    void executeCmdStreamContinuousOutputMatchesPythonLocalProvider() throws Exception {
        SandboxShellOperation operation = registeredOperation();

        List<ExecuteCmdStreamResult> results = collect(operation.executeCmdStream(
                "ping -c 3 127.0.0.1", null, 10, null, null, BaseShellOperation.ShellType.AUTO));

        List<ExecuteCmdStreamResult> stdoutChunks = streamChunks(results, "stdout");
        assertThat(stdoutChunks).hasSizeGreaterThanOrEqualTo(1);
        assertThat(joinedStreamText(results, "stdout")).contains("127.0.0.1");
        assertThat(exitChunk(results).getExitCode()).isZero();
    }

    private static SandboxShellOperation registeredOperation() {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        return operation();
    }

    private static SandboxShellOperation operation() {
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("shell-operation-test-launcher")
                .sandboxType("shell-operation-test-sandbox")
                .build();
        SandboxRunConfig runConfig = SandboxRunConfig.builder()
                .config(SandboxGatewayConfig.builder()
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .isolationKeyTemplate("shell-op-test")
                .build();
        return new SandboxShellOperation(
                "shell",
                OperationMode.SANDBOX,
                "Sandbox shell execution operation",
                runConfig);
    }

    private static List<ExecuteCmdStreamResult> streamChunks(List<ExecuteCmdStreamResult> results, String type) {
        return results.stream()
                .filter(result -> type.equals(result.getData().getType()) && result.getData().getExitCode() == null)
                .toList();
    }

    private static String joinedStreamText(List<ExecuteCmdStreamResult> results, String type) {
        return streamChunks(results, type).stream()
                .map(result -> result.getData().getText())
                .reduce("", String::concat);
    }

    private static ExecuteCmdChunkData exitChunk(List<ExecuteCmdStreamResult> results) {
        return results.stream()
                .map(ExecuteCmdStreamResult::getData)
                .filter(data -> data.getExitCode() != null)
                .findFirst()
                .orElseThrow();
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
    }

    public static final class TestLauncher extends SandboxLauncher {

        @Override
        public CompletableFuture<LaunchedSandbox> launch(
                SandboxLauncherConfig config,
                int timeoutSeconds,
                String isolationKey) {
            return CompletableFuture.completedFuture(new LaunchedSandbox("http://sandbox-shell", "sb-shell"));
        }
    }

    public static final class TestShellProvider {
        private static SandboxEndpoint lastEndpoint;

        public TestShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            lastEndpoint = endpoint;
        }

        public CompletableFuture<ExecuteCmdResult> executeCmd(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options,
                String shellType) {
            ExecuteCmdResult result = new ExecuteCmdResult();
            Map<String, String> safeEnvironment = environment == null ? Map.of() : environment;
            Map<String, Object> safeOptions = options == null ? Map.of() : options;
            if (isBlank(command)) {
                result.setCode(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
                result.setMessage("command can not be empty");
                result.setData(ExecuteCmdData.builder()
                        .command(command)
                        .cwd(cwdOrDefault(cwd))
                        .exitCode(-1)
                        .build());
                return CompletableFuture.completedFuture(result);
            }
            if (isTimeout(command, timeoutSeconds)) {
                result.setCode(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
                result.setMessage("execution timeout after " + timeoutSeconds + " seconds");
                result.setData(ExecuteCmdData.builder()
                        .command(command)
                        .cwd(cwdOrDefault(cwd))
                        .stdout(stdoutFor(command, cwd, safeEnvironment))
                        .stderr(stderrFor(command))
                        .exitCode(-1)
                        .build());
                return CompletableFuture.completedFuture(result);
            }
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ExecuteCmdData.builder()
                    .command(command)
                    .cwd(cwdOrDefault(cwd))
                    .stdout(stdoutFor(command, cwd, safeEnvironment, safeOptions, shellType, timeoutSeconds))
                    .stderr("")
                    .exitCode(0)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options,
                String shellType) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done;

                @Override
                public void request(long itemCount) {
                    if (done) {
                        return;
                    }
                    done = true;
                    if (isBlank(command)) {
                        subscriber.onNext(streamError("command can not be empty", 0));
                        subscriber.onComplete();
                        return;
                    }
                    if (isTimeout(command, timeoutSeconds)) {
                        subscriber.onNext(streamError("execution timeout after " + timeoutSeconds + " seconds", 0));
                        subscriber.onComplete();
                        return;
                    }
                    int chunkIndex = 0;
                    if ("printf ok".equals(command)) {
                        subscriber.onNext(streamChunk(shellType + ":" + command, "stdout", chunkIndex++, null));
                    } else {
                        String stdout = stdoutFor(command, cwd, environment == null ? Map.of() : environment);
                        for (String line : stdout.split("(?<=\\n)")) {
                            if (!line.isEmpty()) {
                                subscriber.onNext(streamChunk(line, "stdout", chunkIndex++, null));
                            }
                        }
                        String stderr = stderrFor(command);
                        for (String line : stderr.split("(?<=\\n)")) {
                            if (!line.isEmpty()) {
                                subscriber.onNext(streamChunk(line, "stderr", chunkIndex++, null));
                            }
                        }
                    }
                    subscriber.onNext(streamChunk("", null, chunkIndex, 0));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }

        public CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(
                String command,
                String cwd,
                Map<String, String> environment,
                double grace,
                String shellType) {
            ExecuteCmdBackgroundResult result = new ExecuteCmdBackgroundResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ExecuteCmdBackgroundData.builder()
                    .command(command)
                    .cwd(cwd)
                    .pid((int) (grace * 1000))
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        private static ExecuteCmdStreamResult streamChunk(String text, String type, int index, Integer exitCode) {
            ExecuteCmdStreamResult result = new ExecuteCmdStreamResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ExecuteCmdChunkData.builder()
                    .text(text)
                    .type(type)
                    .chunkIndex(index)
                    .exitCode(exitCode)
                    .build());
            return result;
        }

        private static ExecuteCmdStreamResult streamError(String message, int index) {
            ExecuteCmdStreamResult result = new ExecuteCmdStreamResult();
            result.setCode(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
            result.setMessage(message);
            result.setData(ExecuteCmdChunkData.builder()
                    .chunkIndex(index)
                    .exitCode(-1)
                    .build());
            return result;
        }

        private static boolean isBlank(String command) {
            return command == null || command.trim().isEmpty();
        }

        private static boolean isTimeout(String command, Integer timeoutSeconds) {
            return timeoutSeconds != null
                    && timeoutSeconds <= 1
                    && (command.contains("sleep") || command.contains("ping") || command.contains("time.sleep"));
        }

        private static String cwdOrDefault(String cwd) {
            return cwd == null ? "/tmp" : cwd;
        }

        private static String stdoutFor(String command, String cwd, Map<String, String> environment) {
            return stdoutFor(command, cwd, environment, Map.of(), "auto", null);
        }

        private static String stdoutFor(String command, String cwd, Map<String, String> environment,
                                        Map<String, Object> options, String shellType, Integer timeoutSeconds) {
            if ("echo ok".equals(command)) {
                return shellType + ":" + timeoutSeconds + ":" + environment.get("A") + ":"
                        + Boolean.TRUE.equals(options.get("pty"));
            }
            if ("pwd".equals(command) || "echo %CD%".equals(command)) {
                return cwdOrDefault(cwd) + "\n";
            }
            if (command.startsWith("echo ")) {
                String payload = command.substring("echo ".length());
                if ("$TEST_VAR".equals(payload) || "%TEST_VAR%".equals(payload)) {
                    return environment.getOrDefault("TEST_VAR", "") + "\n";
                }
                if (payload.startsWith("$")) {
                    return environment.getOrDefault(payload.substring(1), "") + "\n";
                }
                return payload + "\n";
            }
            if (command.contains("127.0.0.1")) {
                return "127.0.0.1\n127.0.0.1\n127.0.0.1\n";
            }
            if (command.contains("ls") || command.contains("dir")) {
                return "file1.txt\nfile2.txt\n";
            }
            if (command.contains("chunk1") && command.contains("chunk2")) {
                return "chunk1\nchunk2\n";
            }
            return "local_shell_output_for: " + command;
        }

        private static String stderrFor(String command) {
            return command.contains("error_chunk") ? "error_chunk\n" : "";
        }
    }

    private static final class CapturingSubscriber<T> implements Flow.Subscriber<T> {

        private final java.util.ArrayList<T> items = new java.util.ArrayList<>();
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
                return done.get(10, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
