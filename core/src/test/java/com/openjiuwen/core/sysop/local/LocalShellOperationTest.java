/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.ShellProcessRegistry;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ShellOperation} behavior in
 * {@code openjiuwen/core/sys_operation/local/shell_operation.py}.
 */
class LocalShellOperationTest {

    private static final long PROCESS_WAIT_SECONDS = 5L;

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
    void executeCmdCancellationUsesCallerSessionAcrossAsyncBoundary() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalShellOperation operation = operation();
        Path pidFile = tempDir.resolve("command.pid");
        String sessionId = "caller_session_" + UUID.randomUUID();
        CountDownLatch workersStarted = new CountDownLatch(ForkJoinPool.commonPool().getParallelism());
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        List<CompletableFuture<Void>> workers = occupyCommonPool(workersStarted, releaseWorkers);
        ShellProcessRegistry.ContextToken<String> token = null;
        CompletableFuture<ExecuteCmdResult> execution = null;
        ProcessHandle commandProcess = null;
        try {
            token = ShellProcessRegistry.setShellSessionId(sessionId);
            releaseWorkers.countDown();
            execution = operation.executeCmd(longRunningCommand(pidFile), null, 30, null, null, shellType());
            ShellProcessRegistry.resetShellSessionId(token);
            commandProcess = ProcessHandle.of(awaitPid(pidFile)).orElseThrow();
            assertThat(commandProcess.isAlive()).isTrue();

            int killed = CompletableFuture.supplyAsync(
                    () -> ShellProcessRegistry.killShellProcessesForSession(sessionId)
            ).get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);

            assertThat(killed).isEqualTo(1);
            commandProcess.onExit().get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
            execution.get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(commandProcess.isAlive()).isFalse();
        } finally {
            releaseWorkers.countDown();
            if (token != null) {
                ShellProcessRegistry.resetShellSessionId(token);
            }
            if (commandProcess != null) {
                destroyProcess(commandProcess);
            } else {
                readPid(pidFile).ifPresent(pid -> ProcessHandle.of(pid)
                        .ifPresent(LocalShellOperationTest::destroyProcess));
            }
            if (execution != null) {
                execution.get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
            }
            CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new))
                    .get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
        }
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
    @Disabled
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

    private static List<CompletableFuture<Void>> occupyCommonPool(CountDownLatch started, CountDownLatch release)
            throws InterruptedException {
        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int index = 0; index < ForkJoinPool.commonPool().getParallelism(); index += 1) {
            workers.add(CompletableFuture.runAsync(() -> {
                started.countDown();
                awaitLatch(release);
            }));
        }
        boolean allStarted = started.await(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
        if (!allStarted) {
            release.countDown();
        }
        assertThat(allStarted).isTrue();
        return workers;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for common-pool worker release");
            }
        } catch (InterruptedException exception) {
            throw new IllegalStateException("Interrupted while occupying a common-pool worker", exception);
        }
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

    private static String longRunningCommand(Path pidFile) {
        if (isWindows()) {
            String quotedPath = "'" + pidFile.toString().replace("'", "''") + "'";
            return "Write-Output $PID | Set-Content -LiteralPath " + quotedPath + "; Start-Sleep -Seconds 60";
        }
        String quotedPath = "'" + pidFile.toString().replace("'", "'\"'\"'") + "'";
        return "printf '%s' $$ > " + quotedPath + "; exec sleep 60";
    }

    private static long awaitPid(Path pidFile) {
        awaitCondition(() -> readPid(pidFile).isPresent());
        return readPid(pidFile).orElseThrow();
    }

    private static OptionalLong readPid(Path pidFile) {
        try {
            String value = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
        } catch (IOException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_WAIT_SECONDS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10L));
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void destroyProcess(ProcessHandle process) {
        process.destroyForcibly();
        process.onExit().orTimeout(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS).join();
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
