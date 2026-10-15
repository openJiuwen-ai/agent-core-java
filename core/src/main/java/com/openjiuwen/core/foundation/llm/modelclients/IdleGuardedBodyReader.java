/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a response body to completion. Idle (peer silence) detection lives in the body
 * stream itself (see {@link IdleBoundInputStream}), so each blocking read is bounded by the
 * idle window and a dead connection surfaces as a tiered {@code MODEL_CALL_TIMEOUT} error
 * instead of hanging forever. When the consuming thread is interrupted, the read aborts via
 * {@link InterruptedException} so cancellation never surfaces as a timeout.
 *
 * <p>Body bytes are accumulated raw and decoded once at the end, so multi-byte UTF-8
 * characters split across read boundaries are never corrupted.</p>
 *
 * @since 2026-10-09
 */
final class IdleGuardedBodyReader {
    private static final int READ_BUFFER_SIZE = 8192;

    private IdleGuardedBodyReader() {
    }

    /**
     * Reads the full body with idle-bounded reads.
     *
     * @param inputStream response body stream, may be null (yields empty body)
     * @param idleSeconds peer silence window in seconds (recorded in the error surface)
     * @param isStreaming true for streaming calls (recorded in the error surface)
     * @return the decoded body, empty string when the stream is null
     * @throws IOException on read failures other than the idle timeout
     * @throws InterruptedException when the consuming thread is interrupted while reading
     */
    static String readFully(InputStream inputStream, double idleSeconds, boolean isStreaming)
            throws IOException, InterruptedException {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        try (inputStream) {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("model response body read interrupted");
                }
                int read;
                try {
                    read = inputStream.read(buffer);
                } catch (IdleBoundInputStream.IdleTimeoutIOException idleTimeout) {
                    throw ModelTimeoutErrorFactory.idleTimeoutError(
                            idlePhase(body.size()), idleSeconds, isStreaming, idleTimeout);
                }
                if (read < 0) {
                    break;
                }
                body.write(buffer, 0, read);
            }
        }
        return body.toString(StandardCharsets.UTF_8);
    }

    private static TimeoutPhase idlePhase(int accumulatedBytes) {
        return accumulatedBytes == 0 ? TimeoutPhase.BEFORE_FIRST_SEGMENT : TimeoutPhase.AFTER_FIRST_SEGMENT;
    }
}
