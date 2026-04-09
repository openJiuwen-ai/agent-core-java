/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handler for monitoring Java subprocess output and state.
 * <p>
 * Mirrors Python's {@code AsyncProcessHandler} in {@code local/utils.py}.
 * Uses Java's {@link Process} and blocking threads instead of Python's asyncio.
 *
 * <p>Usage:
 * <pre>
 *   Process process = new ProcessBuilder("ls", "-la").start();
 *   ProcessHandler handler = new ProcessHandler(process, 1024, StandardCharsets.UTF_8, 300);
 *
 *   // One-shot invocation
 *   InvokeData result = handler.invoke();
 *
 *   // OR streaming (mutually exclusive with invoke)
 *   Iterator&lt;StreamEvent&gt; events = handler.stream();
 * </pre>
 */
public class ProcessHandler {

    private final Process process;
    private final int chunkSize;
    private final Charset encoding;
    private final int overallTimeoutSeconds;
    private final BlockingQueue<StreamEvent> queue;
    private final AtomicBoolean isExecuted;

    public ProcessHandler(Process process, int chunkSize, Charset encoding, int overallTimeoutSeconds) {
        this.process = process;
        this.chunkSize = chunkSize;
        this.encoding = encoding;
        this.overallTimeoutSeconds = overallTimeoutSeconds;
        this.queue = new LinkedBlockingQueue<>();
        this.isExecuted = new AtomicBoolean(false);
    }

    public ProcessHandler(Process process) {
        this(process, 1024, StandardCharsets.UTF_8, 300);
    }

    /**
     * One-time execution to get structured subprocess result.
     *
     * @return InvokeData containing stdout, stderr, exit code
     * @throws IllegalStateException if invoke() or stream() has already been called
     */
    public InvokeData invoke() {
        if (!isExecuted.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "ProcessHandler: invoke() and stream() are mutually exclusive, only one can be executed once");
        }

        // Read stdout and stderr in separate threads to avoid pipe buffer deadlock.
        // If we call waitFor() first without draining, large output fills the OS pipe
        // buffer (~64 KB) and the child blocks on write → waitFor never returns.
        StringBuilder stdoutBuf = new StringBuilder();
        StringBuilder stderrBuf = new StringBuilder();

        Thread stdoutThread = Thread.ofVirtual().name("invoke-stdout-reader").start(
                () -> {
                    try {
                        stdoutBuf.append(readStream(process.getInputStream()));
                    } catch (Exception e) {
                        Loggers.SYS_OPERATION.error("Failed to read stdout", e);
                    }
                });
        Thread stderrThread = Thread.ofVirtual().name("invoke-stderr-reader").start(
                () -> {
                    try {
                        stderrBuf.append(readStream(process.getErrorStream()));
                    } catch (Exception e) {
                        Loggers.SYS_OPERATION.error("Failed to read stderr", e);
                    }
                });

        try {
            boolean finished = process.waitFor(overallTimeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                Loggers.SYS_OPERATION.error("Get process result time out",
                        LogEventType.SYS_OP_ERROR.getValue());
                process.destroyForcibly();
                try {
                    boolean killedOk = process.waitFor(30, TimeUnit.SECONDS);
                    // Wait for reader threads to drain remaining data
                    stdoutThread.join(5000);
                    stderrThread.join(5000);
                    if (!killedOk) {
                        return InvokeData.builder()
                                .stdout(stdoutBuf.toString())
                                .stderr("Process did not terminate after kill")
                                .exitCode(-1)
                                .exception(new InterruptedException("Process timeout after " + overallTimeoutSeconds + "s"))
                                .build();
                    }
                    return InvokeData.builder()
                            .stdout(stdoutBuf.toString())
                            .stderr(stderrBuf.toString())
                            .exitCode(process.exitValue())
                            .exception(new InterruptedException("Process timeout after " + overallTimeoutSeconds + "s"))
                            .build();
                } catch (Exception ex) {
                    Loggers.SYS_OPERATION.error("Kill process error",
                            LogEventType.SYS_OP_ERROR.getValue());
                    return InvokeData.builder()
                            .stdout(stdoutBuf.toString())
                            .stderr("kill process failed, error: " + ex.getMessage())
                            .exitCode(-1)
                            .exception(ex)
                            .build();
                }
            }

            // Wait for reader threads to finish
            stdoutThread.join(5000);
            stderrThread.join(5000);

            return InvokeData.builder()
                    .stdout(stdoutBuf.toString())
                    .stderr(stderrBuf.toString())
                    .exitCode(process.exitValue())
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return InvokeData.builder()
                    .stdout(stdoutBuf.toString())
                    .stderr("Interrupted: " + e.getMessage())
                    .exitCode(-1)
                    .exception(e)
                    .build();
        }
    }

    /**
     * Create an iterator for streaming process output events.
     *
     * @return Iterator of StreamEvent objects (STDOUT/STDERR/ERROR/EXIT)
     * @throws IllegalStateException if invoke() or stream() has already been called
     */
    public Iterator<StreamEvent> stream() {
        if (!isExecuted.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "ProcessHandler: invoke() and stream() are mutually exclusive, only one can be executed once");
        }

        // Start reader threads for stdout and stderr
        Thread stdoutReader = Thread.ofVirtual().name("stdout-reader").start(
                () -> readerTask(process.getInputStream(), StreamEventType.STDOUT));
        Thread stderrReader = Thread.ofVirtual().name("stderr-reader").start(
                () -> readerTask(process.getErrorStream(), StreamEventType.STDERR));

        return new StreamEventIterator(stdoutReader, stderrReader);
    }

    /**
     * Background stream reader thread for stdout/stderr.
     */
    private void readerTask(InputStream inputStream, StreamEventType streamType) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encoding))) {
            char[] buffer = new char[chunkSize];
            int bytesRead;
            int totalChunks = 0;

            while ((bytesRead = reader.read(buffer)) != -1) {
                String data = new String(buffer, 0, bytesRead);
                StreamEvent event = StreamEvent.builder()
                        .type(streamType)
                        .data(data)
                        .build();
                queue.put(event);
                totalChunks++;
                Loggers.SYS_OPERATION.debug("Success to put stream queue item, total_num={}, queue_size={}",
                        totalChunks, queue.size());
            }

            Loggers.SYS_OPERATION.info("Receive stream eof, total_num={}, queue_size={}",
                    totalChunks, queue.size());
        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Stream read error: stream_type={}, chunk_size={}, encoding={}",
                    streamType.getValue(), chunkSize, encoding.name(), e);
            try {
                queue.put(StreamEvent.builder()
                        .type(StreamEventType.ERROR)
                        .data(streamType.getValue() + " reader error: " + e.getMessage())
                        .build());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Read all content from an InputStream.
     */
    private String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encoding))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[chunkSize];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, bytesRead);
            }
            return sb.toString();
        } catch (IOException e) {
            Loggers.SYS_OPERATION.error("Failed to read process stream", e);
            return "";
        }
    }

    /**
     * Iterator implementation that consumes events from the blocking queue.
     */
    private class StreamEventIterator implements Iterator<StreamEvent> {

        private final Thread stdoutReader;
        private final Thread stderrReader;
        private StreamEvent next;
        private boolean exitEmitted = false;
        private final long startTimeMs;

        StreamEventIterator(Thread stdoutReader, Thread stderrReader) {
            this.stdoutReader = stdoutReader;
            this.stderrReader = stderrReader;
            this.startTimeMs = System.currentTimeMillis();
        }

        @Override
        public boolean hasNext() {
            if (exitEmitted) {
                return false;
            }
            if (next != null) {
                return true;
            }
            next = fetchNext();
            return next != null;
        }

        @Override
        public StreamEvent next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            StreamEvent result = next;
            next = null;
            if (result.getType() == StreamEventType.EXIT) {
                exitEmitted = true;
            }
            return result;
        }

        private StreamEvent fetchNext() {
            while (true) {
                // Check overall timeout
                if (overallTimeoutSeconds > 0) {
                    long elapsedMs = System.currentTimeMillis() - startTimeMs;
                    if (elapsedMs >= (long) overallTimeoutSeconds * 1000) {
                        Loggers.SYS_OPERATION.error("Stream execution time out, timeout={}s",
                                overallTimeoutSeconds);
                        process.destroyForcibly();
                        return StreamEvent.builder()
                                .type(StreamEventType.ERROR)
                                .data("execution timeout after " + overallTimeoutSeconds + " seconds")
                                .build();
                    }
                }

                try {
                    StreamEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        return event;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return StreamEvent.builder()
                            .type(StreamEventType.ERROR)
                            .data("stream interrupted: " + e.getMessage())
                            .build();
                }

                // Check if all readers are done and queue is empty
                boolean readersAlive = stdoutReader.isAlive() || stderrReader.isAlive();
                if (!readersAlive && queue.isEmpty()) {
                    // Emit EXIT event
                    try {
                        process.waitFor(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    int exitCode = process.isAlive() ? -1 : process.exitValue();
                    return StreamEvent.builder()
                            .type(StreamEventType.EXIT)
                            .data(exitCode)
                            .build();
                }
            }
        }
    }
}
