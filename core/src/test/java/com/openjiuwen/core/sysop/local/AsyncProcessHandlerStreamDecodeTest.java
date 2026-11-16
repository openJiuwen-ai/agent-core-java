/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Regression for issue198: multi-byte UTF-8 characters spanning chunk boundaries
 * must not become U+FFFD on the streaming path.
 *
 * @since 0.1.18
 */
class AsyncProcessHandlerStreamDecodeTest {
    @Test
    void streamDecodePreservesEmojiAcrossChunkBoundary() throws Exception {
        byte[] payload = ("a".repeat(1022) + "✅ 完成").getBytes(StandardCharsets.UTF_8);

        String invokeOut = drainInvoke(payload, 1024);
        String streamOut = drainStream(payload, 1024);

        assertEquals(0, streamOut.chars().filter(c -> c == 0xFFFD).count());
        assertEquals(invokeOut, streamOut);
        assertEquals(true, streamOut.endsWith("✅ 完成"));
    }

    @Test
    void streamDecodePreservesSplitEmojiWhenChunkSizeIsOne() throws Exception {
        byte[] utf8 = "✅".getBytes(StandardCharsets.UTF_8);
        assertEquals(3, utf8.length);
        String streamOut = drainStream(utf8, 1);
        assertEquals("✅", streamOut);
        assertEquals(0, streamOut.chars().filter(c -> c == 0xFFFD).count());
    }

    private static String drainInvoke(byte[] payload, int chunkSize) throws Exception {
        Process process = new ChunkedStdoutProcess(payload, chunkSize);
        InvokeData data = OperationUtils.createHandler(process, chunkSize, "utf-8", 5).invoke().get();
        return data.getStdout();
    }

    private static String drainStream(byte[] payload, int chunkSize) throws Exception {
        Process process = new ChunkedStdoutProcess(payload, chunkSize);
        BlockingQueue<StreamEvent> queue =
                OperationUtils.createHandler(process, chunkSize, "utf-8", 5).stream();
        StringBuilder streamed = new StringBuilder();
        while (true) {
            StreamEvent event = queue.poll(5, TimeUnit.SECONDS);
            if (event == null || event.getType() == StreamEventType.EXIT
                    || event.getType() == StreamEventType.ERROR) {
                break;
            }
            if (event.getType() == StreamEventType.STDOUT) {
                streamed.append(event.getData());
            }
        }
        return streamed.toString();
    }

    /**
     * Minimal Process stub whose stdout yields at most {@code chunkSize} bytes per read.
     */
    private static final class ChunkedStdoutProcess extends Process {
        private final InputStream stdout;
        private volatile int exitCode = -1;

        private ChunkedStdoutProcess(byte[] payload, int chunkSize) {
            this.stdout = new ChunkedInputStream(new ByteArrayInputStream(payload), chunkSize);
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            exitCode = 0;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            exitCode = 0;
            return true;
        }

        @Override
        public int exitValue() {
            if (exitCode < 0) {
                throw new IllegalThreadStateException("not exited");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            exitCode = 0;
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return exitCode < 0;
        }
    }

    private static final class ChunkedInputStream extends FilterInputStream {
        private final int chunkSize;

        private ChunkedInputStream(InputStream in, int chunkSize) {
            super(in);
            this.chunkSize = Math.max(1, chunkSize);
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            return super.read(buffer, off, Math.min(len, chunkSize));
        }
    }
}
