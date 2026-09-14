/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors Python's {@code AsyncProcessHandler} in
 * {@code openjiuwen/core/sys_operation/local/utils.py}.
 */
public final class AsyncProcessHandler {

    private static final LoggerProtocol LOGGER = Loggers.SYS_OPERATION;

    private final Process process;
    private final int chunkSize;
    private final Charset charset;
    private final int overallTimeoutSeconds;
    private final BlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean executed = new AtomicBoolean(false);
    private final AtomicBoolean suppressReaderErrors = new AtomicBoolean(false);

    public AsyncProcessHandler(Process process, int chunkSize, String encoding, int timeout) {
        this.process = Objects.requireNonNull(process, "process");
        this.chunkSize = chunkSize;
        this.charset = encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        this.overallTimeoutSeconds = timeout;
    }

    public AsyncProcessHandler(Process process) {
        this(process, 1024, "utf-8", 300);
    }

    public CompletableFuture<InvokeData> invoke() {
        ensureSingleExecution();
        return CompletableFuture.supplyAsync(() -> {
            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            List<CompletableFuture<Void>> readers = List.of(
                    drainStream(process.getInputStream(), stdoutBuffer),
                    drainStream(process.getErrorStream(), stderrBuffer)
            );

            try {
                boolean finished = overallTimeoutSeconds <= 0
                        ? waitWithoutTimeout()
                        : process.waitFor(overallTimeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    TimeoutException timeout = new TimeoutException(
                            "Process timed out after " + overallTimeoutSeconds + " seconds"
                    );
                    LOGGER.error("Get process result time out: {}", timeout.getMessage());
                    killProcessTree();
                    awaitReaders(readers, 30);
                    return new InvokeData(
                            decode(stdoutBuffer),
                            decode(stderrBuffer),
                            exitCodeOrDefault(-1),
                            timeout
                    );
                }

                awaitReaders(readers, 30);
                return new InvokeData(decode(stdoutBuffer), decode(stderrBuffer), process.exitValue());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Process cancelled by user, killing subprocess tree");
                killProcessTree();
                awaitReaders(readers, 5);
                throw new CancellationException("Process execution interrupted");
            }
        });
    }

    public BlockingQueue<StreamEvent> stream() {
        ensureSingleExecution();

        List<CompletableFuture<Void>> readers = List.of(
                readToQueue(process.getInputStream(), StreamEventType.STDOUT),
                readToQueue(process.getErrorStream(), StreamEventType.STDERR)
        );

        CompletableFuture.runAsync(() -> {
            long startedAt = System.nanoTime();
            String terminalError = null;
            try {
                while (hasRunningReader(readers)) {
                    if (overallTimeoutSeconds > 0) {
                        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt);
                        if (elapsedSeconds >= overallTimeoutSeconds) {
                            LOGGER.error("Stream execution time out after {} seconds", overallTimeoutSeconds);
                            suppressReaderErrors.set(true);
                            killProcessTree();
                            terminalError = "execution timeout after " + overallTimeoutSeconds + " seconds";
                            break;
                        }
                    }
                    Thread.sleep(50L);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Stream cancelled by user, killing subprocess tree");
                suppressReaderErrors.set(true);
                killProcessTree();
                terminalError = "Execution cancelled by user";
            } catch (Exception exception) {
                LOGGER.error("Stream execution error: {}", exception.getMessage());
                terminalError = "stream loop error: " + exception.getMessage();
            }

            cancelUnfinishedReaders(readers);
            for (CompletableFuture<Void> reader : readers) {
                try {
                    reader.join();
                } catch (Exception exception) {
                    if (suppressReaderErrors.get()) {
                        continue;
                    }
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    offerEvent(new StreamEvent(StreamEventType.ERROR, "reader task error: " + cause.getMessage()));
                }
            }

            try {
                process.waitFor();
                if (terminalError != null) {
                    offerEvent(new StreamEvent(StreamEventType.ERROR, terminalError));
                    return;
                }
                offerEvent(new StreamEvent(StreamEventType.EXIT, process.exitValue()));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                offerEvent(new StreamEvent(StreamEventType.ERROR, "process wait error: interrupted"));
            }
        });

        return queue;
    }

    public CompletableFuture<BackgroundLaunchResult> background() {
        return background(3.0d);
    }

    public CompletableFuture<BackgroundLaunchResult> background(double graceSeconds) {
        ensureSingleExecution();
        return CompletableFuture.supplyAsync(() -> {
            long pid = process.pid();
            try {
                boolean finished = process.waitFor((long) (graceSeconds * 1000.0d), TimeUnit.MILLISECONDS);
                if (!finished) {
                    return new BackgroundLaunchResult(pid, null);
                }
                int code = process.exitValue();
                return code != 0
                        ? new BackgroundLaunchResult(pid, "process exited early with code " + code)
                        : new BackgroundLaunchResult(pid, null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new BackgroundLaunchResult(pid, "background launch interrupted");
            }
        });
    }

    private CompletableFuture<Void> drainStream(InputStream stream, ByteArrayOutputStream buffer) {
        return CompletableFuture.runAsync(() -> readBytes(stream, chunk -> {
            try {
                buffer.write(chunk);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }));
    }

    private CompletableFuture<Void> readToQueue(InputStream stream, StreamEventType streamType) {
        return CompletableFuture.runAsync(() -> readBytes(stream, chunk -> {
            String data = new String(chunk, charset);
            offerEvent(new StreamEvent(streamType, data));
        }));
    }

    private void readBytes(InputStream stream, java.util.function.Consumer<byte[]> consumer) {
        try {
            byte[] buffer = new byte[Math.max(chunkSize, 1)];
            while (true) {
                int read = stream.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                consumer.accept(chunk);
            }
        } catch (Exception exception) {
            LOGGER.error("Stream read error: {}", exception.getMessage());
            if (!suppressReaderErrors.get()) {
                offerEvent(new StreamEvent(
                        StreamEventType.ERROR,
                        streamTypeName(stream, exception) + " reader error: " + exception.getMessage()
                ));
            }
        }
    }

    private String streamTypeName(InputStream stream, Exception exception) {
        if (exception instanceof RuntimeException runtime && runtime.getMessage() != null) {
            return runtime.getMessage();
        }
        if (stream == process.getInputStream()) {
            return StreamEventType.STDOUT.getValue();
        }
        if (stream == process.getErrorStream()) {
            return StreamEventType.STDERR.getValue();
        }
        return "stream";
    }

    private boolean waitWithoutTimeout() throws InterruptedException {
        process.waitFor();
        return true;
    }

    private void awaitReaders(List<CompletableFuture<Void>> readers, int graceSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(graceSeconds);
        for (CompletableFuture<Void> reader : readers) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                reader.cancel(true);
                continue;
            }
            try {
                reader.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (Exception exception) {
                reader.cancel(true);
            }
        }
    }

    private int exitCodeOrDefault(int defaultValue) {
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                return defaultValue;
            }
            return process.exitValue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return defaultValue;
        } catch (IllegalThreadStateException exception) {
            return defaultValue;
        }
    }

    private boolean hasRunningReader(List<CompletableFuture<Void>> readers) {
        for (CompletableFuture<Void> reader : readers) {
            if (!reader.isDone()) {
                return true;
            }
        }
        return false;
    }

    private void cancelUnfinishedReaders(List<CompletableFuture<Void>> readers) {
        for (CompletableFuture<Void> reader : readers) {
            if (!reader.isDone()) {
                reader.cancel(true);
            }
        }
    }

    private void killProcessTree() {
        ProcessHandle handle = process.toHandle();
        if (isWindows()) {
            try {
                new ProcessBuilder("taskkill", "/PID", String.valueOf(handle.pid()), "/T", "/F")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                LOGGER.warning("Failed to taskkill process tree: {}", exception.getMessage());
            }
        } else {
            killUnixChildren(handle.pid(), "TERM");
            killUnixChildren(handle.pid(), "KILL");
        }
        try {
            List<ProcessHandle> descendants = new ArrayList<>(handle.descendants().toList());
            for (int i = descendants.size() - 1; i >= 0; i--) {
                ProcessHandle descendant = descendants.get(i);
                descendant.destroyForcibly();
            }
        } catch (RuntimeException exception) {
            LOGGER.warning("Failed to enumerate subprocess descendants: {}", exception.getMessage());
        }
        handle.destroyForcibly();
    }

    private void killUnixChildren(long pid, String signal) {
        try {
            new ProcessBuilder("pkill", "-" + signal, "-P", String.valueOf(pid))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            LOGGER.warning("Failed to pkill child processes for {}: {}", pid, exception.getMessage());
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void ensureSingleExecution() {
        if (!executed.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "AsyncProcessHandler: invoke() and stream() are mutually exclusive, only one can be executed once"
            );
        }
    }

    private void offerEvent(StreamEvent event) {
        try {
            queue.put(event);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String decode(ByteArrayOutputStream buffer) {
        return buffer.toString(charset);
    }

    /**
     * Mirrors Python's tuple return from {@code background()} in
     * {@code openjiuwen/core/sys_operation/local/utils.py}.
     */
    public record BackgroundLaunchResult(long pid, String error) {
    }
}
