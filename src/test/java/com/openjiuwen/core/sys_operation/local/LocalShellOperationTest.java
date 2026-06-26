/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseShellOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ShellOperation} behavior in
 * {@code openjiuwen/core/sys_operation/local/shell_operation.py}.
 */
class LocalShellOperationTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Test
    void executeCmdCapturesStdoutStderrAndExitCode() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation();

        ExecuteCmdResult result = operation.executeCmd(
                echoWarnExitCommand(7),
                null,
                5,
                null,
                null,
                shellType()).get(10, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getMessage()).isEqualTo("Command executed successfully");
        assertThat(result.getData().getStdout()).contains("hello");
        assertThat(result.getData().getStderr()).contains("warn");
        assertThat(result.getData().getExitCode()).isEqualTo(7);
        assertThat(Path.of(result.getData().getCwd())).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    @Test
    void executeCmdStreamEmitsOutputAndExitChunks() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation();

        List<ExecuteCmdStreamResult> chunks = collect(operation.executeCmdStream(
                echoWarnExitCommand(3),
                null,
                5,
                null,
                null,
                shellType()));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.getData().getType()).isEqualTo("stdout");
            assertThat(chunk.getData().getText()).contains("hello");
        });
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.getData().getType()).isEqualTo("stderr");
            assertThat(chunk.getData().getText()).contains("warn");
        });
        assertThat(chunks.get(chunks.size() - 1).getData().getExitCode()).isEqualTo(3);
    }

    @Test
    void executeCmdBackgroundReturnsProcessIdWhenStillRunning() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation();

        ExecuteCmdBackgroundResult result = operation.executeCmdBackground(
                sleepCommand(),
                null,
                null,
                0.1d,
                shellType()).get(5, TimeUnit.SECONDS);

        ProcessHandle backgroundProcess = result.getData() == null || result.getData().getPid() == null
                ? null
                : ProcessHandle.of(result.getData().getPid()).orElse(null);
        try {
            assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
            assertThat(result.getData().getPid()).isPositive();
            assertThat(Path.of(result.getData().getCwd())).isEqualTo(tempDir.toAbsolutePath().normalize());
        } finally {
            if (backgroundProcess != null && backgroundProcess.isAlive()) {
                backgroundProcess.destroyForcibly();
                backgroundProcess.onExit().get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void dangerousCommandIsRejectedBeforeExecution() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation();

        ExecuteCmdResult result = operation.executeCmd(
                "rm -rf " + tempDir.resolve("missing"),
                null,
                5,
                null,
                null,
                shellType()).get(5, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("command rejected for safety");
        assertThat(result.getData().getExitCode()).isEqualTo(-1);
    }

    private LocalShellOperation operation() {
        LocalWorkConfig config = LocalWorkConfig.builder()
                .shellAllowlist(List.of(
                        "printf",
                        "sleep",
                        "rm",
                        "Write-Output",
                        "Start-Sleep",
                        "[Console]::Error.WriteLine('warn');",
                        "cmd",
                        "powershell"))
                .build();
        return new LocalShellOperation("shell", OperationMode.LOCAL, "local shell", config);
    }

    private static BaseShellOperation.ShellType shellType() {
        return isWindows() ? BaseShellOperation.ShellType.POWERSHELL : BaseShellOperation.ShellType.SH;
    }

    private static String echoWarnExitCommand(int exitCode) {
        if (isWindows()) {
            return "Write-Output 'hello'; [Console]::Error.WriteLine('warn'); exit " + exitCode;
        }
        return "printf 'hello\\n'; printf 'warn\\n' 1>&2; exit " + exitCode;
    }

    private static String sleepCommand() {
        return isWindows() ? "Start-Sleep -Seconds 2" : "sleep 2";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
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
                return done.get(10, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
